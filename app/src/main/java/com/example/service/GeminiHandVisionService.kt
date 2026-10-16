package com.example.service

import android.graphics.Bitmap
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
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

data class HandVisionResult(
    val gesture: String, // SWIPE_LEFT, SWIPE_RIGHT, SWIPE_UP, SWIPE_DOWN, PINCH_TAP, OPEN_PALM, FIST_BACK, POINT_MOVE, NONE
    val handX: Float = 0.5f,
    val handY: Float = 0.5f,
    val actionLabel: String = "No Hand Detected"
)

/**
 * Gemini AI Vision (gemini-3.5-flash) Spatial Hand Gesture Detector.
 * Analyzes camera frames from the mobile VR/AR headset to detect Apple Vision Pro style
 * pinch, point, and directional swipe gestures and maps them to underlying app movements.
 */
object GeminiHandVisionService {
    private const val TAG = "GeminiHandVisionService"
    private const val ENDPOINT =
        "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    fun isApiKeyConfigured(): Boolean {
        val key = BuildConfig.GEMINI_API_KEY
        return key.isNotBlank() && key != "MY_GEMINI_API_KEY"
    }

    private fun Bitmap.toBase64Jpeg(): String {
        val out = ByteArrayOutputStream()
        compress(Bitmap.CompressFormat.JPEG, 72, out)
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    suspend fun analyzeHandGestureFrame(bitmap: Bitmap): HandVisionResult = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (!isApiKeyConfigured()) {
            return@withContext HandVisionResult(
                gesture = "NONE",
                actionLabel = "Optical Hand Tracker Active (Set GEMINI_API_KEY in Secrets for AI Vision)"
            )
        }

        try {
            val base64Image = bitmap.toBase64Jpeg()

            val promptText = """
                You are an Apple Vision Pro spatial hand-tracking vision model analyzing an outward-facing VR/AR headset camera frame.
                Identify the user's hand gesture and its normalized position in the frame (handX: 0.0 left to 1.0 right, handY: 0.0 top to 1.0 bottom).
                Classify "gesture" as EXACTLY one of:
                - "PINCH_TAP": thumb and index finger pinched together (action: click at cursor)
                - "SWIPE_LEFT": hand or fingers swiping/pointing left (action: slide left)
                - "SWIPE_RIGHT": hand or fingers swiping/pointing right (action: slide right)
                - "SWIPE_UP": hand or fingers pointing/swiping up (action: slide/scroll up)
                - "SWIPE_DOWN": hand or fingers pointing/swiping down (action: slide/scroll down)
                - "OPEN_PALM": open palm facing camera with 5 fingers spread (action: play/pause media)
                - "FIST_BACK": closed fist (action: navigate back)
                - "POINT_MOVE": single index finger pointing to move cursor (action: move 3D cursor)
                - "NONE": no hand visible
                Respond ONLY with a valid JSON object:
                {"gesture":"...", "handX":0.5, "handY":0.5, "actionLabel":"short description of gesture and movement"}
            """.trimIndent()

            val inlineDataObj = JSONObject().apply {
                put("mimeType", "image/jpeg")
                put("data", base64Image)
            }

            val partsArray = JSONArray().apply {
                put(JSONObject().put("text", promptText))
                put(JSONObject().put("inlineData", inlineDataObj))
            }

            val contentsArray = JSONArray().apply {
                put(JSONObject().put("parts", partsArray))
            }

            val generationConfig = JSONObject().apply {
                put("responseMimeType", "application/json")
                put("temperature", 0.1)
            }

            val payload = JSONObject().apply {
                put("contents", contentsArray)
                put("generationConfig", generationConfig)
            }

            val request = Request.Builder()
                .url("$ENDPOINT?key=$apiKey")
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "Gemini Vision HTTP ${response.code}")
                    return@withContext HandVisionResult(gesture = "NONE")
                }

                val bodyStr = response.body?.string() ?: return@withContext HandVisionResult(gesture = "NONE")
                val rootJson = JSONObject(bodyStr)
                val textContent = rootJson
                    .optJSONArray("candidates")
                    ?.optJSONObject(0)
                    ?.optJSONObject("content")
                    ?.optJSONArray("parts")
                    ?.optJSONObject(0)
                    ?.optString("text", "")
                    ?: return@withContext HandVisionResult(gesture = "NONE")

                val parsed = JSONObject(textContent)
                val gesture = parsed.optString("gesture", "NONE").uppercase()
                val handX = parsed.optDouble("handX", 0.5).toFloat().coerceIn(0.05f, 0.95f)
                val handY = parsed.optDouble("handY", 0.5).toFloat().coerceIn(0.05f, 0.95f)
                val label = parsed.optString("actionLabel", gesture)

                return@withContext HandVisionResult(
                    gesture = gesture,
                    handX = handX,
                    handY = handY,
                    actionLabel = label
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Gemini hand vision error: ${e.message}")
            return@withContext HandVisionResult(gesture = "NONE")
        }
    }
}
