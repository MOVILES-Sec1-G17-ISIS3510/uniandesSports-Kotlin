package com.uniandes.sport.viewmodels.log

import androidx.lifecycle.ViewModel

class DummyLogViewModel : ViewModel(), LogViewModelInterface {
    override fun log(screen: String, action: String, params: Map<String, String>) {
        println("Log - Screen: $screen, Action: $action, Params: $params")
    }

    override fun crash(screen: String, exception: Exception) {
        println("Crash - Screen: $screen, Exception: ${exception.message}")
    }
}