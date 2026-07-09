package com.example.data.repository

import com.example.data.db.WorkoutDao
import com.example.data.model.Exercise
import com.example.data.model.WorkoutSession
import com.example.data.model.WorkoutSet
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class WorkoutRepository(private val workoutDao: WorkoutDao) {

    val allExercises: Flow<List<Exercise>> = workoutDao.getAllExercises()
    val allSessions: Flow<List<WorkoutSession>> = workoutDao.getAllSessions()
    val allSets: Flow<List<WorkoutSet>> = workoutDao.getAllSets()

    suspend fun insertExercise(exercise: Exercise) {
        workoutDao.insertExercise(exercise)
    }

    suspend fun deleteExercise(exercise: Exercise) {
        workoutDao.deleteExercise(exercise)
    }

    suspend fun insertSession(session: WorkoutSession): Long {
        return workoutDao.insertSession(session)
    }

    suspend fun updateSession(session: WorkoutSession) {
        workoutDao.updateSession(session)
    }

    suspend fun deleteSession(session: WorkoutSession) {
        workoutDao.deleteSession(session)
        workoutDao.deleteSetsForSession(session.id)
    }

    fun getSetsForSession(sessionId: Int): Flow<List<WorkoutSet>> {
        return workoutDao.getSetsForSession(sessionId)
    }

    suspend fun insertSet(set: WorkoutSet) {
        workoutDao.insertSet(set)
    }

    suspend fun insertSets(sets: List<WorkoutSet>) {
        workoutDao.insertSets(sets)
    }

    suspend fun deleteSet(set: WorkoutSet) {
        workoutDao.deleteSet(set)
    }

    suspend fun prepopulateDefaultExercisesIfEmpty() {
        // Retrieve current list first
        val current = workoutDao.getAllExercises().first()
        if (current.isEmpty()) {
            val defaults = listOf(
                Exercise(
                    id = "bench_press",
                    name = "Press de Banca",
                    muscleGroup = "Pecho",
                    description = "Acuéstate en un banco plano, baja la barra al pecho medio y empuja hacia arriba. Ejercicio compuesto estrella para el pectoral."
                ),
                Exercise(
                    id = "squat",
                    name = "Sentadilla Libre",
                    muscleGroup = "Piernas",
                    description = "Coloca la barra en los trapecios, desciende flexionando cadera y rodillas hasta pasar los 90 grados y sube. Fortalece cuádriceps y glúteos."
                ),
                Exercise(
                    id = "deadlift",
                    name = "Peso Muerto",
                    muscleGroup = "Piernas",
                    description = "Levanta la barra desde el suelo manteniendo la espalda recta, extendiendo caderas y rodillas. Excelente para la cadena posterior (femorales, glúteos y espalda)."
                ),
                Exercise(
                    id = "pull_up",
                    name = "Dominadas",
                    muscleGroup = "Espalda",
                    description = "Cuélgate de una barra con agarre prono y jala tu cuerpo hacia arriba hasta que la barbilla pase la barra. Gran constructor de dorsales."
                ),
                Exercise(
                    id = "overhead_press",
                    name = "Press Militar con Barra",
                    muscleGroup = "Hombros",
                    description = "Empuja la barra verticalmente por encima de la cabeza desde la posición del hombro de pie. Desarrolla deltoides y tríceps."
                ),
                Exercise(
                    id = "barbell_curl",
                    name = "Curl de Bíceps con Barra",
                    muscleGroup = "Brazos",
                    description = "De pie, sujeta la barra con agarre supino y flexiona los codos llevando el peso hacia los hombros sin balancear el torso."
                ),
                Exercise(
                    id = "tricep_dips",
                    name = "Fondos en Paralelas",
                    muscleGroup = "Brazos",
                    description = "Sostén tu peso en barras paralelas, desciende flexionando los codos hasta unos 90 grados y empuja hacia arriba para aislar tríceps y pecho inferior."
                ),
                Exercise(
                    id = "lat_pulldown",
                    name = "Jalón al Pecho",
                    muscleGroup = "Espalda",
                    description = "Sentado en la polea alta, jala la barra hacia la parte superior del pecho contrayendo las escápulas."
                ),
                Exercise(
                    id = "leg_press",
                    name = "Prensa de Piernas",
                    muscleGroup = "Piernas",
                    description = "Sentado en la máquina de prensa, empuja la plataforma con los pies para trabajar cuádriceps reduciendo carga lumbar."
                ),
                Exercise(
                    id = "plank",
                    name = "Plancha Abdominal",
                    muscleGroup = "Core",
                    description = "Sostén el cuerpo en línea recta apoyado en antebrazos y puntas de pie, manteniendo el abdomen contraído por tiempo."
                )
            )
            workoutDao.insertExercises(defaults)
        }
    }
}
