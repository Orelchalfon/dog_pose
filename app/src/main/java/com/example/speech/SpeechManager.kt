package com.example.speech

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log

class SpeechManager(
    private val context: Context,
    private val onWordDetected: () -> Unit
) {
    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false
    private var lastErrorTime = 0L
    private var errorCountInRow = 0
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())

    private val recognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            Log.d("SpeechManager", "Ready for speech")
            errorCountInRow = 0 // reset consecutive error count on successful connection
        }

        override fun onBeginningOfSpeech() {
            Log.d("SpeechManager", "Speech started")
        }

        override fun onRmsChanged(rmsdB: Float) {}

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            Log.d("SpeechManager", "Speech ended")
        }

        override fun onError(error: Int) {
            Log.d("SpeechManager", "Recognizer error: $error")
            handleRecognitionError(error)
        }

        override fun onResults(results: Bundle?) {
            processResults(results)
            errorCountInRow = 0
            if (isListening) {
                safelyRestartListening(500)
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            processResults(partialResults)
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    private fun handleRecognitionError(error: Int) {
        val now = System.currentTimeMillis()
        if (now - lastErrorTime < 1000) {
            errorCountInRow++
        } else {
            errorCountInRow = 1
        }
        lastErrorTime = now

        // Cut-off to prevent battery and log thrashing when system services fail to initialize
        if (errorCountInRow > 5) {
            Log.e("SpeechManager", "Too many rapid consecutive errors ($errorCountInRow). Disabling auto-recognition restart.")
            return
        }

        if (isListening) {
            // Use localized backoff based on error to allow graceful recovery
            val delayMs = when (error) {
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT, SpeechRecognizer.ERROR_NO_MATCH -> 1200L
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> 2500L
                else -> 5000L // Cool down for client, connection, network, or permission/audio system AppOps errors
            }
            safelyRestartListening(delayMs)
        }
    }

    private fun safelyRestartListening(delayMs: Long) {
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({
            if (isListening) {
                startListening()
            }
        }, delayMs)
    }

    private fun processResults(bundle: Bundle?) {
        val matches = bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) ?: return
        for (match in matches) {
            val lowercase = match.lowercase()
            Log.d("SpeechManager", "Recognized speech: $lowercase")
            if (lowercase.contains("sit") || lowercase.contains("שב") || lowercase.contains("סיט")) {
                onWordDetected()
                break
            }
        }
    }

    fun start() {
        if (speechRecognizer != null) return

        try {
            if (SpeechRecognizer.isRecognitionAvailable(context)) {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                    setRecognitionListener(recognitionListener)
                }
                isListening = true
                errorCountInRow = 0
                startListening()
            } else {
                Log.w("SpeechManager", "Speech recognition feature is not available/enabled on this device.")
            }
        } catch (e: Exception) {
            Log.e("SpeechManager", "Failed to start speech recognizer", e)
        }
    }

    private fun startListening() {
        val recognizer = speechRecognizer ?: return
        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                // Hebrew & English locale bindings
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "he-IL")
                putExtra(RecognizerIntent.EXTRA_SUPPORTED_LANGUAGES, arrayListOf("he-IL", "en-US"))
            }
            recognizer.startListening(intent)
        } catch (e: Exception) {
            Log.e("SpeechManager", "Error in startListening", e)
        }
    }

    fun stop() {
        isListening = false
        handler.removeCallbacksAndMessages(null)
        try {
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            Log.e("SpeechManager", "Error stopping speech recognizer", e)
        }
        speechRecognizer = null
    }
}
