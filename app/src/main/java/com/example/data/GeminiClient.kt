package com.example.data

import android.content.Context
import android.util.Base64
import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

object GeminiClient {
    private const val TAG = "GeminiClient"
    private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private fun getApiKey(): String {
        return BuildConfig.GEMINI_API_KEY
    }

    /**
     * Generate content for general text analysis, summarization, or query tasks.
     * Model default: gemini-3.5-flash
     */
    suspend fun generateText(
        prompt: String,
        systemInstruction: String? = null,
        model: String = "gemini-3.5-flash"
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val apiKey = getApiKey()
            if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
                return@withContext Result.failure(
                    IllegalStateException("Gemini API key is missing or default. Please configure GEMINI_API_KEY in Secrets panel.")
                )
            }

            val requestJson = JSONObject().apply {
                val contentsArray = JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().put("text", prompt))
                        })
                    })
                }
                put("contents", contentsArray)

                if (!systemInstruction.isNullOrBlank()) {
                    put("systemInstruction", JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().put("text", systemInstruction))
                        })
                    })
                }
            }

            val url = "$BASE_URL$model:generateContent"
            val body = requestJson.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder().url(url).addHeader("x-goog-api-key", apiKey).post(body).build()

            val response = okHttpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                Log.e(TAG, "API Error (${response.code}): $responseBody")
                return@withContext Result.failure(Exception("HTTP ${response.code}: $responseBody"))
            }

            val jsonResponse = JSONObject(responseBody)
            val text = extractTextFromResponse(jsonResponse)
            Result.success(text)
        } catch (e: Exception) {
            Log.e(TAG, "Failed generateText", e)
            Result.failure(e)
        }
    }

    /**
     * Multi-turn Chat request supporting conversation history and system instructions.
     * History is passed as list of Pair(role: "user" | "model", text: String).
     */
    suspend fun generateChat(
        history: List<Pair<String, String>>,
        userMessage: String,
        systemInstruction: String = "You are Nova AI, an executive AI assistant.",
        model: String = "gemini-3.5-flash"
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val apiKey = getApiKey()
            if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
                return@withContext Result.failure(
                    IllegalStateException("Gemini API key is missing. Please configure GEMINI_API_KEY in Secrets.")
                )
            }

            val requestJson = JSONObject().apply {
                val contentsArray = JSONArray()

                // Add chat history
                for ((role, text) in history) {
                    val apiRole = if (role.equals("user", ignoreCase = true)) "user" else "model"
                    contentsArray.put(JSONObject().apply {
                        put("role", apiRole)
                        put("parts", JSONArray().apply {
                            put(JSONObject().put("text", text))
                        })
                    })
                }

                // Add current user message
                contentsArray.put(JSONObject().apply {
                    put("role", "user")
                    put("parts", JSONArray().apply {
                        put(JSONObject().put("text", userMessage))
                    })
                })

                put("contents", contentsArray)

                if (systemInstruction.isNotBlank()) {
                    put("systemInstruction", JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().put("text", systemInstruction))
                        })
                    })
                }
            }

            val url = "$BASE_URL$model:generateContent"
            val body = requestJson.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder().url(url).addHeader("x-goog-api-key", apiKey).post(body).build()

            val response = okHttpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP ${response.code}: $responseBody"))
            }

            val jsonResponse = JSONObject(responseBody)
            val text = extractTextFromResponse(jsonResponse)
            Result.success(text)
        } catch (e: Exception) {
            Log.e(TAG, "Failed generateChat", e)
            Result.failure(e)
        }
    }

    /**
     * Transcribe Audio file using gemini-3.5-flash model with inline audio data.
     */
    suspend fun transcribeAudio(
        audioFile: File,
        mimeType: String = "audio/wav"
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val apiKey = getApiKey()
            if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
                return@withContext Result.failure(
                    IllegalStateException("Gemini API key is missing.")
                )
            }

            val audioBytes = audioFile.readBytes()
            val base64Data = Base64.encodeToString(audioBytes, Base64.NO_WRAP)

            val requestJson = JSONObject().apply {
                val contentsArray = JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", "Please provide a verbatim transcription of this audio recording. If there are key points, include a brief summary below the transcript.")
                            })
                            put(JSONObject().apply {
                                put("inlineData", JSONObject().apply {
                                    put("mimeType", mimeType)
                                    put("data", base64Data)
                                })
                            })
                        })
                    })
                }
                put("contents", contentsArray)
            }

            val url = "${BASE_URL}gemini-3.5-flash:generateContent"
            val body = requestJson.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder().url(url).addHeader("x-goog-api-key", apiKey).post(body).build()

            val response = okHttpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP ${response.code}: $responseBody"))
            }

            val jsonResponse = JSONObject(responseBody)
            val text = extractTextFromResponse(jsonResponse)
            Result.success(text)
        } catch (e: Exception) {
            Log.e(TAG, "Failed transcribeAudio", e)
            Result.failure(e)
        }
    }

    /**
     * Deep Thinking / Reasoning Mode using gemini-3.1-pro-preview model with thinkingLevel set to HIGH.
     */
    suspend fun deepReasoning(
        prompt: String,
        systemInstruction: String = "You are a master strategist and analytical thinker. Break down complex queries into thorough reasoning steps and clear actionable conclusions."
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val apiKey = getApiKey()
            if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
                return@withContext Result.failure(
                    IllegalStateException("Gemini API key is missing.")
                )
            }

            val requestJson = JSONObject().apply {
                val contentsArray = JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().put("text", prompt))
                        })
                    })
                }
                put("contents", contentsArray)

                put("systemInstruction", JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().put("text", systemInstruction))
                    })
                })

                put("generationConfig", JSONObject().apply {
                    put("thinkingConfig", JSONObject().apply {
                        put("thinkingLevel", "HIGH")
                    })
                })
            }

            val url = "${BASE_URL}gemini-3.1-pro-preview:generateContent"
            val body = requestJson.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder().url(url).addHeader("x-goog-api-key", apiKey).post(body).build()

            val response = okHttpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP ${response.code}: $responseBody"))
            }

            val jsonResponse = JSONObject(responseBody)
            val text = extractTextFromResponse(jsonResponse)
            Result.success(text)
        } catch (e: Exception) {
            Log.e(TAG, "Failed deepReasoning", e)
            Result.failure(e)
        }
    }

    private fun extractTextFromResponse(jsonResponse: JSONObject): String {
        val candidates = jsonResponse.optJSONArray("candidates") ?: return "No response text received."
        if (candidates.length() == 0) return "No response text received."

        val firstCandidate = candidates.getJSONObject(0)
        val content = firstCandidate.optJSONObject("content") ?: return "No response content."
        val parts = content.optJSONArray("parts") ?: return "No text parts in response."

        val sb = StringBuilder()
        for (i in 0 until parts.length()) {
            val part = parts.getJSONObject(i)
            if (part.has("text")) {
                sb.append(part.getString("text"))
            }
        }
        return sb.toString().ifEmpty { "No text content generated." }
    }
}
