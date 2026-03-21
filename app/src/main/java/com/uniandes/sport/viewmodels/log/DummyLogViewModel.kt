package com.uniandes.sport.viewmodels.log

import androidx.lifecycle.ViewModel

class DummyLogViewModel : ViewModel(), LogViewModelInterface {
    override fun log(screen: String, action: String, params: Map<String, String>) {
        println("Log - Screen: $screen, Action: $action, Params: $params")
    }

    override fun crash(screen: String, exception: Exception) {
        println("Simulating Crash in -> $screen because of: \n $exception")
    }

    override fun setUserProperty(key: String, value: String) {
        println("Simulating UserProperty in -> Key: $key, Value: $value")
    }
}