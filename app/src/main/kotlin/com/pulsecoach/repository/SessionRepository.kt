package com.pulsecoach.repository

import com.pulsecoach.data.HrSampleDao
import com.pulsecoach.data.HrSampleEntity
import com.pulsecoach.data.SessionDao
import com.pulsecoach.data.SessionEntity
import com.pulsecoach.model.HrSample
import com.pulsecoach.model.Session
import com.pulsecoach.model.UserProfile
import com.pulsecoach.util.SyntheticSessionGenerator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Repository that bridges the Room DAOs and the rest of the app.
 *
 * The rest of the app (ViewModels, UI) only sees domain models (Session, HrSample).
 * Entity classes stay contained inside the data layer.
 * This also means if the schema changes, only this file and the entities need to change.
 */
class SessionRepository(
    private val sessionDao: SessionDao,
    private val hrSampleDao: HrSampleDao
) {

    /** Start a new session. Returns the auto-generated session ID. */
    suspend fun startSession(startTimeMs: Long): Long {
        val entity = SessionEntity(
            startTimeMs = startTimeMs,
            endTimeMs = null,
            targetDurationMs = null,
            totalCalories = 0f,
            avgBpm = 0f,
            notes = ""
        )
        return sessionDao.insert(entity)
    }

    /**
     * Finish a session by writing summary stats.
     * Called when the user taps Stop.
     */
    suspend fun finishSession(
        sessionId: Long,
        endTimeMs: Long,
        totalCalories: Float,
        avgBpm: Float
    ) {
        // Fetch the existing row so we preserve fields we aren't updating (startTimeMs, notes, etc.)
        val existing = sessionDao.getSessionById(sessionId) ?: return
        sessionDao.update(
            existing.copy(
                endTimeMs = endTimeMs,
                totalCalories = totalCalories,
                avgBpm = avgBpm
            )
        )
    }

    /** Insert one HR sample row during an active recording. */
    suspend fun insertSample(sample: HrSample) {
        hrSampleDao.insert(sample.toEntity())
    }

    /** Observe all sessions (newest first) for the history screen. */
    fun getAllSessions(): Flow<List<Session>> =
        sessionDao.getAllSessions().map { list -> list.map { it.toDomain() } }

    /**
     * Observe sessions that qualify for historical averaging (completed, avgBpm > 100,
     * duration > 10 min). Emits on every database change so the ViewModel can react
     * immediately after seeding or finishing a real session.
     */
    fun getQualifyingSessions(): Flow<List<Session>> =
        sessionDao.getQualifyingSessions().map { list -> list.map { it.toDomain() } }

    /**
     * Bulk-fetch all HR samples for the given session IDs.
     * Returns a flat list — HistoricalAverager groups them by sessionId internally.
     */
    suspend fun getSamplesForSessions(ids: List<Long>): List<HrSample> =
        hrSampleDao.getSamplesForSessions(ids).map { it.toDomain() }

    /** Observe all HR samples for a session (live, for charting). */
    fun getSamplesForSession(sessionId: Long): Flow<List<HrSample>> =
        hrSampleDao.getSamplesForSession(sessionId).map { list -> list.map { it.toDomain() } }

    /** Fetch all HR samples for a session once (for CSV export). */
    suspend fun getSamplesForSessionOnce(sessionId: Long): List<HrSample> =
        hrSampleDao.getSamplesForSessionOnce(sessionId).map { it.toDomain() }

    /**
     * Permanently deletes sessions and all their HR samples.
     * Samples are deleted first because they reference sessions by ID.
     */
    suspend fun deleteSessions(ids: Set<Long>) {
        val idList = ids.toList()
        hrSampleDao.deleteBySessionIds(idList)
        sessionDao.deleteByIds(idList)
    }

    /**
     * Seeds 12 parameterized synthetic sessions into Room for testing the projection engine.
     * Sessions are spaced ~26 hours apart ending at the current time, and tagged notes = "synthetic".
     * Call only from debug builds.
     */
    suspend fun seedSyntheticSessions(profile: UserProfile) {
        // Space sessions ~26 hours apart, ending around now, so history looks realistic
        val baseTime = System.currentTimeMillis() -
                SyntheticSessionGenerator.SESSION_CONFIGS.size * 26 * 60 * 60 * 1000L

        SyntheticSessionGenerator.SESSION_CONFIGS.forEachIndexed { index, (targetHr, durationMin) ->
            val startTimeMs = baseTime + index * 26L * 60 * 60 * 1000
            val generated = SyntheticSessionGenerator.generate(targetHr, durationMin, profile, startTimeMs)

            // Insert the session row first so we get the auto-generated ID
            val sessionId = sessionDao.insert(
                SessionEntity(
                    startTimeMs = startTimeMs,
                    endTimeMs = startTimeMs + generated.durationMs,
                    targetDurationMs = durationMin * 60_000L,
                    totalCalories = generated.totalCalories,
                    avgBpm = generated.avgBpm,
                    notes = "synthetic"
                )
            )

            // Stamp each sample with the real session ID, then insert
            generated.samples.forEach { sample ->
                hrSampleDao.insert(sample.copy(sessionId = sessionId).toEntity())
            }
        }
    }

    // --- Private mapping helpers ---

    // Extension functions on the entity class, kept private to this file.
    // "toDomain()" converts a Room entity to a clean domain model.
    // "toEntity()" converts in the other direction.

    private fun SessionEntity.toDomain() = Session(
        id = id,
        startTimeMs = startTimeMs,
        endTimeMs = endTimeMs,
        targetDurationMs = targetDurationMs,
        totalCalories = totalCalories,
        avgBpm = avgBpm,
        notes = notes
    )

    private fun HrSampleEntity.toDomain() = HrSample(
        id = id,
        sessionId = sessionId,
        timestampMs = timestampMs,
        bpm = bpm,
        zone = zone,
        calPerMinute = calPerMinute,
        cumulativeCalories = cumulativeCalories
    )

    private fun HrSample.toEntity() = HrSampleEntity(
        id = id,
        sessionId = sessionId,
        timestampMs = timestampMs,
        bpm = bpm,
        zone = zone,
        calPerMinute = calPerMinute,
        cumulativeCalories = cumulativeCalories
    )
}
