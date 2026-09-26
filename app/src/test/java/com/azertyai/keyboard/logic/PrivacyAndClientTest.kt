package com.azertyai.keyboard.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivacyReducerTest {
    @Test
    fun contextChoiceReadsLocallyAndDoesNotCallTheModel() {
        val start = AiPanelState(visible = true, preview = listOf("ancien"))
        val env = Env(contextEnabled = true, accessibilityOn = true)
        val (reading, effect) = reduce(start, Event.ChooseContext(10), env)

        assertTrue(effect is Effect.Capture)
        assertEquals(10, (effect as Effect.Capture).count)
        assertTrue(reading.capturing)
        assertTrue(reading.preview.isEmpty())

        val (preview, afterCapture) = reduce(reading, Event.Captured(listOf("Salut", "Tu viens ?")), env)
        assertNull(afterCapture)
        assertEquals(listOf("Salut", "Tu viens ?"), preview.preview)
        assertFalse(preview.busy)
        assertTrue(PanelCopy.body(preview).contains("Salut"))
        assertTrue(PanelCopy.status(preview)!!.contains("Rien n'a été envoyé"))
    }

    @Test
    fun contextStaysLocalWhenTheUserHasNotEnabledIt() {
        val env = Env(contextEnabled = false, accessibilityOn = true, field = FieldSnapshot("secret", "", null))
        val (_, disabled) = reduce(AiPanelState(visible = true), Event.ChooseContext(5), env)
        assertNull(disabled)

        val (_, noService) = reduce(
            AiPanelState(visible = true),
            Event.ChooseContext(30),
            Env(contextEnabled = true, accessibilityOn = false),
        )
        assertNull(noService)

        val (_, oddCount) = reduce(
            AiPanelState(visible = true),
            Event.ChooseContext(4),
            Env(contextEnabled = true, accessibilityOn = true),
        )
        assertNull(oddCount)
    }

    @Test
    fun confirmationIsTheOnlyPathThatSendsMessages() {
        val preview = AiPanelState(
            visible = true,
            screen = Screen.CONTEXT,
            preview = listOf("SECRET_THREAD"),
            prompt = "Sois bref",
        )
        val (busy, effect) = reduce(preview, Event.ConfirmPreview)
        val task = (effect as Effect.Complete).task as AiTask.Reply
        assertEquals(listOf("SECRET_THREAD"), task.messages)
        assertEquals("Sois bref", task.instruction)
        assertTrue(busy.busy)

        val asking = preview.copy(screen = Screen.HOME, prompt = "Quelle heure est-il ?")
        val (_, questionEffect) = reduce(asking, Event.SendPrompt)
        val question = (questionEffect as Effect.Complete).task as AiTask.Question
        assertFalse(userPrompt(question).contains("SECRET_THREAD"))
        assertFalse(userPrompt(question).contains("Messages,"))
    }

    @Test
    fun questionsNeverReceiveTheField() {
        val secret = "MOT_DE_PASSE_VISIBLE"
        val env = Env(canRead = false, field = FieldSnapshot(secret, "", null))
        val state = AiPanelState(visible = true, prompt = "Qui a écrit Les Misérables ?")
        val (_, effect) = reduce(state, Event.SendPrompt, env)
        val task = (effect as Effect.Complete).task as AiTask.Question
        assertFalse(userPrompt(task).contains(secret))
        assertEquals(ResultMode.ANSWER, (effect as Effect.Complete).mode)
    }

    @Test
    fun editsDoNotRunOnHiddenFields() {
        val env = Env(canRead = false, field = FieldSnapshot("secret", "", null))
        assertNull(reduce(AiPanelState(visible = true), Event.Professional, env).second)
        assertNull(reduce(AiPanelState(visible = true), Event.Correct, env).second)
        assertNull(reduce(AiPanelState(visible = true, prompt = "Corrige ce texte"), Event.SendPrompt, env).second)

        val empty = Env(canRead = true, field = FieldSnapshot("  ", "", null))
        val (blocked, effect) = reduce(AiPanelState(visible = true), Event.Shorten, empty)
        assertNull(effect)
        assertEquals(Note.EmptyField, blocked.note)
    }

    @Test
    fun answersCannotBeInserted() {
        val start = AiPanelState(visible = true, busy = true, requestId = 4, screen = Screen.RESULT)
        val (done, effect) = reduce(
            start,
            Event.Completed(4, "Victor Hugo.", ResultMode.ANSWER, canReplace = true),
        )
        assertNull(effect)
        assertFalse(done.canReplace)
        assertEquals(listOf(ActionId.COPY, ActionId.BACK), panelActions(done))
    }

    @Test
    fun rewriteOffersReplaceAndAStaleResultIsIgnored() {
        val field = Env(field = FieldSnapshot("bonjour", "", "bonjour"), canRead = true)
        val (_, effect) = reduce(AiPanelState(visible = true), Event.Professional, field)
        val running = effect as Effect.Complete
        assertTrue(running.task is AiTask.Rewrite)
        assertEquals(ResultMode.REPLACE, running.mode)

        val stale = AiPanelState(visible = true, busy = true, requestId = 3)
        val (kept, _) = reduce(stale, Event.Completed(2, "trop tard", ResultMode.REPLACE, true))
        assertTrue(kept.busy)
        assertNull(kept.result)
    }

    @Test
    fun closingDropsPreviewAndResult() {
        val (closed, effect) = reduce(
            AiPanelState(visible = true, preview = listOf("fil"), result = "réponse", prompt = "question"),
            Event.Close,
        )
        assertNull(effect)
        assertFalse(closed.visible)
        assertTrue(closed.preview.isEmpty())
        assertNull(closed.result)
        assertEquals("", closed.prompt)
    }
}

class AiClientTest {
    @Test
    fun geminiRequestIsStatelessAndKeepsTheKeyOutOfTheBody() {
        val http = FakeHttp(HttpResponse(200, """{"output_text":"Bonjour"}"""))
        val client = AiClient(http)
        val outcome = client.complete(config(Provider.GEMINI), AiTask.Question("Dis bonjour"), ResultMode.ANSWER)

        assertEquals("Bonjour", (outcome as AiOutcome.Ok).text)
        assertEquals("https://generativelanguage.googleapis.com/v1beta/interactions", http.last.url)
        assertEquals("secret-gemini", http.last.headers["x-goog-api-key"])
        assertTrue(http.last.body.contains("\"store\":false"))
        assertFalse(http.last.body.contains("secret-gemini"))
        assertTrue(http.last.body.contains("Dis bonjour"))
        assertFalse(http.last.body.contains("previous_interaction_id"))
    }

    @Test
    fun geminiRetriesWithoutThinkingAndReadsStepText() {
        val http = object : HttpTransport {
            val bodies = mutableListOf<String>()
            var calls = 0
            override fun post(request: HttpRequest): HttpResponse {
                calls++
                bodies += request.body
                return if (calls == 1) {
                    HttpResponse(400, """{"error":{"message":"thinking_level is not supported"}}""")
                } else {
                    HttpResponse(
                        200,
                        """{"steps":[{"type":"thought","content":[{"type":"text","text":"secret"}]},{"type":"model_output","content":[{"type":"text","text":"Prêt"}]}]}""",
                    )
                }
            }
        }
        val outcome = AiClient(http).complete(config(Provider.GEMINI), AiTask.Question("Prêt ?"), ResultMode.ANSWER)
        assertEquals("Prêt", (outcome as AiOutcome.Ok).text)
        assertEquals(2, http.calls)
        assertFalse(http.bodies[1].contains("thinking_level"))
    }

    @Test
    fun mistralKeyStaysInTheHeader() {
        val http = FakeHttp(
            HttpResponse(200, """{"choices":[{"message":{"content":"\"Merci\""}}]}"""),
        )
        val outcome = AiClient(http).complete(
            config(Provider.MISTRAL),
            AiTask.Rewrite("Corrige.", "merci"),
            ResultMode.REPLACE,
        )
        assertEquals("Merci", (outcome as AiOutcome.Ok).text)
        assertEquals("Bearer secret-mistral", http.last.headers["Authorization"])
        assertFalse(http.last.body.contains("secret-mistral"))
        assertTrue(http.last.body.contains("merci"))
    }

    @Test
    fun errorsDoNotEchoTheProviderBody() {
        val http = FakeHttp(HttpResponse(401, """{"error":"secret-gemini was rejected"}"""))
        val outcome = AiClient(http).complete(config(Provider.GEMINI), AiTask.Question("?"), ResultMode.ANSWER)
        val message = (outcome as AiOutcome.Err).message
        assertEquals("Clé API refusée.", message)
        assertFalse(message.contains("secret-gemini"))
    }

    private fun config(provider: Provider) = AiConfig(
        provider = provider,
        geminiKey = "secret-gemini",
        mistralKey = "secret-mistral",
    )
}

private class FakeHttp(private val response: HttpResponse) : HttpTransport {
    lateinit var last: HttpRequest
    override fun post(request: HttpRequest): HttpResponse {
        last = request
        return response
    }
}
