package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class JourneyStatus {
    ACTIVE,
    COMPLETED,
    CANCELLED,
    INTERRUPTED
}

@Entity(tableName = "journeys")
data class JourneyEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val departureName: String,
    val departureLatitude: Double,
    val departureLongitude: Double,
    val destinationName: String,
    val destinationLatitude: Double,
    val destinationLongitude: Double,
    val arrivalRadiusMeters: Float = 50f,
    val startTimeMillis: Long,
    val endTimeMillis: Long = 0L,
    val durationSeconds: Long = 0L,
    val totalDistanceMeters: Double = 0.0,
    val status: JourneyStatus = JourneyStatus.ACTIVE
)
