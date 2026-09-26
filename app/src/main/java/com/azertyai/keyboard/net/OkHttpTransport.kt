package com.azertyai.keyboard.net

import com.azertyai.keyboard.logic.HttpRequest
import com.azertyai.keyboard.logic.HttpResponse
import com.azertyai.keyboard.logic.HttpTransport
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class OkHttpTransport : HttpTransport {
    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .build()

    override fun post(request: HttpRequest): HttpResponse {
        val body = request.body.toRequestBody(JSON)
        val builder = Request.Builder().url(request.url).post(body)
        request.headers.forEach { (name, value) -> builder.header(name, value) }
        return try {
            client.newCall(builder.build()).execute().use { response ->
                HttpResponse(response.code, response.body?.string().orEmpty())
            }
        } catch (_: IOException) {
            HttpResponse(0, "")
        }
    }

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
