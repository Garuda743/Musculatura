package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.db.AppDatabase
import com.example.data.model.Exercise
import com.example.data.model.WorkoutSession
import com.example.data.model.WorkoutSet
import com.example.data.repository.WorkoutRepository
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

// Data models for active workout creation/tracking
data class WorkoutSetDraft(
    val id: String = UUID.randomUUID().toString(),
    val setNumber: Int,
    val weightKgText: String = "",
    val repsText: String = "",
    val isCompleted: Boolean = false,
    val isWarmup: Boolean = false
)

data class ActiveExerciseState(
    val id: String = UUID.randomUUID().toString(),
    val exercise: Exercise,
    val sets: List<WorkoutSetDraft> = listOf(WorkoutSetDraft(setNumber = 1))
)

data class ChatMessage(
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

data class PersonalRecord(
    val exerciseId: String,
    val exerciseName: String,
    val muscleGroup: String,
    val maxWeightKg: Double,
    val bestOneRepMax: Double,
    val lastUpdated: Long
)

class WorkoutViewModel(
    application: Application,
    private val repository: WorkoutRepository
) : AndroidViewModel(application) {

    // --- State Streams ---
    val allExercises: StateFlow<List<Exercise>> = repository.allExercises
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allSessions: StateFlow<List<WorkoutSession>> = repository.allSessions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allSets: StateFlow<List<WorkoutSet>> = repository.allSets
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- Active Workout State ---
    private val _activeSessionName = MutableStateFlow<String?>(null)
    val activeSessionName = _activeSessionName.asStateFlow()

    private val _activeExercises = MutableStateFlow<List<ActiveExerciseState>>(emptyList())
    val activeExercises = _activeExercises.asStateFlow()

    private val _timerSeconds = MutableStateFlow(0L)
    val timerSeconds = _timerSeconds.asStateFlow()

    private var timerJob: Job? = null

    // --- AI Coach Chat State ---
    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chatMessages = _chatMessages.asStateFlow()

    private val _isAiLoading = MutableStateFlow(false)
    val isAiLoading = _isAiLoading.asStateFlow()

    // --- Personal Records State (Derived) ---
    val personalRecords: StateFlow<List<PersonalRecord>> = allSets
        .combine(allExercises) { sets, exercises ->
            if (sets.isEmpty()) return@combine emptyList()
            
            val exerciseMap = exercises.associateBy { it.id }
            
            sets.groupBy { it.exerciseId }
                .mapNotNull { (exerciseId, exerciseSets) ->
                    val exercise = exerciseMap[exerciseId] ?: return@mapNotNull null
                    
                    val maxWeight = exerciseSets.maxOfOrNull { it.weightKg } ?: 0.0
                    
                    // Epley 1RM Formula: 1RM = weight * (1 + reps / 30.0)
                    // (only applicable if reps > 1, if reps = 1, 1RM is just the weight)
                    val best1RM = exerciseSets.maxOfOrNull { set ->
                        if (set.reps <= 1) set.weightKg 
                        else set.weightKg * (1.0 + set.reps / 30.0)
                    } ?: 0.0
                    
                    val lastSet = exerciseSets.maxByOrNull { it.timestamp }
                    
                    PersonalRecord(
                        exerciseId = exerciseId,
                        exerciseName = exercise.name,
                        muscleGroup = exercise.muscleGroup,
                        maxWeightKg = maxWeight,
                        bestOneRepMax = best1RM,
                        lastUpdated = lastSet?.timestamp ?: System.currentTimeMillis()
                    )
                }
                .sortedByDescending { it.lastUpdated }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        // Pre-populate default exercise library if empty
        viewModelScope.launch {
            repository.prepopulateDefaultExercisesIfEmpty()
        }
        
        // Add default greeting from Coach IA
        _chatMessages.value = listOf(
            ChatMessage(
                text = "¡Hola! Soy tu Coach de Fuerza y Rendimiento de IA. 💪\n\n¿En qué te puedo ayudar hoy? Puedo diseñar una rutina de entrenamiento efectiva, darte consejos sobre progresión de cargas o responder tus dudas sobre nutrición y descanso.",
                isUser = false
            )
        )
    }

    // --- Active Workout Actions ---

    fun startWorkout(name: String) {
        _activeSessionName.value = name
        _activeExercises.value = emptyList()
        _timerSeconds.value = 0L
        
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (true) {
                delay(1000)
                _timerSeconds.value++
            }
        }
    }

    fun addExerciseToActiveWorkout(exercise: Exercise) {
        val current = _activeExercises.value.toMutableList()
        // Prevent duplicate exercises in the active workout to keep it clean, 
        // but if they want to add it twice, we can just let them add sets
        if (current.none { it.exercise.id == exercise.id }) {
            current.add(ActiveExerciseState(exercise = exercise))
            _activeExercises.value = current
        }
    }

    fun removeExerciseFromActiveWorkout(activeExerciseId: String) {
        val current = _activeExercises.value.toMutableList()
        current.removeAll { it.id == activeExerciseId }
        _activeExercises.value = current
    }

    fun addSetToExercise(activeExerciseId: String) {
        val current = _activeExercises.value.toMutableList()
        val index = current.indexOfFirst { it.id == activeExerciseId }
        if (index != -1) {
            val item = current[index]
            val nextSetNumber = item.sets.size + 1
            // Copy previous set values to make it easy for user
            val lastSet = item.sets.lastOrNull()
            val newSet = WorkoutSetDraft(
                setNumber = nextSetNumber,
                weightKgText = lastSet?.weightKgText ?: "",
                repsText = lastSet?.repsText ?: ""
            )
            current[index] = item.copy(sets = item.sets + newSet)
            _activeExercises.value = current
        }
    }

    fun removeSetFromExercise(activeExerciseId: String, setId: String) {
        val current = _activeExercises.value.toMutableList()
        val index = current.indexOfFirst { it.id == activeExerciseId }
        if (index != -1) {
            val item = current[index]
            if (item.sets.size > 1) {
                val updatedSets = item.sets.filter { it.id != setId }
                    .mapIndexed { idx, set -> set.copy(setNumber = idx + 1) }
                current[index] = item.copy(sets = updatedSets)
                _activeExercises.value = current
            }
        }
    }

    fun updateSetDraft(activeExerciseId: String, setId: String, updatedSet: WorkoutSetDraft) {
        val current = _activeExercises.value.toMutableList()
        val exerciseIndex = current.indexOfFirst { it.id == activeExerciseId }
        if (exerciseIndex != -1) {
            val item = current[exerciseIndex]
            val updatedSets = item.sets.map { set ->
                if (set.id == setId) updatedSet else set
            }
            current[exerciseIndex] = item.copy(sets = updatedSets)
            _activeExercises.value = current
        }
    }

    fun discardWorkout() {
        timerJob?.cancel()
        timerJob = null
        _activeSessionName.value = null
        _activeExercises.value = emptyList()
        _timerSeconds.value = 0L
    }

    fun finishWorkout(notes: String) {
        val sessionName = _activeSessionName.value ?: "Entrenamiento"
        val duration = _timerSeconds.value
        val exercisesList = _activeExercises.value
        
        viewModelScope.launch {
            // Save Session
            val session = WorkoutSession(
                name = sessionName,
                durationSeconds = duration,
                notes = notes
            )
            val sessionId = repository.insertSession(session).toInt()
            
            // Save Sets
            val setsToSave = mutableListOf<WorkoutSet>()
            for (exerciseState in exercisesList) {
                for (setDraft in exerciseState.sets) {
                    if (setDraft.isCompleted) {
                        val weight = setDraft.weightKgText.toDoubleOrNull() ?: 0.0
                        val reps = setDraft.repsText.toIntOrNull() ?: 0
                        setsToSave.add(
                            WorkoutSet(
                                sessionId = sessionId,
                                exerciseId = exerciseState.exercise.id,
                                exerciseName = exerciseState.exercise.name,
                                setNumber = setDraft.setNumber,
                                weightKg = weight,
                                reps = reps,
                                isWarmup = setDraft.isWarmup
                            )
                        )
                    }
                }
            }
            
            if (setsToSave.isNotEmpty()) {
                repository.insertSets(setsToSave)
            }
            
            // Reset Active state
            discardWorkout()
        }
    }

    // --- Exercise Library Actions ---

    fun createCustomExercise(name: String, muscleGroup: String, description: String) {
        val cleanName = name.trim()
        if (cleanName.isNotEmpty()) {
            val id = "custom_" + cleanName.lowercase().replace(" ", "_")
            val newExercise = Exercise(
                id = id,
                name = cleanName,
                muscleGroup = muscleGroup,
                description = description.trim().ifEmpty { "Ejercicio personalizado para $muscleGroup." },
                isCustom = true
            )
            viewModelScope.launch {
                repository.insertExercise(newExercise)
            }
        }
    }

    fun deleteExercise(exercise: Exercise) {
        viewModelScope.launch {
            repository.deleteExercise(exercise)
        }
    }

    fun deleteSession(session: WorkoutSession) {
        viewModelScope.launch {
            repository.deleteSession(session)
        }
    }

    // --- AI Coach Actions ---

    fun sendMessageToCoach(messageText: String) {
        val userText = messageText.trim()
        if (userText.isEmpty()) return

        // Add user message to list
        val currentMessages = _chatMessages.value.toMutableList()
        currentMessages.add(ChatMessage(text = userText, isUser = true))
        _chatMessages.value = currentMessages

        _isAiLoading.value = true

        viewModelScope.launch {
            val responseText = try {
                // Gather some training history context to make the coaching personalized!
                val sessionsCount = allSessions.value.size
                val prs = personalRecords.value
                val historyContext = buildString {
                    append("Historial del atleta: ")
                    append("Ha completado $sessionsCount sesiones de entrenamiento. ")
                    if (prs.isNotEmpty()) {
                        append("Sus récords personales actuales son: ")
                        prs.take(5).forEach { pr ->
                            append("${pr.exerciseName} con ${pr.maxWeightKg}kg (1RM estimado de ${pr.bestOneRepMax.toInt()}kg); ")
                        }
                    } else {
                        append("Aún no ha registrado levantamientos.")
                    }
                }

                // Call Gemini API if Key is present
                val geminiKey = BuildConfig.GEMINI_API_KEY
                if (geminiKey.isNotEmpty() && geminiKey != "MY_GEMINI_API_KEY") {
                    callGeminiApi(userText, historyContext, geminiKey)
                } else {
                    // Fallback to high-quality local rules-based advisor
                    delay(1500) // Simulate coaching thought process
                    getLocalCoachResponse(userText, prs)
                }
            } catch (e: Exception) {
                "Lo siento, ocurrió un error al comunicarme con mi servidor de IA: ${e.localizedMessage}. Pero puedo decirte que la clave para la hipertrofia efectiva es la sobrecarga progresiva y un superávit calórico controlado con 1.6g a 2.2g de proteína por kg al día."
            }

            _isAiLoading.value = false
            val updatedMessages = _chatMessages.value.toMutableList()
            updatedMessages.add(ChatMessage(text = responseText, isUser = false))
            _chatMessages.value = updatedMessages
        }
    }

    private suspend fun callGeminiApi(
        userMessage: String,
        historyContext: String,
        apiKey: String
    ): String = withContext(Dispatchers.IO) {
        val client = OkHttpClient.Builder()
            .connectTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .build()

        val systemInstruction = """
            Eres un Coach de Musculación y Rendimiento Deportivo experto. Te llamas 'Coach IA'. 
            Tus respuestas deben ser motivadoras, científicamente rigurosas (basadas en ciencia del deporte) pero accesibles y prácticas.
            Siempre habla en español. Responde de forma estructurada, usando negritas, listas y saltos de línea para que sea fácil de leer en un celular.
            
            Usa el siguiente contexto sobre el historial del atleta para personalizar tus recomendaciones si es relevante:
            $historyContext
        """.trimIndent()

        // Build request body according to API Spec in SKILL.md
        val requestJson = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", userMessage)
                        })
                    })
                })
            })
            put("systemInstruction", JSONObject().apply {
                put("parts", JSONArray().apply {
                    put(JSONObject().apply {
                        put("text", systemInstruction)
                    })
                })
            })
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.7)
            })
        }

        val mediaType = "application/json".toMediaType()
        val body = requestJson.toString().toRequestBody(mediaType)

        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$apiKey")
            .post(body)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("Error de red: ${response.code} ${response.message}")
            }
            val responseBody = response.body?.string() ?: throw Exception("Respuesta vacía")
            val jsonObject = JSONObject(responseBody)
            val candidates = jsonObject.getJSONArray("candidates")
            val firstCandidate = candidates.getJSONObject(0)
            val contentObj = firstCandidate.getJSONObject("content")
            val parts = contentObj.getJSONArray("parts")
            parts.getJSONObject(0).getString("text")
        }
    }

    private fun getLocalCoachResponse(prompt: String, prs: List<PersonalRecord>): String {
        val query = prompt.lowercase()
        return when {
            query.contains("rutina") || query.contains("entrenar") || query.contains("plan") -> {
                """
                Aquí tienes una **Rutina de Empuje / Tirón / Pierna (PPL)** clásica y altamente efectiva para ganar masa muscular:
                
                📅 **Día 1: Empuje (Pecho, Hombro, Tríceps)**
                - **Press de Banca**: 4 series x 6-8 repeticiones (fuerza/hipertrofia).
                - **Press Militar**: 3 series x 8-10 repeticiones.
                - **Aperturas con mancuernas**: 3 series x 12 repeticiones.
                - **Elevaciones laterales**: 4 series x 12-15 repeticiones.
                - **Fondos en Paralelas**: 3 series al fallo técnico.
                
                📅 **Día 2: Tirón (Espalda, Bíceps, Core)**
                - **Peso Muerto**: 3 series x 5 repeticiones (fuerza base).
                - **Dominadas**: 4 series x Max repeticiones (añade lastre si puedes).
                - **Remo con Barra**: 3 series x 8-10 repeticiones.
                - **Curl de Bíceps**: 3 series x 10-12 repeticiones.
                - **Plancha Abdominal**: 3 series aguantando 1 minuto.
                
                📅 **Día 3: Pierna (Cuádriceps, Femorales, Glúteos)**
                - **Sentadilla Libre**: 4 series x 6-8 repeticiones.
                - **Prensa de Piernas**: 3 series x 10-12 repeticiones.
                - **Peso Muerto Rumano**: 3 series x 10 repeticiones (femoral).
                - **Zancadas con peso**: 3 series x 12 pasos por pierna.
                - **Elevación de Talones**: 4 series x 15-20 repeticiones (pantorrilla).
                
                💡 *Consejo de Rendimiento:* Descansa **2 a 3 minutos** en los ejercicios compuestos pesados para maximizar la recuperación del sistema nervioso y la fuerza.
                
                *(Nota: Si configuras tu clave GEMINI_API_KEY en la sección de secretos, podré generar rutinas personalizadas dinámicamente).*
                """.trimIndent()
            }
            query.contains("estancamiento") || query.contains("estancado") || query.contains("romper") || query.contains("progreso") -> {
                """
                Romper un estancamiento requiere afinar variables específicas. Sigue este protocolo:
                
                1. 📈 **Sobrecarga Progresiva de verdad:** No busques solo subir peso. Puedes progresar añadiendo una repetición más con el mismo peso, mejorando el rango de movimiento o disminuyendo el tiempo de descanso.
                2. 📉 **Semana de Descarga (Deload):** Si llevas más de 8-12 semanas entrenando pesado, reduce la carga al 50-60% o el volumen a la mitad durante una semana para disipar la fatiga acumulada.
                3. 🍎 **Superávit Calórico:** No puedes construir músculo nuevo sin energía. Asegúrate de estar consumiendo un ligero superávit (+200-300 kcal de tus calorías de mantenimiento) y al menos **1.8g de proteína por kg de peso**.
                4. 😴 **Optimiza el Sueño:** El crecimiento muscular ocurre en la cama, no en el gimnasio. Duerme **7-8 horas** de calidad.
                """.trimIndent()
            }
            query.contains("nutricion") || query.contains("dieta") || query.contains("proteina") || query.contains("comida") || query.contains("suplemento") -> {
                """
                La nutrición es el combustible del rendimiento y la construcción muscular. Aquí están las pautas fundamentales:
                
                🍗 **Proteínas:** El bloque constructor. Apunta a **1.6 a 2.2 gramos de proteína por kg de peso corporal al día**. Fuentes: Pollo, huevos, pescado, carne magra, tofu, tempeh, legumbres y proteína de suero (whey).
                🍚 **Carbohidratos:** La gasolina para entrenar pesado. No les temas. Consume fuentes complejas como avena, arroz integral, camote (batata) y quinoa antes de entrenar para llenar tus reservas de glucógeno.
                🥑 **Grasas Saludables:** Clave para la regulación hormonal (incluyendo testosterona). Consume aguacate, frutos secos, aceite de oliva y pescados grasos (como salmón).
                💦 **Hidratación:** Una deshidratación de solo el 2% reduce tu fuerza hasta en un 10%. Bebe entre **3 y 4 litros de agua al día** si entrenas intenso.
                🧪 **Suplementos con Evidencia Científica:**
                - **Creatina Monohidratada:** 3-5g diarios (mejora fuerza, potencia y volumen celular).
                - **Proteína de Suero (Whey):** Conveniencia para llegar a tus requerimientos.
                - **Cafeína:** 150-300mg como pre-entreno (reduce percepción del esfuerzo).
                """.trimIndent()
            }
            else -> {
                """
                Para optimizar tu rendimiento, enfócate en el concepto de **sobrecarga progresiva**. 
                
                Actualmente tienes registrados **${prs.size} récords personales** en la aplicación. 
                ${if (prs.isNotEmpty()) "Tu récord actual más pesado es en **${prs.maxByOrNull { it.maxWeightKg }?.exerciseName}** con **${prs.maxByOrNull { it.maxWeightKg }?.maxWeightKg}kg**. ¡Increíble trabajo!" else "Empieza a registrar tus sesiones en la pestaña **Entrenar** para que pueda analizar tus levantamientos."}
                
                ¿Tienes alguna pregunta específica sobre cómo realizar algún ejercicio, cuántas series hacer, o cómo estructurar tu alimentación para volumen o definición?
                """.trimIndent()
            }
        }
    }
}

// Simple Factory for instantiating our AndroidViewModel
class WorkoutViewModelFactory(
    private val application: Application,
    private val repository: WorkoutRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(WorkoutViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return WorkoutViewModel(application, repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
