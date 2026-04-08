package com.uniandes.sport.ai

import com.google.gson.annotations.SerializedName

data class OpenAiRequest(
    val model: String = "gpt-4o-mini",
    val messages: List<OpenAiMessage>,
    @SerializedName("response_format") val responseFormat: OpenAiResponseFormat = OpenAiResponseFormat(type = "json_object")
)

data class OpenAiMessage(
    val role: String,
    val content: String
)

data class OpenAiResponseFormat(
    val type: String
)

data class OpenAiResponse(
    val choices: List<OpenAiChoice>? = null,
    val error: OpenAiError? = null
)

data class OpenAiChoice(
    val message: OpenAiMessage? = null
)

data class OpenAiError(
    val message: String? = null
)
