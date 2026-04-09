package com.uniandes.sport.viewmodels.log

import android.util.Log
import androidx.lifecycle.ViewModel
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.analytics.ktx.logEvent
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase

/**
 * Primera versión del Analytics Engine configurada para responder 
 * a las Business Questions (BQ) Tipo 1:
 * - Daily Active Users (DAU) by Sport
 * - Daily Event Registration Volume
 */
class FirebaseLogViewModel(): ViewModel(), LogViewModelInterface {

    private val firebaseAnalytics = Firebase.analytics
    private val firebaseCrashlytics = Firebase.crashlytics

    override fun log(screen: String, action: String, params: Map<String, String>) {
        Log.d("TELEMETRY_LOG", "Logging Event: $action | Screen: $screen | Params: $params")
        firebaseAnalytics.logEvent(action){
            param(FirebaseAnalytics.Param.SCREEN_NAME, screen)
            params.forEach { (key, value) ->
                param(key, value)
            }
        }
    }

    override fun crash(screen: String, exception: Exception) {
        firebaseCrashlytics.setCustomKey("Screen", screen)
        firebaseCrashlytics.recordException(exception)
    }

    override fun setUserProperty(key: String, value: String) {
        firebaseAnalytics.setUserProperty(key, value)
    }
}