package com.azertyai.keyboard.logic

private const val GEMINI_URL = "https://generativelanguage.googleapis.com/v1beta/interactions"
private const val MISTRAL_URL = "https://api.mistral.ai/v1/chat/completions"

class AiClient(private val http: HttpTransport) {
    fun complete(config: AiConfig, task: AiTask, mode: ResultMode): AiOutcome {
        val key = when (config.provider) {
            Provider.GEMINI -> config.geminiKey.trim()
            Provider.MISTRAL -> config.mistralKey.trim()
        }
        if (key.isEmpty()) return AiOutcome.Err("Ajoutez une clé API dans l'application.")
        return when (config.provider) {
            Provider.GEMINI -> gemini(config, key, task, mode)
            Provider.MISTRAL -> interpret(mistral(config, key, task), Provider.MISTRAL, mode)
        }
    }

    private fun gemini(config: AiConfig, key: String, task: AiTask, mode: ResultMode): AiOutcome {
        val first = geminiCall(config, key, task, withThinking = true)
        if (first.code == 400 && first.body.contains("thinking", ignoreCase = true)) {
            return interpret(geminiCall(config, key, task, withThinking = false), Provider.GEMINI, mode)
        }
        return interpret(first, Provider.GEMINI, mode)
    }

    private fun geminiCall(config: AiConfig, key: String, task: AiTask, withThinking: Boolean): HttpResponse {
        val payload = linkedMapOf<String, Any?>(
            "model" to sanitizeModel(config.geminiModel, DEFAULT_GEMINI_MODEL),
            "input" to userPrompt(task),
            "system_instruction" to systemPrompt(),
            "store" to false,
        )
        if (withThinking) {
            payload["generation_config"] = mapOf("thinking_level" to "low")
        }
        return http.post(
            HttpRequest(
                url = GEMINI_URL,
                headers = mapOf(
                    "Content-Type" to "application/json; charset=utf-8",
                    "x-goog-api-key" to key,
                ),
                body = toJson(payload),
            ),
        )
    }

    private fun mistral(config: AiConfig, key: String, task: AiTask): HttpResponse {
        val payload = mapOf(
            "model" to sanitizeModel(config.mistralModel, DEFAULT_MISTRAL_MODEL),
            "temperature" to 0.4,
            "max_tokens" to 800,
            "messages" to listOf(
                mapOf("role" to "system", "content" to systemPrompt()),
                mapOf("role" to "user", "content" to userPrompt(task)),
            ),
        )
        return http.post(
            HttpRequest(
                url = MISTRAL_URL,
                headers = mapOf(
                    "Content-Type" to "application/json; charset=utf-8",
                    "Authorization" to "Bearer $key",
                ),
                body = toJson(payload),
            ),
        )
    }

    private fun interpret(response: HttpResponse, provider: Provider, mode: ResultMode): AiOutcome {
        if (response.code == 0) return AiOutcome.Err("Connexion impossible.")
        if (response.code !in 200..299) return AiOutcome.Err(messageForHttp(response.code))
        val text = try {
            val root = parseJson(response.body)
            when (provider) {
                Provider.GEMINI -> extractGeminiText(root)
                Provider.MISTRAL -> extractMistralText(root)
            }
        } catch (_: IllegalArgumentException) {
            null
        }
        val cleaned = text?.let { cleanGenerated(it, mode) }.orEmpty()
        if (cleaned.isBlank()) return AiOutcome.Err("Réponse vide.")
        return AiOutcome.Ok(cleaned)
    }
}

fun messageForHttp(code: Int): String = when (code) {
    401, 403 -> "Clé API refusée."
    429 -> "Trop de requêtes. Réessayez dans un instant."
    400 -> "La requête a été refusée."
    in 500..599 -> "Le service est momentanément indisponible."
    else -> "Le service a répondu avec une erreur."
}

fun extractGeminiText(root: JsonValue): String? {
    when (val direct = root["output_text"]) {
        is JsonValue.Str -> if (direct.value.isNotBlank()) return direct.value
        is JsonValue.Arr -> {
            val joined = direct.list.mapNotNull { it.asString() }.joinToString("")
            if (joined.isNotBlank()) return joined
        }
        else -> Unit
    }
    val steps = root["steps"]?.asArray() ?: return null
    val parts = mutableListOf<String>()
    for (step in steps) {
        val type = step["type"]?.asString().orEmpty()
        if (type.contains("thought", ignoreCase = true)) continue
        collectTextBlocks(step, parts)
    }
    return parts.joinToString("").trim().ifBlank { null }
}

private fun collectTextBlocks(value: JsonValue, out: MutableList<String>) {
    when (value) {
        is JsonValue.Obj -> {
            val type = value["type"]?.asString()
            if (type != null && type.contains("thought", ignoreCase = true)) return
            if (type == "text") {
                value["text"]?.asString()?.let { if (it.isNotEmpty()) out += it }
            }
            value.map.values.forEach { collectTextBlocks(it, out) }
        }
        is JsonValue.Arr -> value.list.forEach { collectTextBlocks(it, out) }
        else -> Unit
    }
}

fun extractMistralText(root: JsonValue): String? {
    val message = root["choices"]?.asArray()?.firstOrNull()?.get("message") ?: return null
    return when (val content = message["content"]) {
        is JsonValue.Str -> content.value
        is JsonValue.Arr -> content.list.mapNotNull { block ->
            block.asString() ?: block["text"]?.asString()
        }.joinToString("")
        else -> null
    }
}
