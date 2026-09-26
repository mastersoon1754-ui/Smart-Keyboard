package com.azertyai.keyboard.logic

import java.util.Locale

class FrenchDictionary(words: List<String>) {
    private val words: List<String> = words
        .asSequence()
        .map { it.trim().lowercase(Locale.FRENCH) }
        .filter { it.isNotEmpty() }
        .distinct()
        .toList()

    fun suggest(prefix: String, limit: Int = 3): List<String> {
        if (prefix.isBlank() || limit <= 0) return emptyList()
        val needle = prefix.lowercase(Locale.FRENCH)
        val matches = ArrayList<String>(limit)
        for (word in words) {
            if (word.startsWith(needle) && !word.equals(needle, ignoreCase = true)) {
                matches += applyWordCase(prefix, word)
                if (matches.size == limit) break
            }
        }
        return matches
    }
}

fun loadDictionary(text: String): FrenchDictionary {
    val words = text.lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") }
        .toList()
    return FrenchDictionary(words)
}

fun applyWordCase(prefix: String, word: String): String {
    val letters = prefix.filter { it.isLetter() }
    if (letters.isNotEmpty() && letters.all { it.isUpperCase() }) {
        return word.uppercase(Locale.FRENCH)
    }
    if (prefix.firstOrNull()?.isUpperCase() == true) {
        return word.replaceFirstChar { char ->
            if (char.isLowerCase()) char.titlecase(Locale.FRENCH) else char.toString()
        }
    }
    return word
}
