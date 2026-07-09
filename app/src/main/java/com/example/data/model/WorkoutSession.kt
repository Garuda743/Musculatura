package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "workout_sessions")
data class WorkoutSession(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val date: Long = System.currentTimeMillis(),
    val durationSeconds: Long = 0,
    val notes: String = ""
)
