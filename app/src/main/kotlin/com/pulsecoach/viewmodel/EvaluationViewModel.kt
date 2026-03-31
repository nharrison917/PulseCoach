package com.pulsecoach.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pulsecoach.data.PulseCoachDatabase
import com.pulsecoach.model.BiologicalSex
import com.pulsecoach.model.HrSample
import com.pulsecoach.model.Session
import com.pulsecoach.model.SessionType
import com.pulsecoach.model.UserProfile
import com.pulsecoach.repository.SessionRepository
import com.pulsecoach.repository.UserProfileRepository
import com.pulsecoach.util.HistoricalAverager
import com.pulsecoach.util.PolynomialProjector
import com.pulsecoach.util.SyntheticSessionGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.random.Random

// --- Result data classes ---

/** Accuracy metrics for one observation window (e.g. "after 10 min of data"). */
data class WindowMetrics(
    val observationMin: Int,
    val mae: Float,   // mean absolute error in calories
    val mape: Float   // mean absolute percentage error, 0-100
)

/** Actual vs projected calorie curves for a single test session (used in overlay chart). */
data class TestCurveData(
    val actual: List<Pair<Float, Float>>,     // (elapsedMinutes, cumulativeCalories)
    val projected: List<Pair<Float, Float>>?  // null if projection could not be computed
)

/**
 * Poly-only vs typed-blend accuracy for one session intensity type.
 * [polyMetrics] is computed over only the test sessions assigned to [type],
 * so the comparison with [blendedMetrics] is apples-to-apples.
 */
data class TypedMetrics(
    val type: SessionType,
    val polyMetrics: List<WindowMetrics>,
    val blendedMetrics: List<WindowMetrics>
)

/** Everything the EvaluationScreen needs. */
data class EvaluationResult(
    /** Polynomial-only MAE/MAPE at each observation window (5, 10, 15, 20 min). */
    val windowMetrics: List<WindowMetrics>,
    /** Blended MAE/MAPE — only populated when >= 10 qualifying sessions are in the database. */
    val blendedWindowMetrics: List<WindowMetrics>?,
    /** First 5 test sessions for the overlay chart. */
    val testCurves: List<TestCurveData>,
    /**
     * Per-type poly vs typed-blend accuracy. Only contains types that have >= 10 labeled
     * sessions in the database. Null if no type meets the threshold.
     */
    val typedBlendedMetrics: List<TypedMetrics>?
)

// --- ViewModel ---

/**
 * Runs an offline accuracy evaluation of PolynomialProjector against 10
 * held-out synthetic sessions. Never writes to the database — results are
 * purely in-memory.
 */
class EvaluationViewModel(application: Application) : AndroidViewModel(application) {

    private val db = PulseCoachDatabase.getInstance(application)
    private val sessionRepository = SessionRepository(db.sessionDao(), db.hrSampleDao())

    private val _result = MutableStateFlow<EvaluationResult?>(null)
    /** Null until runEvaluation() completes. */
    val result: StateFlow<EvaluationResult?> = _result

    private val _isRunning = MutableStateFlow(false)
    /** True while the background computation is in progress. */
    val isRunning: StateFlow<Boolean> = _isRunning

    // 10 held-out test sessions: (targetHr, durationMin)
    // Different params from the seeder's 12 sessions; fixed seeds for reproducibility.
    private val testParams = listOf(
        125 to 33, 125 to 38, 125 to 43,
        147 to 28, 147 to 37, 147 to 48,
        165 to 27, 165 to 31,
        125 to 52, 147 to 42
    )

    // SessionType assigned to each test session — mirrors testParams ordering.
    // 125 bpm = easy pace (RECOVERY), 147 bpm = tempo (STEADY), 165 bpm = threshold (PUSH).
    private val testParamTypes = listOf(
        SessionType.RECOVERY, SessionType.RECOVERY, SessionType.RECOVERY,
        SessionType.STEADY,   SessionType.STEADY,   SessionType.STEADY,
        SessionType.PUSH,     SessionType.PUSH,
        SessionType.RECOVERY, SessionType.STEADY
    )

    private val observationWindows = listOf(10, 15, 20)

    /**
     * Loads qualifying sessions from Room, generates 10 in-memory test sessions,
     * and computes MAE/MAPE for both polynomial-only and blended projections.
     */
    fun runEvaluation() {
        if (_isRunning.value) return
        viewModelScope.launch {
            _isRunning.value = true
            // Load all qualifying sessions + per-type subsets in one IO hop.
            val (qualifyingSessions, typedSessions) = withContext(Dispatchers.IO) {
                val all = sessionRepository.getQualifyingSessions().first()
                val typed = SessionType.entries.associateWith { type ->
                    sessionRepository.getQualifyingSessionsByType(type).first()
                }
                all to typed
            }
            _result.value = withContext(Dispatchers.Default) {
                compute(qualifyingSessions, typedSessions)
            }
            _isRunning.value = false
        }
    }

    // ---------------------------------------------------------------------------
    // Internal computation
    // ---------------------------------------------------------------------------

    private suspend fun compute(
        qualifyingSessions: List<Session>,
        typedSessions: Map<SessionType, List<Session>>
    ): EvaluationResult {
        val profile = loadProfile()

        // Generate 10 held-out sessions in memory with fixed seeds
        val testSessions = testParams.mapIndexed { index, (targetHr, durationMin) ->
            SyntheticSessionGenerator.generate(
                targetHr = targetHr,
                durationMin = durationMin,
                profile = profile,
                startTimeMs = 1_700_000_000_000L + index * 3_600_000L,
                random = Random(seed = index.toLong() + 100L)
            )
        }

        val actualCurves = testSessions.map { buildMinuteCurve(it.samples) }

        // Panel 2 — polynomial-only metrics
        val polyMetrics = observationWindows.map { window ->
            computePolyWindowMetrics(window, testSessions, actualCurves)
        }

        // Panel 3 — blended metrics (requires >= 10 qualifying sessions in Room)
        val blendedMetrics = if (qualifyingSessions.size >= HistoricalAverager.BLEND_MIN_SESSIONS) {
            computeBlendedMetrics(qualifyingSessions, testSessions, actualCurves)
        } else null

        // Panel 4 — per-type typed-blend metrics
        val typedMetrics = computeTypedBlendedMetrics(typedSessions, testSessions, actualCurves)

        // Panel 1 — first 5 test sessions for the overlay chart (projected at 10-min mark)
        val testCurves = actualCurves.take(5).mapIndexed { i, curve ->
            val durationMin = testSessions[i].durationMs / 60_000f
            val observedPoints = curve.filter { (min, _) -> min <= 10f }
            val projected = if (observedPoints.size >= 3) {
                PolynomialProjector.project(observedPoints, durationMin)
            } else null
            TestCurveData(actual = curve, projected = projected)
        }

        return EvaluationResult(
            windowMetrics = polyMetrics,
            blendedWindowMetrics = blendedMetrics,
            testCurves = testCurves,
            typedBlendedMetrics = typedMetrics
        )
    }

    /** Polynomial-only projection error at a single observation window. */
    private fun computePolyWindowMetrics(
        window: Int,
        sessions: List<SyntheticSessionGenerator.Result>,
        actualCurves: List<List<Pair<Float, Float>>>
    ): WindowMetrics {
        val errorPairs = sessions.zip(actualCurves).mapNotNull { (session, curve) ->
            val durationMin = session.durationMs / 60_000f
            val observedPoints = curve.filter { (min, _) -> min <= window.toFloat() }
            if (observedPoints.size < 3) return@mapNotNull null
            val projected = PolynomialProjector.project(observedPoints, durationMin)
            val projectedFinal = projected?.lastOrNull()?.second ?: return@mapNotNull null
            Pair(projectedFinal, session.totalCalories)
        }
        return buildMetrics(window, errorPairs)
    }

    /**
     * Blended projection error at each observation window.
     * Loads HrSamples for qualifying sessions from Room to build the historical curve.
     */
    private suspend fun computeBlendedMetrics(
        qualifyingSessions: List<Session>,
        testSessions: List<SyntheticSessionGenerator.Result>,
        actualCurves: List<List<Pair<Float, Float>>>
    ): List<WindowMetrics> {
        val sampleIds = qualifyingSessions.map { it.id }
        val historicalSamples = withContext(Dispatchers.IO) {
            sessionRepository.getSamplesForSessions(sampleIds)
        }

        return observationWindows.map { window ->
            val errorPairs = testSessions.zip(actualCurves).mapNotNull { (session, curve) ->
                val durationMin = session.durationMs / 60_000f
                val observedPoints = curve.filter { (min, _) -> min <= window.toFloat() }
                if (observedPoints.size < 3) return@mapNotNull null

                val polyProjection = PolynomialProjector.project(observedPoints, durationMin)
                    ?: return@mapNotNull null

                // Build the historical curve for this specific target duration
                val targetMin = (durationMin + 0.5f).toInt()
                val historical = HistoricalAverager.buildCurve(
                    qualifyingSessions, historicalSamples, targetMin
                )

                val blended = HistoricalAverager.blend(polyProjection, historical, observedPoints.last().second)
                val projectedFinal = blended.lastOrNull()?.second ?: return@mapNotNull null
                Pair(projectedFinal, session.totalCalories)
            }
            buildMetrics(window, errorPairs)
        }
    }

    /**
     * Per-type accuracy: for each SessionType with >= 10 labeled sessions in Room,
     * computes poly-only and typed-blend metrics over only the test sessions of that type.
     * Returns null if no type meets the threshold.
     */
    private suspend fun computeTypedBlendedMetrics(
        typedSessions: Map<SessionType, List<Session>>,
        testSessions: List<SyntheticSessionGenerator.Result>,
        actualCurves: List<List<Pair<Float, Float>>>
    ): List<TypedMetrics>? {
        val result = mutableListOf<TypedMetrics>()

        for (type in SessionType.entries) {
            val sessionsForType = typedSessions[type] ?: emptyList()
            if (sessionsForType.size < HistoricalAverager.BLEND_MIN_SESSIONS) continue

            // Subset of test sessions assigned this type
            val indices = testParamTypes.indices.filter { testParamTypes[it] == type }
            val typeTestSessions = indices.map { testSessions[it] }
            val typeActualCurves = indices.map { actualCurves[it] }

            // Poly-only metrics for this type's test sessions (fair baseline)
            val polyMetrics = observationWindows.map { window ->
                computePolyWindowMetrics(window, typeTestSessions, typeActualCurves)
            }

            // Load samples for the typed historical sessions
            val sampleIds = sessionsForType.map { it.id }
            val samples = withContext(Dispatchers.IO) {
                sessionRepository.getSamplesForSessions(sampleIds)
            }

            // Typed-blend metrics using getFilteredCurve()
            val blendedMetrics = observationWindows.map { window ->
                val errorPairs = typeTestSessions.zip(typeActualCurves).mapNotNull { (session, curve) ->
                    val durationMin = session.durationMs / 60_000f
                    val observedPoints = curve.filter { (min, _) -> min <= window.toFloat() }
                    if (observedPoints.size < 3) return@mapNotNull null

                    val polyProjection = PolynomialProjector.project(observedPoints, durationMin)
                        ?: return@mapNotNull null

                    val targetMin = (durationMin + 0.5f).toInt()
                    val durationBucket = HistoricalAverager.durationBucketFor(session.durationMs)
                    val historical = HistoricalAverager.getFilteredCurve(
                        sessionsForType, samples, targetMin, durationBucket
                    ) ?: return@mapNotNull null

                    val blended = HistoricalAverager.blend(polyProjection, historical, observedPoints.last().second)
                    val projectedFinal = blended.lastOrNull()?.second ?: return@mapNotNull null
                    Pair(projectedFinal, session.totalCalories)
                }
                buildMetrics(window, errorPairs)
            }

            result.add(TypedMetrics(type, polyMetrics, blendedMetrics))
        }

        return if (result.isEmpty()) null else result
    }

    /** Computes MAE and MAPE from a list of (projected, actual) calorie pairs. */
    private fun buildMetrics(window: Int, errorPairs: List<Pair<Float, Float>>): WindowMetrics {
        if (errorPairs.isEmpty()) return WindowMetrics(window, mae = 0f, mape = 0f)
        val mae = errorPairs.map { (proj, actual) -> abs(proj - actual) }.average().toFloat()
        val mape = errorPairs
            .map { (proj, actual) -> abs(proj - actual) / actual.coerceAtLeast(1f) }
            .average().toFloat() * 100f
        return WindowMetrics(observationMin = window, mae = mae, mape = mape)
    }

    /**
     * Resamples HrSamples (one per second) to one point per elapsed minute.
     * Extracted to companion so JVM unit tests can call it without an Application context.
     */
    private fun buildMinuteCurve(samples: List<HrSample>): List<Pair<Float, Float>> =
        Companion.buildMinuteCurve(samples)

    companion object {
        /** Public for testing: resamples HrSamples to per-minute cumulative calorie points. */
        internal fun buildMinuteCurve(samples: List<HrSample>): List<Pair<Float, Float>> {
            if (samples.isEmpty()) return emptyList()
            val startMs = samples.first().timestampMs
            return samples
                .groupBy { ((it.timestampMs - startMs) / 60_000L).toInt() }
                .entries
                .sortedBy { it.key }
                .map { (minute, group) ->
                    val lastSample = group.maxBy { it.timestampMs }
                    Pair(minute.toFloat(), lastSample.cumulativeCalories)
                }
        }
    }

    private fun loadProfile(): UserProfile =
        UserProfileRepository(getApplication()).getProfile()
            ?: UserProfile(age = 30, weightKg = 75f, sex = BiologicalSex.MALE)
}
