package com.uniandes.sport.viewmodels.auth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.uniandes.sport.models.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.OAuthProvider
import android.app.Activity
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import java.text.Normalizer

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
        if (fullName.isBlank() || email.isBlank() || password.isBlank()) {
            onFailure(IllegalArgumentException("Full Name, Email and password are required."))
            return
        }

        // We NO LONGER create the Firebase Auth account here.
        // We just validate the fields and store them in memory.
        // The account will be created ONLY when saveOnboardingData is called at the end of onboarding.
        val skeletonUser = User(
            email = email,
            fullName = fullName
        )
        onSuccess(skeletonUser)
    }

    override fun login(
        onSuccess: (result: User, isNewUser: Boolean) -> Unit,
        onFailure: (exception: Exception) -> Unit
    ) {
        if (email.isBlank() || password.isBlank()) {
            onFailure(IllegalArgumentException("Email and password are required."))
            return
        }

        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val uid = auth.currentUser?.uid ?: ""
                    db.collection("users").document(uid).get()
                        .addOnSuccessListener { document ->
                            if (document != null && document.exists()) {
                                val userProfile = document.toObject(User::class.java)
                                if (userProfile != null) {
                                    email = userProfile.email
                                    fullName = userProfile.fullName
                                    val isNew = userProfile.program.isBlank() || userProfile.mainSport.isBlank()
                                    onSuccess(userProfile, isNew)
                                    return@addOnSuccessListener
                                }
                            }
                            // Fallback if no extended profile exists -> They need onboarding
                            onSuccess(User(uid = uid, email = email), true)
                        }
                        .addOnFailureListener {
                            // If we can't check, play it safe and assume they might need onboarding
                            onSuccess(User(uid = uid, email = email), true)
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
                                email = userProfile.email
                                fullName = userProfile.fullName
                                val isNew = userProfile.program.isBlank() || userProfile.mainSport.isBlank()
                                onSuccess(userProfile, isNew)
                                return@addOnSuccessListener
                            }
                        }

                        // For new Google users, we set the skeleton data in the ViewModel state
                        // but DO NOT create the Firestore document yet.
                        email = fallbackUser.email
                        fullName = fallbackUser.fullName
                        
                        onSuccess(fallbackUser, true)
                    }
                    .addOnFailureListener {
                        onSuccess(fallbackUser, true)
                    }
            }
    }

    override fun loginWithMicrosoft(
        activity: Activity,
        onSuccess: (result: User, isNewUser: Boolean) -> Unit,
        onFailure: (exception: Exception) -> Unit
    ) {
        val provider = OAuthProvider.newBuilder("microsoft.com")
        
        // This forces the Microsoft login page to ask for the account every time
        provider.addCustomParameter("prompt", "select_account")
        
        // Restricting specifically to organizations (optional but common for Uniandes)
        // provider.addCustomParameter("tenant", "organizations")

        auth.startActivityForSignInWithProvider(activity, provider.build())
            .addOnSuccessListener { authResult ->
                val firebaseUser = auth.currentUser
                if (firebaseUser == null) {
                    onFailure(Exception("Authentication failed: No user found."))
                    return@addOnSuccessListener
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
                                email = userProfile.email
                                fullName = userProfile.fullName
                                val isNew = userProfile.program.isBlank() || userProfile.mainSport.isBlank()
                                onSuccess(userProfile, isNew)
                                return@addOnSuccessListener
                            }
                        }
                        email = fallbackUser.email
                        fullName = fallbackUser.fullName
                        onSuccess(fallbackUser, true)
                    }
                    .addOnFailureListener {
                        onSuccess(fallbackUser, true)
                    }
            }
            .addOnFailureListener { e ->
                onFailure(e)
            }
    }
    override fun saveOnboardingData(onSuccess: () -> Unit, onFailure: (exception: Exception) -> Unit) {
        viewModelScope.launch {
            try {
                // Determine if we need to create the Auth account first
                var currentUser = auth.currentUser
                if (currentUser == null) {
                    // This is a new Email/Password user who just finished onboarding
                    if (email.isBlank() || password.isBlank()) {
                        onFailure(Exception("Email and password are missing for registration."))
                        return@launch
                    }
                    val authResult = auth.createUserWithEmailAndPassword(email, password).await()
                    currentUser = authResult.user
                }

                if (currentUser == null) {
                    onFailure(Exception("Could not authenticate user."))
                    return@launch
                }

                val uid = currentUser.uid
                val fullProfile = User(
                    uid = uid,
                    email = currentUser.email ?: email,
                    fullName = if (fullName.isNotBlank()) fullName else (currentUser.displayName ?: ""),
                    program = program,
                    semester = semester.toIntOrNull() ?: 0,
                    mainSport = mainSport,
                    role = "athlete",
                    createdAt = System.currentTimeMillis()
                )

                // Now create the Firestore document
                db.collection("users").document(uid)
                    .set(fullProfile)
                    .await()

                onSuccess()
            } catch (e: Exception) {
                onFailure(e)
            }
        }
    }

    override fun isUserLoggedIn(
        onSuccess: (isLoggedIn: Boolean, isNewUser: Boolean) -> Unit,
        onFailure: (exception: Exception) -> Unit
    ) {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            db.collection("users").document(currentUser.uid).get()
                .addOnSuccessListener { document ->
                    if (document != null && document.exists()) {
                        val userProfile = document.toObject(User::class.java)
                        if (userProfile != null) {
                            email = userProfile.email
                            fullName = userProfile.fullName
                            val isNew = userProfile.program.isBlank() || userProfile.mainSport.isBlank()
                            onSuccess(true, isNew)
                            return@addOnSuccessListener
                        }
                    }
                    // Document does NOT exist -> User is new to the USPORTS platform
                    onSuccess(true, true)
                }
                .addOnFailureListener {
                    onSuccess(true, true) // if error, assume they need onboarding to be safe
                }
        } else {
            onSuccess(false, false)
        }
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
                            _email = userProfile.email
                            _fullName = userProfile.fullName
                            _program = userProfile.program
                            _semester = userProfile.semester.toString()
                            _mainSport = userProfile.mainSport
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

    fun updateMainSports(
        newMainSportsCsv: String,
        onSuccess: () -> Unit,
        onFailure: (exception: Exception) -> Unit
    ) {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            onFailure(Exception("User is not logged in."))
            return
        }

        val normalizedSports = newMainSportsCsv
            .split(",", ";", "|")
            .map { normalizeSportId(it) }
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString(",")

        if (normalizedSports.isBlank()) {
            onFailure(IllegalArgumentException("Select at least one main sport."))
            return
        }

        db.collection("users").document(currentUser.uid)
            .update("mainSport", normalizedSports)
            .addOnSuccessListener {
                _mainSport = normalizedSports
                onSuccess()
            }
            .addOnFailureListener { onFailure(it) }
    }

    private fun normalizeSportId(value: String): String {
        val withoutAccents = Normalizer.normalize(value, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
        val clean = withoutAccents.trim().lowercase()

        return when (clean) {
            "soccer", "football", "futbol" -> "soccer"
            "basket", "basketball", "baloncesto" -> "basketball"
            "tenis", "tennis" -> "tennis"
            "calistenia", "calisthenics", "calistennics" -> "calisthenics"
            "running", "correr" -> "running"
            else -> clean
        }
    }
}