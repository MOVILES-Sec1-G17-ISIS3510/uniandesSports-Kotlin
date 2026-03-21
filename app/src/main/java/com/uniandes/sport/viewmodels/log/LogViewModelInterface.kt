package com.uniandes.sport.viewmodels.log

import android.graphics.Bitmap

interface LogViewModelInterface {
    fun log(screen: String, action: String, params: Map<String, String> = emptyMap())
    fun setUserProperty(key: String, value: String)
    fun crash(screen: String, exception: Exception)
}