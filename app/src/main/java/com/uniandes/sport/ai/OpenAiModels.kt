package com.uniandes.sport.ai

import com.google.gson.annotations.SerializedName

data class OpenAiRequest(
    val model: String = "gpt-4o-mini",
    val messages: List<OpenAiMessage>,
    @SerializedName("max_tokens") val maxTokens: Int? = null,
    @SerializedName("response_format") val responseFormat: OpenAiResponseFormat? = null
)

data class OpenAiMessage(
    val role: String,
    val content: Any // Can be String or List<OpenAiContent>
)

data class OpenAiContent(
    val type: String, // "text" or "image_url"
    val text: String? = null,
    @SerializedName("image_url") val imageUrl: OpenAiImageUrl? = null
)

data class OpenAiImageUrl(
    val url: String, // "data:image/jpeg;base64,..."
    val detail: String? = null
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
