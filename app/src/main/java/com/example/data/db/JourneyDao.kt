package com.example.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.JourneyEntity
import com.example.data.model.JourneyPointEntity
import com.example.data.model.JourneyStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface JourneyDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertJourney(journey: JourneyEntity): Long

    @Update
    suspend fun updateJourney(journey: JourneyEntity)

    @Delete
    suspend fun deleteJourney(journey: JourneyEntity)

    @Query("DELETE FROM journeys WHERE id = :id")
    suspend fun deleteJourneyById(id: Long)

    @Query("SELECT * FROM journeys WHERE status = 'ACTIVE' ORDER BY startTimeMillis DESC LIMIT 1")
    suspend fun getActiveJourney(): JourneyEntity?

    @Query("SELECT * FROM journeys WHERE status = 'ACTIVE' ORDER BY startTimeMillis DESC LIMIT 1")
    fun getActiveJourneyFlow(): Flow<JourneyEntity?>

    @Query("SELECT * FROM journeys WHERE id = :id")
    suspend fun getJourneyById(id: Long): JourneyEntity?

    @Query("SELECT * FROM journeys WHERE id = :id")
    fun getJourneyByIdFlow(id: Long): Flow<JourneyEntity?>

    @Query("SELECT * FROM journeys WHERE status = 'COMPLETED' ORDER BY endTimeMillis DESC")
    fun getAllCompletedJourneysFlow(): Flow<List<JourneyEntity>>

    @Query("SELECT * FROM journeys ORDER BY startTimeMillis DESC")
    fun getAllJourneysFlow(): Flow<List<JourneyEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPoint(point: JourneyPointEntity): Long

    @Query("SELECT * FROM journey_points WHERE journeyId = :journeyId ORDER BY timestampMillis ASC")
    suspend fun getPointsForJourney(journeyId: Long): List<JourneyPointEntity>

    @Query("SELECT * FROM journey_points WHERE journeyId = :journeyId ORDER BY timestampMillis ASC")
    fun getPointsForJourneyFlow(journeyId: Long): Flow<List<JourneyPointEntity>>

    @Query("SELECT COUNT(*) FROM journey_points WHERE journeyId = :journeyId")
    suspend fun getPointCountForJourney(journeyId: Long): Int

    @Query("UPDATE journeys SET status = 'INTERRUPTED' WHERE status = 'ACTIVE'")
    suspend fun markActiveJourneysInterrupted()
}
