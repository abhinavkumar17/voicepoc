package com.example.voicepoc

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.microsoft.cognitiveservices.speech.*
import com.microsoft.cognitiveservices.speech.audio.AudioConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*

// imports you already have for Speech SDK + coroutines + compose state

enum class Sender { User, Bot }
data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val from: Sender,
    val text: String,
    val isThinking: Boolean = false
)
enum class ListenMode { PushToTalk, Continuous }

class VoiceChatViewModel(
    key: String,
    region: String
) : androidx.lifecycle.ViewModel() {

    // -------- UI state --------
    var messages by mutableStateOf(listOf(ChatMessage(from = Sender.Bot, text = "Hello! Tap Mic or type a message."))); private set
    var input by mutableStateOf(""); private set
    var voiceEnabled by mutableStateOf(true); private set
    var isListening by mutableStateOf(false); private set
    var isSpeaking by mutableStateOf(false); private set
    var listenMode by mutableStateOf(ListenMode.Continuous); private set

    // NEW: single flag that drives the whole UI
    var voiceSession by mutableStateOf(false); private set


    // -------- Azure objects --------
    private val speechConfig: com.microsoft.cognitiveservices.speech.SpeechConfig =
        com.microsoft.cognitiveservices.speech.SpeechConfig.fromSubscription(key, region).apply {
            speechRecognitionLanguage = "en-US"
            setProperty(com.microsoft.cognitiveservices.speech.PropertyId.SpeechServiceConnection_EndSilenceTimeoutMs, "1100")
            setSpeechSynthesisOutputFormat(SpeechSynthesisOutputFormat.Raw16Khz16BitMonoPcm)
            speechSynthesisVoiceName = "en-US-JennyNeural"
        }

    private val micInput = com.microsoft.cognitiveservices.speech.audio.AudioConfig.fromDefaultMicrophoneInput()
    private val speakerOut = com.microsoft.cognitiveservices.speech.audio.AudioConfig.fromDefaultSpeakerOutput()
    private val recognizer = com.microsoft.cognitiveservices.speech.SpeechRecognizer(speechConfig, micInput)
    private var synthesizer = com.microsoft.cognitiveservices.speech.SpeechSynthesizer(speechConfig, speakerOut)

    // guards
    private var isStarting = false
    private var isStopping = false

    init {
        // preconnect (optional)
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try { com.microsoft.cognitiveservices.speech.Connection.fromRecognizer(recognizer).openConnection(true) } catch (_: Exception) {}
        }

        recognizer.sessionStarted.addEventListener { _, _ -> isListening = true }
        recognizer.sessionStopped.addEventListener { _, _ -> isListening = false }
        synthesizer.SynthesisStarted.addEventListener { _, _ -> isSpeaking = true }
        synthesizer.SynthesisCompleted.addEventListener { _, _ -> isSpeaking = false }

        // live partials (only when we are not speaking)
        recognizer.recognizing.addEventListener { _, e ->
            if (!isSpeaking) input = e.result.text ?: ""
        }

        // final result in continuous mode
        recognizer.recognized.addEventListener { _, e ->
            if (listenMode != ListenMode.Continuous) return@addEventListener
            val final = e.result.text.orEmpty()
            if (final.isNotBlank()) {
                // half-duplex: stop mic before speaking
                stopMic()
                viewModelScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                    input = ""
                    append(ChatMessage(from = Sender.User, text = final))
                    respond(final)  // will speak; may re-arm mic if session is active
                }
            }
        }
    }

    // ---------- Session helpers (NEW) ----------
    // state
   // var voiceSession by mutableStateOf(false); private set

    fun enterVoiceMode() {
        if (voiceSession) return
        voiceSession = true
        startMic()                          // begin listening right away
    }

    fun endVoiceMode() {
        if (isListening) stopMic()
        if (isSpeaking)  cancelTts()
        voiceSession = false
    }


    // ---------- UI handlers ----------
    fun onInputChange(v: String) { input = v }
    fun sendText() {
        val txt = input.trim(); if (txt.isEmpty()) return
        input = ""
        append(ChatMessage(from = Sender.User, text = txt))
        respond(txt)
    }

    // PTT path (kept for completeness)
    fun tapMic() {
        if (!voiceEnabled || listenMode != ListenMode.PushToTalk) return
        input = ""
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val mic = com.microsoft.cognitiveservices.speech.audio.AudioConfig.fromDefaultMicrophoneInput()
            val oneShot = com.microsoft.cognitiveservices.speech.SpeechRecognizer(speechConfig, mic)
            try {
                val res = oneShot.recognizeOnceAsync().get()
                withContextMain {
                    if (res.reason == com.microsoft.cognitiveservices.speech.ResultReason.RecognizedSpeech) {
                        val t = res.text.orEmpty()
                        if (t.isNotBlank()) {
                            append(ChatMessage(from = Sender.User, text = t))
                            respond(t)
                        }
                    }
                }
            } catch (_: Exception) { } finally {
                try { oneShot.close() } catch (_: Exception) {}
                try { mic.close() } catch (_: Exception) {}
            }
        }
    }

    fun startMic() {
        if (!voiceEnabled || listenMode != ListenMode.Continuous || isListening || isStarting || isStopping) return
        isStarting = true
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try { recognizer.startContinuousRecognitionAsync().get() } catch (_: Exception) {}
            withContextMain { isStarting = false; isListening = true }
        }
    }

    fun stopMic() {
        if (listenMode != ListenMode.Continuous || !isListening || isStopping) return
        isStopping = true
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try { recognizer.stopContinuousRecognitionAsync().get() } catch (_: Exception) {}
            withContextMain { isStopping = false; isListening = false }
        }
    }

   /* private fun respond(userText: String) {
        val thinkId = java.util.UUID.randomUUID().toString()
        append(ChatMessage(id = thinkId, from = Sender.Bot, text = "…", isThinking = true))

        viewModelScope.launch {
            kotlinx.coroutines.delay(300)
            val reply = generateReply(userText)
            replace(thinkId, ChatMessage(from = Sender.Bot, text = reply))
            if (voiceEnabled) speak(reply)

            // re-arm only if we are in an active voice session
            if (voiceSession && listenMode == ListenMode.Continuous && !isListening) {
                kotlinx.coroutines.delay(120)
                startMic()
            }
        }
    }*/

    private fun respond(userText: String) {
        val thinkId = java.util.UUID.randomUUID().toString()
        append(
            ChatMessage(
                id = thinkId,
                from = Sender.Bot,
                text = "",
                isThinking = true
            )
        )

        viewModelScope.launch {
            // ⏳ delay to show typing dots (e.g. 2000 ms = 2s)
            kotlinx.coroutines.delay(2000)

            val reply = generateReply(userText)
            replace(thinkId, ChatMessage(from = Sender.Bot, text = reply))
            if (voiceEnabled) speak(reply)

            if (voiceSession && listenMode == ListenMode.Continuous && !isListening) {
                kotlinx.coroutines.delay(120)
                startMic()
            }
        }
    }


    private suspend fun speak(text: String) = withContextIO {
        withContextMain { isSpeaking = true }
        try {
            val res = synthesizer.SpeakTextAsync(text).get()
            if (res.reason == com.microsoft.cognitiveservices.speech.ResultReason.Canceled) {
                val c = com.microsoft.cognitiveservices.speech.SpeechSynthesisCancellationDetails.fromResult(res)
                append(ChatMessage(from = Sender.Bot, text = "TTS canceled: ${c.errorCode} - ${c.errorDetails}"))
            }
            res.close()
        } finally {
            withContextMain { isSpeaking = false }
        }
    }

    private fun generateReply(userText: String) =
        if (userText.contains("roam", true) && userText.contains("usa", true))
            "For roaming in the USA, you can use Roam Like Home at 16 dollars/day up to 20 days, or a US Travel Pass for 14 or 30 days. Want details?"
        else "You said: $userText"

    private fun append(m: ChatMessage) { messages = messages + m }
    private fun replace(id: String, m: ChatMessage) { messages = messages.map { if (it.id == id) m.copy(id = id) else it } }

    override fun onCleared() {
        super.onCleared()
        try { com.microsoft.cognitiveservices.speech.Connection.fromRecognizer(recognizer).closeConnection() } catch (_: Exception) {}
        recognizer.close(); micInput.close()
        synthesizer.close(); speakerOut.close()
        speechConfig.close()
    }

    fun cancelTts() {
        try { synthesizer.close() } catch (_: Exception) {}
        synthesizer = com.microsoft.cognitiveservices.speech.SpeechSynthesizer(speechConfig, speakerOut)
        isSpeaking = false
    }

    // small helpers
    private suspend fun withContextMain(block: suspend () -> Unit) =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { block() }
    private suspend fun <T> withContextIO(block: suspend () -> T): T =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { block() }
}
