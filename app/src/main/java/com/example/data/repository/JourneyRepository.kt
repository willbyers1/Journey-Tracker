package com.example.data.repository

import com.example.data.db.JourneyDao
import com.example.data.model.JourneyEntity
import com.example.data.model.JourneyPointEntity
import com.example.data.model.JourneyStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class JourneyRepository(private val dao: JourneyDao) {

    val activeJourneyFlow: Flow<JourneyEntity?> = dao.getActiveJourneyFlow()

    val completedJourneysFlow: Flow<List<JourneyEntity>> = dao.getAllCompletedJourneysFlow()

    val allJourneysFlow: Flow<List<JourneyEntity>> = dao.getAllJourneysFlow()

    suspend fun getActiveJourney(): JourneyEntity? = withContext(Dispatchers.IO) {
        dao.getActiveJourney()
    }

    suspend fun getJourneyById(id: Long): JourneyEntity? = withContext(Dispatchers.IO) {
        dao.getJourneyById(id)
    }

    fun getJourneyByIdFlow(id: Long): Flow<JourneyEntity?> = dao.getJourneyByIdFlow(id)

    fun getPointsForJourneyFlow(journeyId: Long): Flow<List<JourneyPointEntity>> =
        dao.getPointsForJourneyFlow(journeyId)

    suspend fun getPointsForJourney(journeyId: Long): List<JourneyPointEntity> =
        withContext(Dispatchers.IO) {
            dao.getPointsForJourney(journeyId)
        }

    suspend fun startJourney(journey: JourneyEntity): Long = withContext(Dispatchers.IO) {
        dao.insertJourney(journey)
    }

    suspend fun updateJourney(journey: JourneyEntity) = withContext(Dispatchers.IO) {
        dao.updateJourney(journey)
    }

    suspend fun insertPoint(point: JourneyPointEntity): Long = withContext(Dispatchers.IO) {
        dao.insertPoint(point)
    }

    suspend fun completeJourney(
        journeyId: Long,
        endTimeMillis: Long,
        durationSeconds: Long,
        totalDistanceMeters: Double
    ) = withContext(Dispatchers.IO) {
        val existing = dao.getJourneyById(journeyId)
        if (existing != null) {
            val updated = existing.copy(
                endTimeMillis = endTimeMillis,
                durationSeconds = durationSeconds,
                totalDistanceMeters = totalDistanceMeters,
                status = JourneyStatus.COMPLETED
            )
            dao.updateJourney(updated)
        }
    }

    suspend fun cancelJourney(journeyId: Long) = withContext(Dispatchers.IO) {
        val existing = dao.getJourneyById(journeyId)
        if (existing != null) {
            val updated = existing.copy(
                endTimeMillis = System.currentTimeMillis(),
                status = JourneyStatus.CANCELLED
            )
            dao.updateJourney(updated)
        }
    }

    suspend fun deleteJourneyById(id: Long) = withContext(Dispatchers.IO) {
        dao.deleteJourneyById(id)
    }

    suspend fun markActiveJourneysInterrupted() = withContext(Dispatchers.IO) {
        dao.markActiveJourneysInterrupted()
    }
}
