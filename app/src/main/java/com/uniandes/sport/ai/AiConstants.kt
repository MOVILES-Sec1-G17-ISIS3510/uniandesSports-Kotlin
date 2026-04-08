package com.uniandes.sport.ai

import com.uniandes.sport.BuildConfig

object AiConstants {
    // LLM API Key ahora se lee de local.properties
    val OPENAI_API_KEY = BuildConfig.OPENAI_API_KEY
    const val OPENAI_BASE_URL = "https://api.openai.com/"

    val GEMINI_API_KEY = BuildConfig.GEMINI_API_KEY
    const val GEMINI_BASE_URL = "https://generativelanguage.googleapis.com/"
}
