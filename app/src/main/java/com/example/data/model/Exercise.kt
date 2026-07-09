package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "exercises")
data class Exercise(
    @PrimaryKey val id: String,
    val name: String,
    val muscleGroup: String, // e.g. Pecho, Espalda, Piernas, Hombros, Brazos, Core
    val description: String,
    val isCustom: Boolean = false
)
