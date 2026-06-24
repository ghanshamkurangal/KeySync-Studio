package com.example.api

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.example.BuildConfig
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object GeminiService {
    private const val TAG = "GeminiService"
    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    // Convert Bitmap to Base64 String
    private fun Bitmap.toBase64(): String {
        val outputStream = ByteArrayOutputStream()
        this.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
        return Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
    }

    suspend fun analyzeSheetMusic(bitmap: Bitmap, customApiKey: String? = null): AnalyzedSongResult? = withContext(Dispatchers.IO) {
        val rawApiKey = customApiKey ?: BuildConfig.GEMINI_API_KEY
        val apiKey = rawApiKey.trim().removeSurrounding("\"").removeSurrounding("'")
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            throw Exception("Gemini API key is not configured. Please open the Secrets panel in AI Studio, add 'GEMINI_API_KEY' with your valid API key, or enter a custom key in the fields below, and rebuild the app.")
        }

        val base64Image = bitmap.toBase64()

        // Create the system instruction / prompt to ask Gemini to extract sheet music notes
        val promptText = """
            You are an expert music teacher and sheet music transcriber. 
            Analyze this uploaded sheet music image. Your job is to extract:
            1. Title (e.g., "Aura Lee")
            2. Artist (e.g., "Traditional" or the composer if visible)
            3. Difficulty Level: Classify as "Easy", "Medium", or "Hard".
            4. Description: A short description of the piece and tips for playing it.
            5. Notes: The list of consecutive musical notes. For each note, extract its Pitch (e.g. "C4", "D4", "E4", "F4", "G4", "A4", "B4", "C5", "D5", "E5", "F5", "G5", "A5", "B5"). 
               Include flat or sharp accidentals if present, e.g. "D#4" or "A#4" or "F#4".
               Also extract the syllable or word associated with this note (the lyric). If there is no lyric, just use the pitch name itself.

            You MUST return the output ONLY as a valid JSON object with the following structure:
            {
              "title": "Aura Lee",
              "artist": "Traditional",
              "difficulty": "Easy",
              "description": "A beautiful traditional American Civil War song, simple melody in G major.",
              "notes": [
                {"pitch": "G4", "lyric": "As"},
                {"pitch": "C4", "lyric": "the"},
                {"pitch": "B4", "lyric": "black-"},
                {"pitch": "C4", "lyric": "bird"}
              ]
            }

            Do not wrap the JSON in Markdown backticks. Do not include any explanation. Just return pure valid JSON.
        """.trimIndent()

        // Construct request payload manually using JSONObject for type safety
        val requestJson = JSONObject()
        val contentsArray = JSONArray()
        val contentObject = JSONObject()
        val partsArray = JSONArray()

        // Text Part
        val textPart = JSONObject().put("text", promptText)
        partsArray.put(textPart)

        // Image Part
        val imagePart = JSONObject().put("inlineData", JSONObject().apply {
            put("mimeType", "image/jpeg")
            put("data", base64Image)
        })
        partsArray.put(imagePart)

        contentObject.put("parts", partsArray)
        contentsArray.put(contentObject)
        requestJson.put("contents", contentsArray)

        // Set response schema to JSON format if desired
        val generationConfig = JSONObject().apply {
            put("responseMimeType", "application/json")
            put("temperature", 0.2) // Low temperature for factual transcription
        }
        requestJson.put("generationConfig", generationConfig)

        val requestBodyString = requestJson.toString()

        val modelsToTry = listOf("gemini-2.5-flash", "gemini-1.5-flash")
        var lastException: Exception? = null

        for (modelName in modelsToTry) {
            val url = "https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent?key=${apiKey}"
            val request = Request.Builder()
                .url(url)
                .post(requestBodyString.toRequestBody(JSON_MEDIA_TYPE))
                .build()

            try {
                Log.d(TAG, "Trying Gemini API with model: $modelName...")
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        val errorBody = response.body?.string() ?: ""
                        Log.e(TAG, "Model $modelName failed with code: ${response.code}, body: $errorBody")
                        val errMsg = try {
                            val errorJson = JSONObject(errorBody)
                            val errorObj = errorJson.optJSONObject("error")
                            errorObj?.optString("message") ?: "API Error code ${response.code}"
                        } catch (e: Exception) {
                            "API Error code ${response.code}: $errorBody"
                        }
                        throw Exception(errMsg)
                    }

                    val responseBodyStr = response.body?.string() ?: throw Exception("Empty response body from Gemini API")
                    Log.d(TAG, "Gemini $modelName Response: ${responseBodyStr}")

                    val responseJson = JSONObject(responseBodyStr)
                    val candidates = responseJson.optJSONArray("candidates")
                    if (candidates == null || candidates.length() == 0) {
                        throw Exception("No content candidates returned by the model")
                    }

                    val content = candidates.getJSONObject(0).optJSONObject("content") ?: throw Exception("Candidate has no content")
                    val parts = content.optJSONArray("parts") ?: throw Exception("Content has no parts")
                    if (parts.length() == 0) {
                        throw Exception("Content parts are empty")
                    }

                    val textResponse = parts.getJSONObject(0).optString("text") ?: throw Exception("Part contains no text")
                    
                    // Parse the inner JSON result returned from Gemini
                    var jsonClean = textResponse.trim()
                    if (jsonClean.startsWith("```json")) {
                        jsonClean = jsonClean.removePrefix("```json")
                    }
                    if (jsonClean.startsWith("```")) {
                        jsonClean = jsonClean.removePrefix("```")
                    }
                    if (jsonClean.endsWith("```")) {
                        jsonClean = jsonClean.removeSuffix("```")
                    }
                    jsonClean = jsonClean.trim()

                    val resultObj = JSONObject(jsonClean)
                    val title = resultObj.optString("title", "Unnamed AI Track")
                    val artist = resultObj.optString("artist", "AI Extracted")
                    val difficulty = resultObj.optString("difficulty", "Easy")
                    val description = resultObj.optString("description", "")
                    
                    val notesArray = resultObj.optJSONArray("notes")
                    val songNotesList = mutableListOf<AnalyzedNote>()
                    if (notesArray != null) {
                        for (i in 0 until notesArray.length()) {
                            val noteObj = notesArray.getJSONObject(i)
                            val pitch = noteObj.optString("pitch", "")
                            if (pitch.isNotEmpty()) {
                                songNotesList.add(
                                    AnalyzedNote(
                                        pitch = pitch,
                                        lyric = noteObj.optString("lyric", pitch)
                                    )
                                )
                            }
                        }
                    }

                    return@withContext AnalyzedSongResult(
                        title = title,
                        artist = artist,
                        difficulty = difficulty,
                        description = description,
                        notes = songNotesList
                    )
                }
            } catch (e: Exception) {
                lastException = e
                Log.w(TAG, "Failed with model $modelName: ${e.message}. Trying next fallback...")
            }
        }

        // If we reached here, all models failed. Re-throw the last exception so the user can see it!
        throw lastException ?: Exception("Unknown error occurred during sheet music transcription.")
    }
}

data class AnalyzedNote(
    val pitch: String,
    val lyric: String
)

data class AnalyzedSongResult(
    val title: String,
    val artist: String,
    val difficulty: String,
    val description: String,
    val notes: List<AnalyzedNote>
)
