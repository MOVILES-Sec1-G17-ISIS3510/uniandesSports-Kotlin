package com.uniandes.sport.viewmodels.auth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.uniandes.sport.models.User
import kotlinx.coroutines.*

class DummyAuthViewModel : AuthViewModelInterface, ViewModel() {
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

    override fun register(onSuccess: (result: User) -> Unit, onFailure: (exception: Exception) -> Unit) {
        viewModelScope.launch {
            val result = async {
                if (email.isNotEmpty() && password.isNotEmpty()) {
                    User(uid = "123", email = email, fullName = fullName, program = program, mainSport = mainSport)
                } else {
                    throw Exception("Email or password is empty.")
                }
            }
            try {
                onSuccess(result.await())
            } catch (e: Exception) {
                onFailure(e)
            }
        }
    }

    override fun login(onSuccess: (result: User) -> Unit, onFailure: (exception: Exception) -> Unit) {
        register(onSuccess, onFailure)
    }

    override fun loginWithGoogleIdToken(
        idToken: String,
        onSuccess: (result: User) -> Unit,
        onFailure: (exception: Exception) -> Unit
    ) {
        viewModelScope.launch {
            if (idToken.isBlank()) {
                onFailure(Exception("Google ID token is empty."))
                return@launch
            }

            onSuccess(
                User(
                    uid = "google-123",
                    email = if (email.isNotBlank()) email else "google.user@example.com",
                    fullName = if (fullName.isNotBlank()) fullName else "Google User"
                )
            )
        }
    }

    override fun isUserLoggedIn(onSuccess: (isLoggedIn: Boolean) -> Unit, onFailure: (exception: Exception) -> Unit) {
        onSuccess(false)
    }

    override fun recoverPassword(onSuccess: () -> Unit, onFailure: (exception: Exception) -> Unit) {
        viewModelScope.launch {
            try {
                onSuccess()
            } catch (e: Exception) {
                onFailure(e)
            }
        }
    }

    override fun getUser(onSuccess: (result: User) -> Unit, onFailure: (exception: Exception) -> Unit) {
        viewModelScope.launch {
            val result = async {
                User(uid = "123", email = email)
            }
            try {
                onSuccess(result.await())
            } catch (e: Exception) {
                onFailure(e)
            }
        }
    }

    override fun logout(onSuccess: () -> Unit, onFailure: (exception: Exception) -> Unit) {
        onSuccess()
    }
}