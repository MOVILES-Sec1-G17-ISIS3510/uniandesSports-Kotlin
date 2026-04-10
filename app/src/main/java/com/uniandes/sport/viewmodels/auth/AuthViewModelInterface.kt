package com.uniandes.sport.viewmodels.auth

import android.app.Activity
import com.uniandes.sport.models.User

interface AuthViewModelInterface {
    var email: String
    var password: String
    var fullName: String
    var program: String
    var semester: String
    var mainSport: String

    fun register(onSuccess: (result: User) -> Unit, onFailure: (exception: Exception) -> Unit)
    fun login(onSuccess: (result: User, isNewUser: Boolean) -> Unit, onFailure: (exception: Exception) -> Unit)
    fun loginWithGoogleIdToken(idToken: String, onSuccess: (result: User, isNewUser: Boolean) -> Unit, onFailure: (exception: Exception) -> Unit)
    fun loginWithMicrosoft(activity: Activity, onSuccess: (result: User, isNewUser: Boolean) -> Unit, onFailure: (exception: Exception) -> Unit)
    fun isUserLoggedIn(onSuccess: (isLoggedIn: Boolean, isNewUser: Boolean) -> Unit, onFailure: (exception: Exception) -> Unit)
    fun recoverPassword(onSuccess: () -> Unit, onFailure: (exception: Exception) -> Unit)
    fun getUser(onSuccess: (result: User) -> Unit, onFailure: (exception: Exception) -> Unit)
    fun logout(onSuccess: () -> Unit, onFailure: (exception: Exception) -> Unit)
    fun saveOnboardingData(onSuccess: () -> Unit, onFailure: (exception: Exception) -> Unit)
}