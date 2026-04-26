package com.uniandes.sport.viewmodels.profesores

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.uniandes.sport.models.Profesor
import com.uniandes.sport.models.Review
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import com.google.firebase.ktx.Firebase
import com.google.firebase.messaging.ktx.messaging

/**
 * IMPLEMENTACIÓN DE MULTITHREADING (CORRUTINAS)
 * Esta clase demuestra tres estrategias fundamentales de concurrencia en Kotlin:
 * 1. Corrutina con Dispatcher: Uso explícito de Dispatchers.IO para operaciones pesadas.
 * 2. Múltiples corrutinas anidadas: Una corrutina lanza otra en segundo plano (nested).
 * 3. Coordinación I/O + Main: Ejecución en segundo plano y actualización en el hilo principal.
 */
class FirestoreProfesoresViewModel : ViewModel(), ProfesoresViewModelInterface {

    private val db = FirebaseFirestore.getInstance()

    private val _profesores = MutableStateFlow<List<Profesor>>(emptyList())
    override val profesores: StateFlow<List<Profesor>> = _profesores.asStateFlow()

    private val _reviews = MutableStateFlow<List<Review>>(emptyList())
    override val reviews: StateFlow<List<Review>> = _reviews.asStateFlow()

    private val _bookingRequests = MutableStateFlow<List<com.uniandes.sport.models.BookingRequest>>(emptyList())
    override val bookingRequests: StateFlow<List<com.uniandes.sport.models.BookingRequest>> = _bookingRequests.asStateFlow()

    private var cachedProfesores: List<Profesor>? = null
    private var reviewsListener: ListenerRegistration? = null
    private var requestsListener: ListenerRegistration? = null

    /**
     * CRITERIO: Una corrutina ejecutando en Input/Output y otra encargada del Main (UI).
     */
    override fun fetchProfesores(
        onSuccess: (List<Profesor>) -> Unit, 
        onFailure: (Exception) -> Unit
    ) {
        // RAM (L1) - Síncrona, no requiere corrutina
        if (cachedProfesores != null && cachedProfesores!!.isNotEmpty()) {
            _profesores.value = cachedProfesores!!
            onSuccess(cachedProfesores!!)
            return
        }

        // Definimos el Scope (viewModelScope) para que la corrutina muera si el ViewModel se destruye
        viewModelScope.launch(Dispatchers.Main) { // Hilo Principal (Main)
            try {
                // Cambiamos el contexto a IO para no bloquear la interfaz de usuario
                val cachedList = withContext(Dispatchers.IO) { // ESTRATEGIA: Input/Output Dispatcher
                    Log.d("Coroutine_Debug", "Fetching from Disk Cache (IO Thread)")
                    val snapshot = db.collection("profesores")
                        .get(com.google.firebase.firestore.Source.CACHE)
                        .await() // Suspensión: Espera el resultado sin bloquear el hilo
                    
                    snapshot.mapNotNull { doc ->
                        try { doc.toObject(Profesor::class.java).apply { id = doc.id } } catch (e: Exception) { null }
                    }
                }

                if (cachedList.isNotEmpty()) {
                    cachedProfesores = cachedList
                    _profesores.value = cachedList // Actualización segura en el Main Thread
                    onSuccess(cachedList)
                    
                    // CRITERIO: Múltiples corrutinas, una dentro de la otra (Nested Coroutines)
                    // Lanzamos una corrutina hermana para actualizar el caché desde el servidor en background
                    launch(Dispatchers.IO) {
                        Log.d("Coroutine_Debug", "Starting nested coroutine for background sync")
                        syncFromServer()
                    }
                } else {
                    // Si no hay caché, forzamos descarga desde el servidor
                    syncFromServer()
                    onSuccess(_profesores.value)
                }
            } catch (e: Exception) {
                Log.e("Coroutine_Debug", "Error in main fetch coroutine", e)
                // Fallback al servidor si falla el caché
                viewModelScope.launch(Dispatchers.IO) { syncFromServer() }
                onFailure(e)
            }
        }
    }

    /**
     * Función privada de suspensión que maneja la lógica de I/O pura.
     */
    private suspend fun syncFromServer() = withContext(Dispatchers.IO) {
        try {
            Log.d("Coroutine_Debug", "Syncing from Server (IO Thread)")
            val snapshot = db.collection("profesores")
                .get(com.google.firebase.firestore.Source.SERVER)
                .await()

            val list = snapshot.mapNotNull { doc ->
                try {
                    val p = doc.toObject(Profesor::class.java).apply { id = doc.id }
                    if (p.disponibilidad == "A convenir") {
                        p.disponibilidad = "To be agreed"
                        doc.reference.update("disponibilidad", "To be agreed")
                    }
                    p
                } catch (e: Exception) { null }
            }

            // Actualizamos la UI volviendo al Main Thread internamente o usando StateFlow
            cachedProfesores = list
            _profesores.value = list
        } catch (e: Exception) {
            Log.e("Coroutine_Debug", "Cloud Sync Failed", e)
        }
    }

    override fun refreshProfesores(onComplete: () -> Unit) {
        cachedProfesores = null
        viewModelScope.launch(Dispatchers.Main) {
            syncFromServer()
            onComplete()
        }
    }

    override fun fetchReviews(profesorId: String) {
        reviewsListener?.remove()
        reviewsListener = db.collection("profesores").document(profesorId)
            .collection("reviews")
            .addSnapshotListener { snapshot, e ->
                if (e != null) return@addSnapshotListener
                if (snapshot != null) {
                    _reviews.value = snapshot.mapNotNull { doc ->
                        try { doc.toObject(Review::class.java).apply { id = doc.id } } catch (parseError: Exception) { null }
                    }
                }
            }
    }

    override fun fetchBookingRequestsBySport(sport: String) {
        requestsListener?.remove()
        requestsListener = db.collection("coach_requests")
            .whereEqualTo("sport", sport)
            .addSnapshotListener { snapshot, e ->
                if (e != null) return@addSnapshotListener
                if (snapshot != null) {
                    val list = snapshot.map { doc ->
                        doc.toObject(com.uniandes.sport.models.BookingRequest::class.java).apply { id = doc.id }
                    }
                    _bookingRequests.value = list.sortedByDescending { it.createdAt }
                }
            }
    }

    override fun syncCoachingLeadsTopic(sport: String) {
        val topic = "sport_leads_${sport.lowercase().replace(Regex("[^a-z0-9]"), "_")}"
        viewModelScope.launch(Dispatchers.IO) { // Corrutina con Dispatcher I/O para red
            try {
                Firebase.messaging.subscribeToTopic(topic).await()
                Log.d("FCM", "Subscribed to $topic")
            } catch (e: Exception) {
                Log.e("FCM", "Subscribe failed", e)
            }
        }
    }

    override fun createProfesor(
        profesor: Profesor,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) { // Operación de escritura en background (I/O)
            try {
                val documentRef = if (profesor.id.isNotEmpty()) {
                    db.collection("profesores").document(profesor.id)
                } else {
                    db.collection("profesores").document()
                }
                val profToSave = profesor.copy(id = documentRef.id)
                documentRef.set(profToSave).await()
                
                withContext(Dispatchers.Main) { onSuccess() } // Regresamos al Main para notificar éxito
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { onFailure(e) }
            }
        }
    }

    override fun addReview(
        profesorId: String,
        review: Review,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) { // Manejo asíncrono robusto
            try {
                val reviewsCollection = db.collection("profesores").document(profesorId).collection("reviews")
                val existing = reviewsCollection.whereEqualTo("estudiante", review.estudiante).limit(1).get().await()
                
                if (!existing.isEmpty) {
                    withContext(Dispatchers.Main) { onFailure(Exception("Already reviewed")) }
                    return@launch
                }

                val reviewRef = reviewsCollection.document()
                val reviewToSave = review.copy(id = reviewRef.id, rating = review.rating.coerceIn(1, 5))

                db.runTransaction { transaction ->
                    val profRef = db.collection("profesores").document(profesorId)
                    val profSnapshot = transaction.get(profRef)
                    val currentTotal = profSnapshot.getLong("totalReviews") ?: 0
                    val currentRating = profSnapshot.getDouble("rating") ?: 0.0
                    val newTotal = currentTotal + 1
                    val newRating = ((currentRating * currentTotal) + reviewToSave.rating) / newTotal

                    transaction.set(reviewRef, reviewToSave)
                    transaction.update(profRef, "totalReviews", newTotal)
                    transaction.update(profRef, "rating", newRating)
                }.await()

                // CRITERIO: Corrutina anidada (nested coroutine)
                // Desde la corrutina IO principal lanzamos una corrutina hija
                // en background para re-sincronizar el conteo total sin bloquear al usuario.
                launch(Dispatchers.IO) {
                    Log.d("Coroutine_Debug", "Nested coroutine: re-syncing review count in background")
                    syncReviewsCountInternal(profesorId)
                }

                withContext(Dispatchers.Main) { onSuccess() }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { onFailure(e) }
            }
        }
    }

    /**
     * CRITERIO: Función de I/O pura invocada desde corrutina anidada.
     * Recalcula el rating real desde el servidor y lo persiste en Firestore.
     * Solo se llama internamente (desde una nested coroutine en addReview).
     */
    private suspend fun syncReviewsCountInternal(profesorId: String) = withContext(Dispatchers.IO) {
        try {
            val profRef = db.collection("profesores").document(profesorId)
            val snapshot = profRef.collection("reviews")
                .get(com.google.firebase.firestore.Source.SERVER).await()
            val realCount = snapshot.size()
            val totalRating = snapshot.sumOf { it.getDouble("rating") ?: 0.0 }
            val newRating = if (realCount > 0) totalRating / realCount else 0.0
            profRef.update(mapOf("totalReviews" to realCount, "rating" to newRating)).await()
            Log.d("Coroutine_Debug", "Nested coroutine: review count synced. Total=$realCount, Rating=$newRating")
        } catch (e: Exception) {
            Log.e("Coroutine_Debug", "Nested coroutine: review count sync failed", e)
        }
    }

    override fun syncReviewsCount(
        profesorId: String,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val profRef = db.collection("profesores").document(profesorId)
                val snapshot = profRef.collection("reviews").get(com.google.firebase.firestore.Source.SERVER).await()
                val realCount = snapshot.size()
                val totalRating = snapshot.sumOf { it.getDouble("rating") ?: 0.0 }
                val newRating = if (realCount > 0) totalRating / realCount else 0.0

                profRef.update(mapOf("totalReviews" to realCount, "rating" to newRating)).await()
                
                withContext(Dispatchers.Main) { onSuccess() }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { onFailure(e) }
            }
        }
    }

    override fun acceptBookingRequest(
        requestId: String,
        professorId: String,
        professorName: String,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                db.collection("coach_requests").document(requestId)
                    .update(mapOf(
                        "status" to "accepted",
                        "targetProfesorId" to professorId,
                        "targetProfesorName" to professorName
                    )).await()
                withContext(Dispatchers.Main) { onSuccess() }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { onFailure(e) }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        reviewsListener?.remove()
        requestsListener?.remove()
    }
}
