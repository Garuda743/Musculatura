package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "workout_sets")
data class WorkoutSet(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val sessionId: Int,
    val exerciseId: String,
    val exerciseName: String, // Cached to display easily
    val setNumber: Int,
    val weightKg: Double,
    val reps: Int,
    val isWarmup: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)
