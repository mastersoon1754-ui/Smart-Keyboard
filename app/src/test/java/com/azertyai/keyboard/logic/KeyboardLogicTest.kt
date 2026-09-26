package com.azertyai.keyboard.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LayoutTest {
    @Test
    fun lettersFollowFrenchAzerty() {
        val rows = boardRows(BoardMode.LETTERS)
        assertEquals("azertyuiop", lettersOf(rows[0]))
        assertEquals("qsdfghjklm", lettersOf(rows[1]))
        assertEquals("wxcvbn'", lettersOf(rows[2]))
        assertEquals("é", alternates("e").first())
        assertTrue(alternates("c").contains("ç"))
    }

    @Test
    fun emailRowOffersAtAndSymbolsKeepFrenchQuotes() {
        val email = boardRows(BoardMode.LETTERS, email = true).last()
        assertTrue(email.any { it.text == "@" })
        val alt = boardRows(BoardMode.SYMBOLS_ALT).flatten()
        assertTrue(alt.any { it.text == "«" || it.text == "»" || alternates("'").contains("«") })
    }

    private fun lettersOf(row: List<SoftKey>): String =
        row.mapNotNull { key -> if (key.action == null) key.text else null }.joinToString("")
}

class FieldPolicyTest {
    @Test
    fun passwordsAreNotReadableAndChatsSend() {
        val password = policyFor(0x81, 0)
        assertFalse(password.canReadText)
        assertFalse(password.suggestions)

        val hidden = policyFor(0x1, 0x00100000)
        assertFalse(hidden.canReadText)

        val numberPassword = policyFor(0x12, 0)
        assertEquals(BoardMode.NUMBER, numberPassword.board)
        assertFalse(numberPassword.canReadText)

        val chat = policyFor(0x20001, 0x4)
        assertEquals(EnterBehavior.ACTION, chat.enter.behavior)
        assertEquals("Envoyer", chat.enter.label)

        val multiline = policyFor(0x20001, 0)
        assertEquals(EnterBehavior.NEWLINE, multiline.enter.behavior)

        val email = policyFor(0x21, 0)
        assertTrue(email.emailLike)
        assertEquals(BoardMode.PHONE, policyFor(0x3, 0).board)
    }
}

class TypingTest {
    @Test
    fun shiftLockAndAutoCap() {
        val shift = ShiftController()
        shift.sync("", autoCap = true)
        assertEquals(ShiftMode.ONCE, shift.mode)

        shift.onLetterCommitted("B", autoCap = true)
        assertEquals(ShiftMode.OFF, shift.mode)

        assertEquals(ShiftMode.ONCE, shift.onShiftTap(1_000))
        shift.sync("bonjour", autoCap = true)
        assertEquals(ShiftMode.ONCE, shift.mode)
        assertEquals(ShiftMode.LOCK, shift.onShiftTap(1_200))
        shift.onLetterCommitted("BONJOUR", autoCap = true)
        assertEquals(ShiftMode.LOCK, shift.mode)
        assertEquals(ShiftMode.OFF, shift.onShiftTap(2_000))

        val auto = ShiftController()
        auto.sync("", autoCap = true)
        assertEquals(ShiftMode.OFF, auto.onShiftTap(50))
        auto.sync("", autoCap = true)
        assertEquals(ShiftMode.OFF, auto.mode)
    }

    @Test
    fun doubleSpaceAndSuggestions() {
        assertTrue(shouldConvertDoubleSpace("Bonjour "))
        assertFalse(shouldConvertDoubleSpace("Bonjour"))
        assertFalse(shouldConvertDoubleSpace("Bonjour. "))
        assertEquals("Bon", currentPrefix("Je dis Bon"))
        assertEquals("", currentPrefix("Je dis "))
        assertEquals(3 to "Bonjour ", suggestionEdit("Bon", "Bonjour"))
        assertEquals("A", applyCase("a", ShiftMode.ONCE))
        assertEquals(".com", applyCase(".com", ShiftMode.ONCE))
        assertEquals("caf", dropLastCodePoint("café"))
        assertEquals("", dropLastCodePoint("👍"))
    }
}

class DictionaryTest {
    @Test
    fun suggestsWithoutExactEcho() {
        val dictionary = FrenchDictionary(listOf("bonjour", "bon", "bonne"))
        assertEquals(listOf("Bonjour", "Bonne"), dictionary.suggest("Bon"))
        assertEquals(listOf("BONJOUR", "BONNE"), dictionary.suggest("BON"))
        assertTrue(dictionary.suggest("bonjour").isEmpty())
    }

    @Test
    fun bundledLexiconIsClean() {
        val file = listOf(File("src/main/assets/fr_words.txt"), File("app/src/main/assets/fr_words.txt"))
            .first { it.exists() }
        val lines = file.readLines().map { it.trim() }.filter { it.isNotEmpty() }
        assertTrue(lines.size >= 400)
        assertEquals(lines.size, lines.distinct().size)
        assertTrue(lines.all { it == it.lowercase() && !it.any { char -> char.isWhitespace() } })
        assertTrue(lines.contains("bonjour"))
        assertTrue(lines.contains("merci"))
    }
}

class ExtractorTest {
    @Test
    fun keepsRecentMessagesAndDropsChromeDraftAndDuplicates() {
        val parent = RawText("Bonjour, tu viens ?", 10, 90, 0, 400, false)
        val childA = RawText("Bonjour,", 16, 40, 12, 180, false)
        val childB = RawText("tu viens ?", 48, 80, 12, 200, false)
        val time = RawText("12:30", 8, 28, 320, 390, false)
        val draft = RawText("mon brouillon", 400, 460, 0, 400, true)
        val older = RawText("À tout à l'heure", 200, 240, 20, 300, false)
        val latest = RawText("Oui", 260, 300, 20, 80, false)

        val messages = extractMessages(
            listOf(parent, childA, childB, time, draft, older, latest, RawText("en ligne", 0, 20, 0, 80, false)),
            limit = 2,
            draft = "mon brouillon",
        )
        assertEquals(listOf("À tout à l'heure", "Oui"), messages)
    }

    @Test
    fun mergesWrappedLines() {
        val nodes = listOf(
            RawText("Bonjour,", 10, 30, 20, 160, false),
            RawText("ça va ?", 34, 54, 20, 150, false),
            RawText("Oui", 140, 164, 20, 70, false),
        )
        assertEquals(listOf("Bonjour, ça va ?", "Oui"), extractMessages(nodes, 10))
    }
}

class JsonTest {
    @Test
    fun escapesAndReadsNestedContent() {
        assertEquals("\"a\\nb\\\"c\"", jsonString("a\nb\"c"))
        val parsed = parseJson("""{"choices":[{"message":{"content":"Bonjour"}}]}""")
        assertEquals("Bonjour", extractMistralText(parsed))
        val gemini = parseJson("""{"output_text":"éà"}""")
        assertEquals("éà", extractGeminiText(gemini))
    }
}

class PromptTest {
    @Test
    fun freeTextDefaultsToAnAnswer() {
        assertEquals(FreeIntent.QUESTION, classifyFreeText("Comment traduire cette idée plus tard ?"))
        assertEquals(FreeIntent.EDIT, classifyFreeText("Traduis en anglais"))
        assertEquals(FreeIntent.EDIT, classifyFreeText("Rends ce texte plus professionnel"))
        val prompt = userPrompt(AiTask.Question("Pourquoi le ciel est bleu ?"))
        assertFalse(prompt.contains("Texte :"))
    }
}
