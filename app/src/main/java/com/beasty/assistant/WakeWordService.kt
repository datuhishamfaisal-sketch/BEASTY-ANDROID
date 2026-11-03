package com.beasty.assistant

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.KeywordSpotter
import com.k2fsa.sherpa.onnx.KeywordSpotterConfig
import com.k2fsa.sherpa.onnx.getKwsModelConfig
import com.k2fsa.sherpa.onnx.getFeatureConfig
import com.k2fsa.sherpa.onnx.getKeywordsFile

class WakeWordService : Service() {

    companion object {
        const val ACTION_START = "com.beasty.assistant.WAKE_START"
        const val ACTION_STOP = "com.beasty.assistant.WAKE_STOP"
        const val ACTION_PAUSE = "com.beasty.assistant.WAKE_PAUSE"
        const val ACTION_RESUME = "com.beasty.assistant.WAKE_RESUME"
        const val ACTION_WAKE_DETECTED = "com.beasty.assistant.WAKE_DETECTED"
        const val EXTRA_WAKE_DETECTED = "wake_detected"
        private const val CHANNEL_ID = "beasty_wake_word"
        private const val NOTIFICATION_ID = 4101
        private const val SAMPLE_RATE = 16000
        private const val MODEL_DIR = "sherpa-onnx-kws-zipformer-gigaspeech-3.3M-2024-01-01"
    }

    @Volatile private var running = false
    @Volatile private var detectorThread: Thread? = null
    @Volatile private var recorder: AudioRecord? = null
    @Volatile private var spotter: KeywordSpotter? = null
    @Volatile private var stream: com.k2fsa.sherpa.onnx.OnlineStream? = null

    override fun onBind(intent: Intent?): IBinder? = null

    // Persist the last native startup checkpoint so a fatal JNI crash can be
    // diagnosed after Android restarts the app (Throwable cannot catch SIGSEGV).
    private fun checkpoint(stage: String) {
        getSharedPreferences("beasty_diagnostics", MODE_PRIVATE).edit()
            .putString("wake_last_stage", stage)
            .putLong("wake_last_time", System.currentTimeMillis())
            .apply()
        android.util.Log.i("BEASTY_WAKE_DIAG", stage)
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            checkpoint("service_onStartCommand")
            // A microphone foreground service must never be resurrected by Android
            // in the background. Only an explicit user tap from the visible
            // activity may start it.
            if (intent == null) {
                checkpoint("service_restart_blocked_null_intent")
                stopSelf(startId)
                return START_NOT_STICKY
            }
            when (intent.action) {
                ACTION_STOP -> {
                    stopDetector()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    return START_NOT_STICKY
                }
                ACTION_PAUSE -> {
                    stopDetector()
                    showNotification("Microphone paused — voice command mode")
                }
                ACTION_RESUME -> {
                    showNotification("Wake word standby — say Hey Jarvis")
                    if (!running && detectorThread?.isAlive != true) startDetector()
                }
                ACTION_START, null -> {
                    // Android 14–16 require the microphone foreground service to
                    // be promoted while the app is visible and microphone access is granted.
                    checkpoint("before_foreground_notification")
                    showNotification("Wake word standby — say Hey Jarvis")
                    checkpoint("foreground_notification_started")
                    if (!running) startDetector()
                }
            }
            // Do not let Android automatically restart a microphone service
            // after the app has gone to the background.
            return START_NOT_STICKY
        } catch (error: Throwable) {
            running = false
            checkpoint("service_start_exception_${error.javaClass.simpleName}")
            getSharedPreferences("beasty_diagnostics", MODE_PRIVATE).edit()
                .putString("wake_last_error", "${error.javaClass.name}: ${error.message}")
                .apply()
            android.util.Log.e("BEASTY_WAKE", "Unable to start microphone foreground service", error)
            try { stopForeground(STOP_FOREGROUND_REMOVE) } catch (_: Throwable) {}
            stopSelf(startId)
            return START_NOT_STICKY
        }
    }

    private fun showNotification(message: String) {
        val openApp = PendingIntent.getActivity(
            this, 1,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_WAKE_DETECTED, message.contains("detected"))
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopService = PendingIntent.getService(
            this, 2,
            Intent(this, WakeWordService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("BEASTY Wake Word")
            .setContentText(message)
            .setContentIntent(openApp)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_media_pause, "Stop", stopService)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            startForeground(
                NOTIFICATION_ID, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "BEASTY Wake Word", NotificationManager.IMPORTANCE_LOW
            )
            channel.description = "Shows when BEASTY is listening for its wake phrase"
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun startDetector() {
        checkpoint("before_detector_thread")
        running = true
        detectorThread = Thread {
            var localSpotter: KeywordSpotter? = null
            var localStream: com.k2fsa.sherpa.onnx.OnlineStream? = null
            var localRecorder: AudioRecord? = null
            var wakeDetected = false
            try {
                checkpoint("detector_thread_started")
                checkpoint("before_sherpa_jni_load")
                System.loadLibrary("sherpa-onnx-jni")
                checkpoint("sherpa_jni_loaded")

                checkpoint("before_kws_model_config")
                val modelConfig = getKwsModelConfig(type = 1)
                    ?: throw IllegalStateException("English wake-word model config unavailable")
                checkpoint("kws_model_config_ready")

                val featureConfig = getFeatureConfig(sampleRate = SAMPLE_RATE, featureDim = 80)
                val keywordFile = getKeywordsFile(type = 1)
                checkpoint("before_keywordspotter_config")
                val config = KeywordSpotterConfig(
                    featConfig = featureConfig,
                    modelConfig = modelConfig,
                    keywordsFile = keywordFile,
                    keywordsScore = 1.5f,
                    keywordsThreshold = 0.25f
                )
                checkpoint("keywordspotter_config_ready")

                checkpoint("before_keywordspotter_native_create")
                val detector = KeywordSpotter(application.assets, config)
                checkpoint("native_model_initialized")
                localSpotter = detector
                spotter = detector
                checkpoint("before_native_stream_create")
                val detectorStream = detector.createStream()
                checkpoint("native_stream_created")
                localStream = detectorStream
                stream = detectorStream

                checkpoint("before_audio_record_init")
                val minBuffer = AudioRecord.getMinBufferSize(
                    SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
                )
                if (minBuffer <= 0) throw IllegalStateException("AudioRecord buffer unavailable")
                val microphone = AudioRecord(
                    MediaRecorder.AudioSource.MIC, SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                    minBuffer * 2
                )
                localRecorder = microphone
                if (microphone.state != AudioRecord.STATE_INITIALIZED) {
                    throw IllegalStateException("Microphone could not be initialized")
                }
                recorder = microphone
                checkpoint("audio_record_initialized")
                microphone.startRecording()
                checkpoint("microphone_recording_started")

                val buffer = ShortArray(1600)
                var lastAudioDiagnosticAt = 0L
                var lastDecodeDiagnosticAt = 0L
                var audioChunks = 0L
                var readyDecodeCount = 0L
                getSharedPreferences("beasty_diagnostics", MODE_PRIVATE).edit()
                    .putLong("wake_audio_chunks", 0L)
                    .putLong("wake_decode_count", 0L)
                    .putLong("wake_ready_count", 0L)
                    .putString("wake_last_keyword", "")
                    .putString("wake_last_tokens", "")
                    .apply()
                while (running) {
                    val count = microphone.read(buffer, 0, buffer.size)
                    if (count > 0) {
                        val now = System.currentTimeMillis()
                        audioChunks++
                        if (now - lastAudioDiagnosticAt >= 3000L) {
                            var energy = 0.0
                            for (i in 0 until count) {
                                val sample = buffer[i] / 32768.0
                                energy += sample * sample
                            }
                            val rms = kotlin.math.sqrt(energy / count)
                            val level = (rms * 1000).toInt()
                            checkpoint("audio_chunks_${audioChunks}_ready_${readyDecodeCount}_rms_${level}")
                            getSharedPreferences("beasty_diagnostics", MODE_PRIVATE).edit()
                                .putLong("wake_audio_chunks", audioChunks)
                                .putLong("wake_decode_count", readyDecodeCount)
                                .putLong("wake_ready_count", readyDecodeCount)
                                .putLong("wake_diagnostic_at", now)
                                .apply()
                            lastAudioDiagnosticAt = now
                        }
                        val samples = FloatArray(count) { buffer[it] / 32768.0f }
                        detectorStream.acceptWaveform(samples, SAMPLE_RATE)
                        while (running && detector.isReady(detectorStream)) {
                            detector.decode(detectorStream)
                            readyDecodeCount++
                            val result = detector.getResult(detectorStream)
                            val diagnosticNow = System.currentTimeMillis()
                            if (diagnosticNow - lastDecodeDiagnosticAt >= 1500L) {
                                getSharedPreferences("beasty_diagnostics", MODE_PRIVATE).edit()
                                    .putString("wake_last_keyword", result.keyword)
                                    .putString("wake_last_tokens", result.tokens.joinToString(" "))
                                    .putLong("wake_last_decode_at", diagnosticNow)
                                    .putLong("wake_decode_count", readyDecodeCount)
                                    .putLong("wake_ready_count", readyDecodeCount)
                                    .putLong("wake_audio_chunks", audioChunks)
                                    .apply()
                                lastDecodeDiagnosticAt = diagnosticNow
                            }
                            val keyword = result.keyword
                            if (keyword.isNotBlank()) {
                                detector.reset(detectorStream)
                                running = false
                                wakeDetected = true
                                showNotification("Hey Jarvis detected — listening for command")
                                break
                            }
                        }
                    }
                }
            } catch (e: Throwable) {
                running = false
                checkpoint("detector_exception_${e.javaClass.simpleName}")
                getSharedPreferences("beasty_diagnostics", MODE_PRIVATE).edit()
                    .putString("wake_last_error", "${e.javaClass.name}: ${e.message}")
                    .apply()
                android.util.Log.e("BEASTY_WAKE", "Wake-word detector crashed", e)
                try {
                    showNotification("Wake word error: ${e.javaClass.simpleName}")
                } catch (notificationError: Throwable) {
                    android.util.Log.e("BEASTY_WAKE", "Could not update wake notification", notificationError)
                }
            } finally {
                checkpoint("detector_cleanup_started")
                try { localRecorder?.stop() } catch (_: Throwable) {}
                try { localRecorder?.release() } catch (_: Throwable) {}
                try { localStream?.release() } catch (_: Throwable) {}
                try { localSpotter?.release() } catch (_: Throwable) {}
                if (recorder === localRecorder) recorder = null
                if (stream === localStream) stream = null
                if (spotter === localSpotter) spotter = null
                checkpoint("detector_stopped")
                // Notify the activity only after AudioRecord and native stream
                // have been released. SpeechRecognizer must not compete with
                // the wake-word recorder for the microphone.
                if (wakeDetected) {
                    sendBroadcast(Intent(ACTION_WAKE_DETECTED).setPackage(packageName))
                }
            }
        }.apply {
            name = "BEASTY-WakeWord"
            start()
        }
    }

    private fun stopDetector() {
        running = false
        val activeRecorder = recorder
        try { activeRecorder?.stop() } catch (_: Exception) {}

        val oldThread = detectorThread
        oldThread?.interrupt()
        if (oldThread != null && oldThread !== Thread.currentThread()) {
            try {
                oldThread.join(1500)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
        if (oldThread == null || !oldThread.isAlive) {
            detectorThread = null
        }
    }

    override fun onDestroy() {
        stopDetector()
        super.onDestroy()
    }
}
