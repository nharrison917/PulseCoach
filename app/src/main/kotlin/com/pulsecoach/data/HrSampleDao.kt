package com.pulsecoach.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for heart rate sample rows.
 */
@Dao
interface HrSampleDao {

    /**
     * Insert one HR sample. Called every ~1 second during an active recording.
     * suspend = runs on a background coroutine — never blocks the main thread.
     */
    @Insert
    suspend fun insert(sample: HrSampleEntity)

    /**
     * Observe all samples for a session in chronological order.
     * Used by the live cal/min chart — emits a new list on every new insert.
     */
    @Query("SELECT * FROM hr_samples WHERE sessionId = :sessionId ORDER BY timestampMs ASC")
    fun getSamplesForSession(sessionId: Long): Flow<List<HrSampleEntity>>

    /**
     * Fetch all samples for a session as a one-shot list (not a Flow).
     * Used by CSV export, which needs all rows at once rather than a live stream.
     * suspend = runs off the main thread.
     */
    @Query("SELECT * FROM hr_samples WHERE sessionId = :sessionId ORDER BY timestampMs ASC")
    suspend fun getSamplesForSessionOnce(sessionId: Long): List<HrSampleEntity>
}
