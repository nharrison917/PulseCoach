package com.pulsecoach.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for session records.
 *
 * [insert] returns the auto-generated row ID so the caller can immediately link
 * HrSampleEntity rows to the new session without a second query.
 */
@Dao
interface SessionDao {

    /**
     * Insert a new session row and return the auto-generated ID.
     * Called at the moment the user taps Start.
     */
    @Insert
    suspend fun insert(session: SessionEntity): Long

    /**
     * Update an existing session row (e.g. to write endTimeMs, totalCalories, avgBpm at Stop).
     * Room matches on the primary key field [SessionEntity.id].
     */
    @Update
    suspend fun update(session: SessionEntity)

    /**
     * Observe all sessions, newest first.
     * Room emits a new list automatically whenever any session row changes —
     * the history screen stays up to date without manual refreshes.
     */
    @Query("SELECT * FROM sessions ORDER BY startTimeMs DESC")
    fun getAllSessions(): Flow<List<SessionEntity>>

    /** Fetch a single session by ID for detail/export views. */
    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun getSessionById(id: Long): SessionEntity?

    /** Delete sessions by their IDs. Called during multi-select deletion. */
    @Query("DELETE FROM sessions WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    /**
     * Returns completed sessions with avgBpm > 100 and actual duration > 10 minutes.
     * Duration is computed from timestamps so it works for all sessions regardless of
     * whether targetDurationMs was set (it was null before the duration picker existed).
     * Emits a new list whenever session rows change — the ViewModel reacts automatically
     * without needing a manual refresh after seeding or finishing a session.
     */
    @Query("""
        SELECT * FROM sessions
        WHERE endTimeMs IS NOT NULL
          AND avgBpm > 100
          AND (endTimeMs - startTimeMs) > 600000
        ORDER BY startTimeMs ASC
    """)
    fun getQualifyingSessions(): Flow<List<SessionEntity>>
}
