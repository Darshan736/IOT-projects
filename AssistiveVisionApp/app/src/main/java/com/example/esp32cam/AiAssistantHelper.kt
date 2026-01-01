package com.example.esp32cam

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/**
 * Isolated helper to handle Optional AI (LLM) Guidance using GROQ API for ultra-fast response.
 * This keeps LLM networking code AWAY from the core MainActivity detection loop.
 */
class AiAssistantHelper {

    private val client = OkHttpClient()
    
    // REPLACE with your actual Groq API Key
    private val API_KEY = "YOUR_GROQ_API_KEY" 
    private val API_URL = "https://api.groq.com/openai/v1/chat/completions"

    /**
     * Converts raw detection labels into a natural sentence via Groq (LLaMA 3).
     */
    suspend fun getNaturalGuidance(detectionsSummary: String, userQuestion: String? = null): String? {
        // Correct check: Only stop if the key remains the default placeholder
        if (API_KEY == "YOUR_GROQ_API_KEY" || API_KEY.length < 10) {
            return "Note: Please add your Groq API Key in AiAssistantHelper.kt."
        }

        return withContext(Dispatchers.IO) {
            try {
                val systemPrompt = """
                    SYSTEM IDENTITY: You are an assistive AI providing natural-language guidance for a visually impaired user.
                    
                    SCENE DATA STRUCTURE:
                    - Objects: [List of {type, pos, dist}]
                    - Scene status: Overall environment state
                    - User context: What the user is doing or asking
                    
                    TASK:
                    1. Summarize the scene based ONLY on provided data.
                    2. Prioritize people and obstacles for safety.
                    3. Use calm, clear, human-friendly language. No technical jargon.
                    4. Max 1-2 sentences.
                    
                    RULES:
                    - NEVER override navigation safety.
                    - NEVER hallucinate objects.
                    - If a question is asked, answer ONLY using the scene data.
                """.trimIndent()

                val userInput = if (userQuestion != null) {
                    "USER QUESTION: $userQuestion | SCENE DATA: $detectionsSummary"
                } else {
                    "SCENE DATA: $detectionsSummary"
                }

                val jsonRequest = JSONObject().apply {
                    put("model", "llama-3.1-8b-instant")
                    put("messages", JSONArray().apply {
                        put(JSONObject().apply {
                            put("role", "system")
                            put("content", systemPrompt)
                        })
                        put(JSONObject().apply {
                            put("role", "user")
                            put("content", userInput)
                        })
                    })
                    put("max_tokens", 80)
                }

                val body = jsonRequest.toString().toRequestBody("application/json".toMediaType())
                val request = Request.Builder()
                    .url(API_URL)
                    .addHeader("Authorization", "Bearer $API_KEY")
                    .addHeader("Content-Type", "application/json")
                    .post(body)
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.e("AiAssistant", "Groq Error: ${response.code} | ${response.message}")
                        return@use null
                    }

                    val responseBody = response.body?.string() ?: return@use null
                    val jsonResponse = JSONObject(responseBody)
                    
                    // Navigate path: choices[0].message.content
                    val text = jsonResponse.getJSONArray("choices")
                        .getJSONObject(0)
                        .getJSONObject("message")
                        .getString("content")

                    text.trim().replace("\"", "") // Remove any quotes the AI might include
                }
            } catch (e: Exception) {
                Log.e("AiAssistant", "Groq Network Error: ${e.message}")
                null
            }
        }
    }
}
