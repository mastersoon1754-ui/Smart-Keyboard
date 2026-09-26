package com.azertyai.keyboard.logic

data class RawText(
    val text: String,
    val top: Int,
    val bottom: Int,
    val left: Int,
    val right: Int,
    val editable: Boolean,
)

private data class Line(
    val text: String,
    val top: Int,
    val bottom: Int,
    val left: Int,
    val right: Int,
)

fun extractMessages(nodes: List<RawText>, limit: Int, draft: String? = null): List<String> {
    require(limit in 1..30)
    val visible = nodes.filter { !it.editable && it.text.isNotBlank() && it.bottom > it.top }
    val deduped = dropNestedDuplicates(visible)
    val lines = groupLines(deduped)
    val bubbles = mergeBubbles(lines)
    val cleaned = bubbles
        .map { it.replace(Regex("\\s+"), " ").trim() }
        .filter { it.isNotEmpty() && !isChrome(it) }
    val draftNorm = draft?.trim()?.takeIf { it.length >= 2 }
    val withoutDraft = if (draftNorm == null) {
        cleaned
    } else {
        cleaned.filterNot { it.equals(draftNorm, ignoreCase = true) }
    }
    return withoutDraft.takeLast(limit).map { it.take(MESSAGE_CAP) }
}

fun isChrome(text: String): Boolean {
    val value = text.trim()
    if (value.isEmpty()) return true
    if (CLOCK.matches(value) || NUMERIC_DATE.matches(value) || RELATIVE_TIME.matches(value)) return true
    return value.lowercase() in CHROME
}

private fun dropNestedDuplicates(nodes: List<RawText>): List<RawText> {
    val sorted = nodes.sortedByDescending { (it.right - it.left).coerceAtLeast(0) * (it.bottom - it.top) }
    val kept = mutableListOf<RawText>()
    for (node in sorted) {
        val covered = kept.any { parent ->
            boundsContain(parent, node) && parent.text.contains(node.text.trim())
        }
        if (!covered) kept += node
    }
    return kept
}

private fun boundsContain(parent: RawText, child: RawText): Boolean {
    return child.left >= parent.left - 4 &&
        child.right <= parent.right + 4 &&
        child.top >= parent.top - 4 &&
        child.bottom <= parent.bottom + 4 &&
        parent !== child
}

private fun groupLines(nodes: List<RawText>): List<Line> {
    val sorted = nodes.sortedWith(compareBy({ it.top }, { it.left }))
    val groups = mutableListOf<MutableList<RawText>>()
    for (node in sorted) {
        val current = groups.lastOrNull()
        val anchor = current?.firstOrNull()
        if (anchor != null && kotlin.math.abs(centerY(anchor) - centerY(node)) <= 18) {
            current += node
        } else {
            groups += mutableListOf(node)
        }
    }
    return groups.map { group ->
        val ordered = group.sortedBy { it.left }
        Line(
            text = ordered.joinToString(" ") { it.text.trim() },
            top = ordered.minOf { it.top },
            bottom = ordered.maxOf { it.bottom },
            left = ordered.minOf { it.left },
            right = ordered.maxOf { it.right },
        )
    }
}

private fun mergeBubbles(lines: List<Line>): List<String> {
    if (lines.isEmpty()) return emptyList()
    val bubbles = mutableListOf<String>()
    var buffer = lines.first().text.trim()
    var previous = lines.first()
    for (line in lines.drop(1)) {
        val gap = line.top - previous.bottom
        val overlaps = line.left <= previous.right && line.right >= previous.left
        if (gap in 0..18 && overlaps) {
            buffer = buffer.trimEnd() + " " + line.text.trim()
        } else {
            bubbles += buffer
            buffer = line.text.trim()
        }
        previous = line
    }
    bubbles += buffer
    return bubbles
}

private fun centerY(node: RawText): Int = (node.top + node.bottom) / 2

private const val MESSAGE_CAP = 1000

private val CLOCK = Regex("""^\d{1,2}:\d{2}(\s?[AP]M)?$""", RegexOption.IGNORE_CASE)
private val NUMERIC_DATE = Regex("""^\d{1,2}[/.-]\d{1,2}([/.-]\d{2,4})?$""")
private val RELATIVE_TIME = Regex("""(?i)^(il y a\b|today\b|yesterday\b|aujourd'hui\b|hier\b).{0,24}$""")

private val CHROME = setOf(
    "en ligne", "online", "vu", "seen", "delivered", "envoyé", "sent",
    "aujourd'hui", "hier", "today", "yesterday",
    "en train d'écrire", "en train d'écrire…", "typing…", "typing...", "typing",
    "message", "messages", "envoyer", "send", "photo", "vidéo", "video",
    "connexion", "sms", "messenger",
)
