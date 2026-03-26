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
}
