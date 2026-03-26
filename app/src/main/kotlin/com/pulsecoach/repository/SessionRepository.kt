package com.pulsecoach.repository

import com.pulsecoach.data.HrSampleDao
import com.pulsecoach.data.HrSampleEntity
import com.pulsecoach.data.SessionDao
import com.pulsecoach.data.SessionEntity
import com.pulsecoach.model.HrSample
import com.pulsecoach.model.Session
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

    /** Observe all HR samples for a session (live, for charting). */
    fun getSamplesForSession(sessionId: Long): Flow<List<HrSample>> =
        hrSampleDao.getSamplesForSession(sessionId).map { list -> list.map { it.toDomain() } }

    /** Fetch all HR samples for a session once (for CSV export). */
    suspend fun getSamplesForSessionOnce(sessionId: Long): List<HrSample> =
        hrSampleDao.getSamplesForSessionOnce(sessionId).map { it.toDomain() }

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
