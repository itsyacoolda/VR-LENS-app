package com.example.service

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.data.VrPreferencesRepository
import java.util.Locale

/**
 * Hands-Free VR Voice Control Engine.
 * Listens for natural voice commands ("click", "swipe left", "swipe right", "scroll down",
 * "scroll up", "back", "home", "volume up", "volume down", "zoom in", "zoom out", "ar mode",
 * "pause", "play", "recenter") and dispatches the corresponding VR / OS action.
 */
class VrVoiceControlManager(
    private val context: Context,
    private val onHapticFeedback: (Long) -> Unit,
    private val onClickCenterDot: () -> Unit,
    private val onPlayPauseToggle: () -> Unit
) {
    private val tag = "VrVoiceControlManager"
    private val mainHandler = Handler(Looper.getMainLooper())
    private val prefsRepo = VrPreferencesRepository.getInstance(context)
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    private var speechRecognizer: SpeechRecognizer? = null
    private var isRunning = false
    private var isCurrentlyListening = false

    fun updateFromSettings() {
        val enabled = prefsRepo.getSettings().voiceControlEnabled
        if (enabled && !isRunning) {
            startVoiceControl()
        } else if (!enabled && isRunning) {
            stopVoiceControl()
        }
    }

    fun startVoiceControl() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            prefsRepo.setVoiceStatus(
                isListening = false,
                commandText = "🎙️ Grant Microphone Permission to Enable Voice Control"
            )
            return
        }

        isRunning = true
        mainHandler.post {
            try {
                if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                    prefsRepo.setVoiceStatus(
                        isListening = false,
                        commandText = "🎙️ Voice Engine Ready (Use Quick Voice Commands or Mic)"
                    )
                    return@post
                }
                if (speechRecognizer == null) {
                    speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                        setRecognitionListener(recognitionListener)
                    }
                }
                prefsRepo.setVoiceStatus(
                    isListening = true,
                    commandText = "🎙️ Listening: Say \"Click\", \"Swipe Left\", \"Home\", \"Back\"..."
                )
                startListeningCycle()
            } catch (e: Exception) {
                Log.w(tag, "SpeechRecognizer start warning: ${e.message}")
                prefsRepo.setVoiceStatus(
                    isListening = false,
                    commandText = "🎙️ Voice Commands Ready"
                )
            }
        }
    }

    private fun startListeningCycle() {
        if (!isRunning || isCurrentlyListening) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            stopVoiceControl()
            return
        }

        mainHandler.post {
            try {
                val recognizer = speechRecognizer ?: return@post
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                }
                isCurrentlyListening = true
                prefsRepo.setVoiceStatus(isListening = true)
                recognizer.startListening(intent)
            } catch (e: Exception) {
                isCurrentlyListening = false
                Log.w(tag, "startListeningCycle error: ${e.message}")
            }
        }
    }

    private val recognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            prefsRepo.setVoiceStatus(isListening = true)
        }

        override fun onBeginningOfSpeech() {}

        override fun onRmsChanged(rmsdB: Float) {}

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            isCurrentlyListening = false
        }

        override fun onError(error: Int) {
            isCurrentlyListening = false
            if (isRunning && prefsRepo.getSettings().voiceControlEnabled) {
                mainHandler.postDelayed({ startListeningCycle() }, 900L)
            }
        }

        override fun onResults(results: Bundle?) {
            isCurrentlyListening = false
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val bestPhrase = matches?.firstOrNull()
            if (!bestPhrase.isNullOrBlank()) {
                executeVoiceCommand(bestPhrase)
            }
            if (isRunning && prefsRepo.getSettings().voiceControlEnabled) {
                mainHandler.postDelayed({ startListeningCycle() }, 450L)
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val partial = matches?.firstOrNull()
            if (!partial.isNullOrBlank()) {
                prefsRepo.setVoiceStatus(
                    isListening = true,
                    commandText = "🎙️ Hearing: \"$partial\"..."
                )
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    /**
     * Parses and executes a voice command phrase (either spoken into the microphone
     * or triggered from the Voice Control Command Deck).
     */
    fun executeVoiceCommand(rawSpeech: String) {
        val text = rawSpeech.lowercase(Locale.US).trim()
        val a11y = VrAccessibilityService.instance

        when {
            text.contains("click") || text.contains("tap") || text.contains("select") || text.contains("press") -> {
                onHapticFeedback(35L)
                onClickCenterDot()
                prefsRepo.setVoiceStatus(commandText = "🎙️ Voice \"${rawSpeech}\" ➔ Clicked Center Dot!")
            }
            text.contains("left") || text.contains("next") -> {
                onHapticFeedback(30L)
                prefsRepo.moveCursorByDelta(-0.15f, 0f)
                a11y?.dispatchDirectionalSlide("SWIPE_LEFT")
                prefsRepo.setVoiceStatus(commandText = "🎙️ Voice \"${rawSpeech}\" ➔ Slid App Left")
            }
            text.contains("right") || text.contains("previous") -> {
                onHapticFeedback(30L)
                prefsRepo.moveCursorByDelta(+0.15f, 0f)
                a11y?.dispatchDirectionalSlide("SWIPE_RIGHT")
                prefsRepo.setVoiceStatus(commandText = "🎙️ Voice \"${rawSpeech}\" ➔ Slid App Right")
            }
            text.contains("down") -> {
                onHapticFeedback(30L)
                if (text.contains("volume") || text.contains("quiet") || text.contains("soft")) {
                    audioManager?.adjustStreamVolume(
                        AudioManager.STREAM_MUSIC,
                        AudioManager.ADJUST_LOWER,
                        AudioManager.FLAG_SHOW_UI
                    )
                    prefsRepo.setVoiceStatus(commandText = "🎙️ Voice \"${rawSpeech}\" ➔ Volume Down")
                } else {
                    prefsRepo.moveCursorByDelta(0f, +0.15f)
                    a11y?.dispatchDirectionalSlide("SWIPE_DOWN")
                    prefsRepo.setVoiceStatus(commandText = "🎙️ Voice \"${rawSpeech}\" ➔ Scrolled App Down")
                }
            }
            text.contains("up") -> {
                onHapticFeedback(30L)
                if (text.contains("volume") || text.contains("loud")) {
                    audioManager?.adjustStreamVolume(
                        AudioManager.STREAM_MUSIC,
                        AudioManager.ADJUST_RAISE,
                        AudioManager.FLAG_SHOW_UI
                    )
                    prefsRepo.setVoiceStatus(commandText = "🎙️ Voice \"${rawSpeech}\" ➔ Volume Up")
                } else {
                    prefsRepo.moveCursorByDelta(0f, -0.15f)
                    a11y?.dispatchDirectionalSlide("SWIPE_UP")
                    prefsRepo.setVoiceStatus(commandText = "🎙️ Voice \"${rawSpeech}\" ➔ Scrolled App Up")
                }
            }
            text.contains("back") || text.contains("return") -> {
                onHapticFeedback(35L)
                a11y?.triggerBackButton()
                prefsRepo.setVoiceStatus(commandText = "🎙️ Voice \"${rawSpeech}\" ➔ Gear VR Back")
            }
            text.contains("home") -> {
                onHapticFeedback(35L)
                a11y?.triggerHomeButton()
                prefsRepo.setVoiceStatus(commandText = "🎙️ Voice \"${rawSpeech}\" ➔ Gear VR Home")
            }
            text.contains("zoom in") || text.contains("bigger") || text.contains("closer") -> {
                onHapticFeedback(25L)
                prefsRepo.adjustScale(+0.06f)
                prefsRepo.setVoiceStatus(commandText = "🎙️ Voice \"${rawSpeech}\" ➔ Zoomed Screen In")
            }
            text.contains("zoom out") || text.contains("smaller") || text.contains("further") -> {
                onHapticFeedback(25L)
                prefsRepo.adjustScale(-0.06f)
                prefsRepo.setVoiceStatus(commandText = "🎙️ Voice \"${rawSpeech}\" ➔ Zoomed Screen Out")
            }
            text.contains("ar mode") || text.contains("enable ar") || text.contains("passthrough") -> {
                onHapticFeedback(40L)
                prefsRepo.setArPassthroughEnabled(true)
                prefsRepo.setVoiceStatus(commandText = "🎙️ Voice \"${rawSpeech}\" ➔ Enabled AR Mode")
            }
            text.contains("vr mode") || text.contains("disable ar") -> {
                onHapticFeedback(40L)
                prefsRepo.setArPassthroughEnabled(false)
                prefsRepo.setVoiceStatus(commandText = "🎙️ Voice \"${rawSpeech}\" ➔ Switched to VR Mode")
            }
            text.contains("pause") || text.contains("play") || text.contains("resume") -> {
                onHapticFeedback(35L)
                onPlayPauseToggle()
                prefsRepo.setVoiceStatus(commandText = "🎙️ Voice \"${rawSpeech}\" ➔ Toggled Play/Pause")
            }
            text.contains("center") || text.contains("recenter") || text.contains("reset") -> {
                onHapticFeedback(30L)
                prefsRepo.recenterCursor()
                VrOverlayService.instance?.recenterHeadPose()
                prefsRepo.setVoiceStatus(commandText = "🎙️ Voice \"${rawSpeech}\" ➔ Centered Head View & 3D Reticle")
            }
            text.contains("head tracking") -> {
                onHapticFeedback(35L)
                val enable = !text.contains("off") && !text.contains("disable")
                prefsRepo.setHeadTrackingEnabled(enable)
                prefsRepo.setVoiceStatus(commandText = "🎙️ Voice \"${rawSpeech}\" ➔ Head Tracking ${if (enable) "ON" else "OFF"}")
            }
            text.contains("cinema") || text.contains("curved") || text.contains("theater") -> {
                onHapticFeedback(35L)
                prefsRepo.setCinemaScreenEnabled(true)
                prefsRepo.setVoiceStatus(commandText = "🎙️ Voice \"${rawSpeech}\" ➔ Curved Cinema Screen ON")
            }
            text.contains("flat") -> {
                onHapticFeedback(35L)
                prefsRepo.setCinemaScreenEnabled(false)
                prefsRepo.setVoiceStatus(commandText = "🎙️ Voice \"${rawSpeech}\" ➔ Flat Screen Mode ON")
            }
            else -> {
                prefsRepo.setVoiceStatus(commandText = "🎙️ Heard: \"$rawSpeech\"")
            }
        }
    }

    fun stopVoiceControl() {
        isRunning = false
        isCurrentlyListening = false
        mainHandler.post {
            try {
                speechRecognizer?.stopListening()
                speechRecognizer?.cancel()
            } catch (_: Exception) {}
            prefsRepo.setVoiceStatus(
                isListening = false,
                commandText = "🎙️ Voice Control Paused"
            )
        }
    }

    fun release() {
        stopVoiceControl()
        mainHandler.post {
            try {
                speechRecognizer?.destroy()
            } catch (_: Exception) {}
            speechRecognizer = null
        }
    }
}
