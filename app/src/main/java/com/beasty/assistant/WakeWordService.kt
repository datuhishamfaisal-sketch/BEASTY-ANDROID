package com.beasty.assistant

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.os.Handler
import android.os.Looper
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.RecognitionListener
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.os.Bundle
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale
import androidx.core.app.NotificationCompat
import com.openwakeword.OpenWakeWord

class WakeWordService : Service() {
    companion object {
        const val ACTION_START = "com.beasty.assistant.WAKE_START"
        const val ACTION_STOP = "com.beasty.assistant.WAKE_STOP"
        const val ACTION_PAUSE = "com.beasty.assistant.WAKE_PAUSE"
        const val ACTION_RESUME = "com.beasty.assistant.WAKE_RESUME"
        const val ACTION_WAKE_DETECTED = "com.beasty.assistant.WAKE_DETECTED"
        const val ACTION_VOICE_TRANSCRIPT = "com.beasty.assistant.VOICE_TRANSCRIPT"
        const val ACTION_VOICE_REPLY = "com.beasty.assistant.VOICE_REPLY"
        const val ACTION_VOICE_STATUS = "com.beasty.assistant.VOICE_STATUS"
        const val EXTRA_TEXT = "text"
        const val EXTRA_WAKE_DETECTED = "wake_detected"
        private const val CHANNEL_ID = "beasty_wake_word"
        private const val NOTIFICATION_ID = 4101
        private const val TAG = "BEASTY_WAKE"
    }

    @Volatile private var detector: OpenWakeWord? = null
    @Volatile private var listening = false
    @Volatile private var foregroundReady = false
    @Volatile private var conversationActive = false
    @Volatile private var requestBusy = false
    private var speechRecognizer: SpeechRecognizer? = null
    private var textToSpeech: TextToSpeech? = null
    private var ttsReady = false
    private val handler = Handler(Looper.getMainLooper())
    private val conversationHistory = JSONArray()
    private val geminiModel = "gemini-3.8-flash"
    private val groqModel = "openai/gpt-oss-20b"

    override fun onBind(intent: Intent?): IBinder? = null

    private fun checkpoint(stage: String, error: String? = null) {
        val edit = getSharedPreferences("beasty_diagnostics", MODE_PRIVATE).edit()
            .putString("wake_last_stage", stage)
            .putLong("wake_last_time", System.currentTimeMillis())
        if (error != null) edit.putString("wake_last_error", error)
        edit.apply()
        Log.i(TAG, stage)
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        initializeTextToSpeech()
        try {
            promoteToForeground("Starting Hey Jarvis")
            foregroundReady = true
            checkpoint("foreground_promoted")
        } catch (t: Throwable) {
            checkpoint("foreground_promotion_failed", t.javaClass.name + ": " + t.message)
            Log.e(TAG, "Foreground promotion failed", t)
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            if (!foregroundReady) throw IllegalStateException("Foreground microphone service not active")
            when (intent?.action) {
                ACTION_STOP -> {
                    conversationActive = false
                    requestBusy = false
                    releaseSpeechRecognizer()
                    try { textToSpeech?.stop() } catch (_: Throwable) {}
                    stopDetector()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf(startId)
                    return START_NOT_STICKY
                }
                ACTION_PAUSE -> {
                    conversationActive = false
                    requestBusy = false
                    releaseSpeechRecognizer()
                    stopDetector()
                    updateNotification("Microphone paused")
                }
                ACTION_START, ACTION_RESUME -> {
                    if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
                        throw SecurityException("RECORD_AUDIO permission is missing")
                    conversationActive = false
                    releaseSpeechRecognizer()
                    updateNotification("Hey Jarvis is listening")
                    startDetector()
                }
                else -> throw IllegalArgumentException("Unknown wake-word service action")
            }
        } catch (t: Throwable) {
            checkpoint("service_start_failed", t.javaClass.name + ": " + t.message)
            Log.e(TAG, "Wake service start failed", t)
            stopDetector()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf(startId)
        }
        return START_NOT_STICKY
    }

    private fun startDetector() {
        if (listening && detector != null) return
        checkpoint("before_openwakeword_create")
        val engine = OpenWakeWord.Builder(applicationContext)
            .setModel(OpenWakeWord.BuiltInModel.HEY_JARVIS)
            .setThreshold(0.5f)
            .setDebounceMs(2500L)
            .build()
        detector = engine
        engine.setOnErrorListener { t ->
            listening = false
            checkpoint("openwakeword_processing_error", t.javaClass.name + ": " + t.message)
            Log.e(TAG, "Wake detector processing failed", t)
            stopDetector()
            updateNotification("Wake word error: " + t.javaClass.simpleName)
        }
        listening = true
        checkpoint("before_openwakeword_start")
        engine.start { score ->
            if (listening) {
                listening = false
                checkpoint("hey_jarvis_detected_" + score)
                Thread({
                    try { engine.release() } catch (t: Throwable) { Log.e(TAG, "Release failed", t) }
                    if (detector === engine) detector = null
                    updateNotification("Hey Jarvis detected. Listening for your question")
                    sendBroadcast(Intent(ACTION_WAKE_DETECTED).setPackage(packageName))
                    handler.postDelayed({
                        if (foregroundReady) startConversationListening()
                    }, 850)
                }, "BEASTY-WakeRelease").start()
            }
        }
        checkpoint("openwakeword_start_requested")
    }


    // Voice recognition and AI conversation live in this foreground service,
    // not in MainActivity, so they can continue while another app is visible.
    private fun startConversationListening() {
        if (!foregroundReady || requestBusy) return
        conversationActive = true
        stopDetector()
        try {
            if (!SpeechRecognizer.isRecognitionAvailable(this)) {
                updateNotification("Speech recognition is unavailable")
                sendStatus("Speech recognition unavailable")
                return
            }
            releaseSpeechRecognizer()
            val recognizer = SpeechRecognizer.createSpeechRecognizer(applicationContext)
            speechRecognizer = recognizer
            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    updateNotification("Listening. Speak now")
                    sendStatus("Listening")
                }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {
                    updateNotification("Processing your voice")
                    sendStatus("Processing")
                }
                override fun onError(error: Int) {
                    Log.w(TAG, "Speech recognition error: $error")
                    releaseSpeechRecognizer()
                    if (conversationActive && error != SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                        updateNotification("Listening again")
                        handler.postDelayed({ startConversationListening() }, 1300)
                    } else {
                        sendStatus("Voice recognition error $error")
                    }
                }
                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val spoken = matches?.firstOrNull()?.trim().orEmpty()
                    releaseSpeechRecognizer()
                    if (spoken.isEmpty()) {
                        if (conversationActive) handler.postDelayed({ startConversationListening() }, 700)
                        return
                    }
                    sendBroadcast(Intent(ACTION_VOICE_TRANSCRIPT).setPackage(packageName)
                        .putExtra(EXTRA_TEXT, spoken))
                    addHistory("user", spoken)
                    requestBusy = true
                    updateNotification("BEASTY is thinking")
                    sendStatus("Thinking")
                    Thread({
                        val answer = generateAnswer()
                        handler.post {
                            requestBusy = false
                            if (!conversationActive) return@post
                            if (answer.isNullOrBlank()) {
                                updateNotification("Connection error. Listening again")
                                sendStatus("Connection error")
                                handler.postDelayed({ startConversationListening() }, 1200)
                            } else {
                                addHistory("assistant", answer)
                                sendBroadcast(Intent(ACTION_VOICE_REPLY).setPackage(packageName)
                                    .putExtra(EXTRA_TEXT, answer))
                                speakAnswer(answer)
                            }
                        }
                    }, "BEASTY-AI-Request").start()
                }
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ms-MY")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }
            recognizer.startListening(intent)
        } catch (t: Throwable) {
            Log.e(TAG, "Could not start service speech recognition", t)
            updateNotification("Voice error. Retrying")
            handler.postDelayed({ if (conversationActive) startConversationListening() }, 1800)
        }
    }

    private fun releaseSpeechRecognizer() {
        val old = speechRecognizer
        speechRecognizer = null
        try { old?.cancel() } catch (_: Throwable) {}
        try { old?.destroy() } catch (_: Throwable) {}
    }

    private fun initializeTextToSpeech() {
        try {
            textToSpeech = TextToSpeech(applicationContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    val engine = textToSpeech
                    try {
                        val languageResult = engine?.setLanguage(Locale("ms", "MY"))
                        if (languageResult == TextToSpeech.LANG_MISSING_DATA ||
                            languageResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                            engine?.setLanguage(Locale.US)
                        }
                        engine?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                            override fun onStart(utteranceId: String?) {}
                            override fun onDone(utteranceId: String?) {
                                if (utteranceId == "BEASTY_BACKGROUND_REPLY") {
                                    handler.postDelayed({ if (conversationActive && !requestBusy) startConversationListening() }, 900)
                                }
                            }
                            @Deprecated("Deprecated in Java")
                            override fun onError(utteranceId: String?) {
                                if (utteranceId == "BEASTY_BACKGROUND_REPLY") {
                                    handler.postDelayed({ if (conversationActive && !requestBusy) startConversationListening() }, 900)
                                }
                            }
                            override fun onError(utteranceId: String?, errorCode: Int) {
                                if (utteranceId == "BEASTY_BACKGROUND_REPLY") {
                                    handler.postDelayed({ if (conversationActive && !requestBusy) startConversationListening() }, 900)
                                }
                            }
                        })
                        ttsReady = true
                    } catch (t: Throwable) { Log.e(TAG, "TTS setup failed", t) }
                }
            }
        } catch (t: Throwable) { Log.e(TAG, "TTS initialization failed", t) }
    }

    private fun speakAnswer(answer: String) {
        if (!getSharedPreferences("beasty_voice", MODE_PRIVATE).getBoolean("voice_enabled", true)) {
            handler.postDelayed({ if (conversationActive) startConversationListening() }, 500)
            return
        }
        val engine = textToSpeech
        if (engine == null || !ttsReady) {
            handler.postDelayed({ if (conversationActive) startConversationListening() }, 1200)
            return
        }
        updateNotification("BEASTY is replying")
        sendStatus("Speaking")
        try {
            engine.speak(answer, TextToSpeech.QUEUE_FLUSH, null, "BEASTY_BACKGROUND_REPLY")
        } catch (t: Throwable) {
            Log.e(TAG, "TTS speak failed", t)
            handler.postDelayed({ if (conversationActive) startConversationListening() }, 900)
        }
    }

    private fun sendStatus(status: String) {
        sendBroadcast(Intent(ACTION_VOICE_STATUS).setPackage(packageName).putExtra(EXTRA_TEXT, status))
    }

    private fun addHistory(role: String, text: String) {
        synchronized(conversationHistory) {
            conversationHistory.put(JSONObject().put("role", role).put("text", text))
            while (conversationHistory.length() > 20) conversationHistory.remove(0)
        }
    }

    private fun generateAnswer(): String? {
        val instruction = "Gunakan Smart Mode. Seimbangkan kelajuan, kecerdasan dan jawapan yang lengkap. Jawab dalam bahasa pengguna."
        var geminiError = ""
        var groqError = ""
        try {
            return callGemini(instruction)
        } catch (t: Throwable) { geminiError = t.message ?: "Gemini error" }
        try {
            return callGroq(instruction)
        } catch (t: Throwable) { groqError = t.message ?: "Groq error" }
        Log.e(TAG, "AI providers failed. Gemini: $geminiError. Groq: $groqError")
        return "Maaf. BEASTY tidak dapat menyambung kepada AI sekarang."
    }

    private fun callGemini(instruction: String): String {
        val key = BuildConfig.GEMINI_API_KEY
        if (key.isBlank()) throw IllegalStateException("Gemini API key missing")
        val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/$geminiModel:generateContent?key=" +
            URLEncoder.encode(key, "UTF-8")
        val body = JSONObject()
        val parts = JSONArray().put(JSONObject().put("text",
            "You are BEASTY, an intelligent AI assistant. Be helpful, accurate and friendly. Answer using the user's language. $instruction Write in plain natural text only. Never use emojis, emoticons, decorative symbols, markdown formatting, dashes, hyphens, commas, semicolons or unnecessary special characters. Use simple sentences with normal full stops only."))
        body.put("systemInstruction", JSONObject().put("parts", parts))
        val contents = JSONArray()
        synchronized(conversationHistory) {
            for (i in 0 until conversationHistory.length()) {
                val item = conversationHistory.getJSONObject(i)
                contents.put(JSONObject()
                    .put("role", if (item.optString("role") == "assistant") "model" else "user")
                    .put("parts", JSONArray().put(JSONObject().put("text", item.optString("text")))))
            }
        }
        body.put("contents", contents)
        body.put("generationConfig", JSONObject().put("temperature", 0.7).put("maxOutputTokens", 4096))
        val response = makeRequest(endpoint, body, null)
        val candidates = JSONObject(response).getJSONArray("candidates")
        if (candidates.length() == 0) throw IllegalStateException("Gemini returned no candidates")
        val responseParts = candidates.getJSONObject(0).getJSONObject("content").getJSONArray("parts")
        val output = StringBuilder()
        for (i in 0 until responseParts.length()) output.append(responseParts.getJSONObject(i).optString("text", ""))
        return output.toString().trim().also { if (it.isEmpty()) throw IllegalStateException("Gemini returned empty response") }
    }

    private fun callGroq(instruction: String): String {
        val key = BuildConfig.GROQ_API_KEY
        if (key.isBlank()) throw IllegalStateException("Groq API key missing")
        val body = JSONObject().put("model", groqModel).put("temperature", 0.7).put("max_tokens", 4096)
        val messages = JSONArray().put(JSONObject().put("role", "system").put("content",
            "You are BEASTY, an intelligent AI assistant. Be helpful, accurate and friendly. Answer using the user's language. $instruction Write in plain natural text only. Never use emojis, emoticons, decorative symbols, markdown formatting, dashes, hyphens, commas, semicolons or unnecessary special characters. Use simple sentences with normal full stops only."))
        synchronized(conversationHistory) {
            for (i in 0 until conversationHistory.length()) {
                val item = conversationHistory.getJSONObject(i)
                messages.put(JSONObject()
                    .put("role", if (item.optString("role") == "assistant") "assistant" else "user")
                    .put("content", item.optString("text")))
            }
        }
        body.put("messages", messages)
        val response = JSONObject(makeRequest("https://api.groq.com/openai/v1/chat/completions", body, key))
        val choices = response.getJSONArray("choices")
        if (choices.length() == 0) throw IllegalStateException("Groq returned no choices")
        return choices.getJSONObject(0).getJSONObject("message").optString("content", "").trim()
            .also { if (it.isEmpty()) throw IllegalStateException("Groq returned empty response") }
    }

    private fun makeRequest(endpoint: String, body: JSONObject, apiKey: String?): String {
        var connection: HttpURLConnection? = null
        try {
            connection = URL(endpoint).openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.connectTimeout = 20000
            connection.readTimeout = 60000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            if (apiKey != null) connection.setRequestProperty("Authorization", "Bearer $apiKey")
            connection.outputStream.use { output: OutputStream ->
                output.write(body.toString().toByteArray(StandardCharsets.UTF_8))
            }
            val code = connection.responseCode
            val stream: InputStream? = if (code in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() } ?: ""
            if (code !in 200..299) {
                val detail = try { JSONObject(response).getJSONObject("error").optString("message", response) }
                    catch (_: Throwable) { response }
                throw IllegalStateException("HTTP $code: $detail")
            }
            return response
        } finally { connection?.disconnect() }
    }

    private fun stopDetector() {
        listening = false
        val old = detector
        detector = null
        try { old?.release() } catch (t: Throwable) {
            checkpoint("openwakeword_release_error", t.javaClass.name + ": " + t.message)
            Log.e(TAG, "Detector release failed", t)
        }
        checkpoint("openwakeword_stopped")
    }

    private fun promoteToForeground(message: String) {
        val notification = buildNotification(message)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else startForeground(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(message: String): Notification {
        val open = PendingIntent.getActivity(this, 1, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 2,
            Intent(this, WakeWordService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("BEASTY Wake Word").setContentText(message)
            .setContentIntent(open).setOngoing(true).setSilent(true)
            .addAction(android.R.drawable.ic_media_pause, "Stop", stop).build()
    }

    private fun updateNotification(message: String) {
        try { getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(message)) }
        catch (t: Throwable) { Log.e(TAG, "Notification update failed", t) }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "BEASTY Wake Word", NotificationManager.IMPORTANCE_LOW)
            channel.description = "Shows when BEASTY is listening for Hey Jarvis"
            channel.setShowBadge(false)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        conversationActive = false
        handler.removeCallbacksAndMessages(null)
        releaseSpeechRecognizer()
        try { textToSpeech?.stop(); textToSpeech?.shutdown() } catch (_: Throwable) {}
        textToSpeech = null
        stopDetector()
        super.onDestroy()
    }
}
