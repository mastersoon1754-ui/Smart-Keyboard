package com.azertyai.keyboard.logic

enum class Provider { GEMINI, MISTRAL }

const val DEFAULT_GEMINI_MODEL = "gemini-3.8-flash"
const val DEFAULT_MISTRAL_MODEL = "mistral-small-latest"

data class AiConfig(
    val provider: Provider,
    val geminiKey: String,
    val mistralKey: String,
    val geminiModel: String = DEFAULT_GEMINI_MODEL,
    val mistralModel: String = DEFAULT_MISTRAL_MODEL,
)

data class FieldSnapshot(
    val before: String,
    val after: String,
    val selected: String?,
) {
    val sourceForEdit: String
        get() = selected?.takeIf { it.isNotBlank() } ?: (before + after)
}

data class HttpRequest(
    val url: String,
    val headers: Map<String, String>,
    val body: String,
)

data class HttpResponse(
    val code: Int,
    val body: String,
)

fun interface HttpTransport {
    fun post(request: HttpRequest): HttpResponse
}

sealed interface AiOutcome {
    data class Ok(val text: String) : AiOutcome
    data class Err(val message: String) : AiOutcome
}

fun sanitizeModel(value: String, fallback: String): String {
    val trimmed = value.trim()
    return if (MODEL_ID.matches(trimmed)) trimmed else fallback
}

private val MODEL_ID = Regex("^[A-Za-z0-9._-]{1,80}$")
