package com.uniandes.sport.viewmodels.auth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.uniandes.sport.models.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

class FirebaseAuthViewModel: AuthViewModelInterface, ViewModel() {
    private val auth: FirebaseAuth = Firebase.auth
    private val db = FirebaseFirestore.getInstance()

    private var _email: String by mutableStateOf("")
    override var email: String
        get() = _email
        set(value) {
            _email = value
        }

    private var _password: String by mutableStateOf("")
    override var password: String
        get() = _password
        set(value) {
            _password = value
        }

    private var _fullName: String by mutableStateOf("")
    override var fullName: String
        get() = _fullName
        set(value) {
            _fullName = value
        }

    private var _program: String by mutableStateOf("")
    override var program: String
        get() = _program
        set(value) {
            _program = value
        }

    private var _semester: String by mutableStateOf("")
    override var semester: String
        get() = _semester
        set(value) {
            _semester = value
        }

    private var _mainSport: String by mutableStateOf("")
    override var mainSport: String
        get() = _mainSport
        set(value) {
            _mainSport = value
        }

    override fun register(
        onSuccess: (result: User) -> Unit,
        onFailure: (exception: Exception) -> Unit
    ) {
        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val uid = auth.currentUser?.uid ?: ""
                    val userProfile = User(
                        uid = uid,
                        email = email,
                        fullName = fullName,
                        program = program,
                        semester = semester.toIntOrNull() ?: 0,
                        mainSport = mainSport
                    )
                    
                    db.collection("users").document(uid).set(userProfile)
                        .addOnSuccessListener {
                            onSuccess(userProfile)
                        }
                        .addOnFailureListener { e ->
                            onFailure(e)
                        }
                } else {
                    onFailure(task.exception ?: Exception("Unknown exception."))
                }
            }
    }

    override fun login(
        onSuccess: (result: User) -> Unit,
        onFailure: (exception: Exception) -> Unit
    ) {
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val uid = auth.currentUser?.uid ?: ""
                    db.collection("users").document(uid).get()
                        .addOnSuccessListener { document ->
                            if (document != null && document.exists()) {
                                val userProfile = document.toObject(User::class.java)
                                if (userProfile != null) {
                                    onSuccess(userProfile)
                                    return@addOnSuccessListener
                                }
                            }
                            // Fallback if no extended profile exists
                            onSuccess(User(uid = uid, email = email))
                        }
                        .addOnFailureListener {
                            onSuccess(User(uid = uid, email = email))
                        }
                } else {
                    onFailure(task.exception ?: Exception("Unknown exception."))
                }
            }
    }

    override fun loginWithGoogleIdToken(
        idToken: String,
        onSuccess: (result: User, isNewUser: Boolean) -> Unit,
        onFailure: (exception: Exception) -> Unit
    ) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener { task ->
                if (!task.isSuccessful) {
                    onFailure(task.exception ?: Exception("Unknown exception."))
                    return@addOnCompleteListener
                }

                val firebaseUser = auth.currentUser
                if (firebaseUser == null) {
                    onFailure(Exception("No se pudo obtener el usuario autenticado."))
                    return@addOnCompleteListener
                }

                val uid = firebaseUser.uid
                val fallbackUser = User(
                    uid = uid,
                    email = firebaseUser.email ?: "",
                    fullName = firebaseUser.displayName ?: "",
                    photoUrl = firebaseUser.photoUrl?.toString()
                )

                db.collection("users").document(uid).get()
                    .addOnSuccessListener { document ->
                        if (document != null && document.exists()) {
                            val userProfile = document.toObject(User::class.java)
                            if (userProfile != null) {
                                onSuccess(userProfile, false)
                                return@addOnSuccessListener
                            }
                        }

                        val newProfile = fallbackUser

                        db.collection("users").document(uid).set(newProfile)
                            .addOnSuccessListener { onSuccess(newProfile, true) }
                            // Do not block login on profile write issues.
                            .addOnFailureListener { onSuccess(fallbackUser, true) }
                    }
                    .addOnFailureListener {
                        onSuccess(fallbackUser, true)
                    }
            }
    }

    override fun saveOnboardingData(onSuccess: () -> Unit, onFailure: (exception: Exception) -> Unit) {
        val uid = auth.currentUser?.uid
        if (uid.isNullOrBlank()) {
            onFailure(Exception("Usuario no autenticado"))
            return
        }

        val updates = mapOf(
            "program" to program,
            "semester" to (semester.toIntOrNull() ?: 0),
            "mainSport" to mainSport
        )

        db.collection("users").document(uid)
            .update(updates)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { e -> onFailure(e) }
    }

    override fun isUserLoggedIn(
        onSuccess: (isLoggedIn: Boolean) -> Unit,
        onFailure: (exception: Exception) -> Unit
    ) {
        val currentUser = auth.currentUser
        onSuccess(currentUser != null)
    }

    override fun recoverPassword(onSuccess: () -> Unit, onFailure: (exception: Exception) -> Unit) {
        auth.sendPasswordResetEmail(email)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    onSuccess()
                } else {
                    onFailure(task.exception ?: Exception("Unknown exception."))
                }
            }
    }

    override fun getUser(
        onSuccess: (result: User) -> Unit,
        onFailure: (exception: Exception) -> Unit
    ) {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            db.collection("users").document(currentUser.uid).get()
                .addOnSuccessListener { document ->
                    if (document != null && document.exists()) {
                        val userProfile = document.toObject(User::class.java)
                        if (userProfile != null) {
                            onSuccess(userProfile)
                            return@addOnSuccessListener
                        }
                    }
                    onSuccess(User(uid = currentUser.uid, email = currentUser.email ?: ""))
                }
                .addOnFailureListener {
                    onSuccess(User(uid = currentUser.uid, email = currentUser.email ?: ""))
                }
        } else {
            onFailure(Exception("User is not logged in."))
        }
    }

    override fun logout(onSuccess: () -> Unit, onFailure: (exception: Exception) -> Unit) {
        try {
            auth.signOut()
            onSuccess()
        } catch (e: Exception) {
            onFailure(e)
        }
    }
}