package com.marklvii.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

data class ChatMessage(val role: String, val text: String, val imageBase64: String? = null)

object Ai {
    private val client = OkHttpClient()

    suspend fun chat(
        baseUrl: String,
        key: String,
        model: String,
        history: List<ChatMessage>,
        system: String
    ): String = withContext(Dispatchers.IO) {
        val messages = JSONArray()
        messages.put(JSONObject().put("role", "system").put("content", system))
        for (m in history) {
            val content: Any = if (m.imageBase64 != null) {
                val parts = JSONArray()
                if (m.text.isNotBlank()) {
                    parts.put(JSONObject().put("type", "text").put("text", m.text))
                }
                parts.put(
                    JSONObject().put("type", "image_url").put(
                        "image_url", JSONObject().put("url", "data:image/jpeg;base64,${m.imageBase64}")
                    )
                )
                parts
            } else {
                m.text
            }
            messages.put(JSONObject().put("role", m.role).put("content", content))
        }
        val payload = JSONObject()
            .put("model", model)
            .put("messages", messages)
            .put("max_tokens", 1024)
        val body = payload.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(baseUrl)
            .addHeader("Authorization", "Bearer $key")
            .addHeader("HTTP-Referer", "https://github.com/khalil203/Mark-LVII")
            .addHeader("X-Title", "Mark LVII Android")
            .post(body)
            .build()
        client.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw Exception("HTTP ${response.code}: ${raw.take(240)}")
            }
            val obj = JSONObject(raw)
            val content = obj.getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .get("content")
            when (content) {
                is String -> content
                is JSONArray -> (0 until content.length()).joinToString("\n") { i ->
                    val part = content.getJSONObject(i)
                    if (part.optString("type") == "text") part.optString("text") else ""
                }.trim()
                else -> ""
            }
        }
    }
}
