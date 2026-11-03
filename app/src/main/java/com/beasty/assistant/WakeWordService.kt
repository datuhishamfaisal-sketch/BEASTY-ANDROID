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

class WakeWordService : Service() {

    companion object {
        const val ACTION_START = "com.beasty.assistant.WAKE_START"
        const val ACTION_STOP = "com.beasty.assistant.WAKE_STOP"
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

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopDetector()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START, null -> {
                showNotification("Wake word standby — say Hey BEASTY")
                if (!running) startDetector()
            }
        }
        return START_STICKY
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
        running = true
        detectorThread = Thread {
            var localSpotter: KeywordSpotter? = null
            var localStream: com.k2fsa.sherpa.onnx.OnlineStream? = null
            var localRecorder: AudioRecord? = null
            try {
                val config = KeywordSpotterConfig(
                    featConfig = FeatureConfig(sampleRate = SAMPLE_RATE, featureDim = 80),
                    modelConfig = getKwsModelConfig(type = 1)
                        ?: throw IllegalStateException("English wake-word model config unavailable"),
                    keywordsFile = "$MODEL_DIR/keywords.txt"
                )
                val detector = KeywordSpotter(application.assets, config)
                localSpotter = detector
                spotter = detector
                val detectorStream = detector.createStream()
                localStream = detectorStream
                stream = detectorStream

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
                microphone.startRecording()

                val buffer = ShortArray(1600)
                while (running) {
                    val count = microphone.read(buffer, 0, buffer.size)
                    if (count > 0) {
                        val samples = FloatArray(count) { buffer[it] / 32768.0f }
                        detectorStream.acceptWaveform(samples, SAMPLE_RATE)
                        while (running && detector.isReady(detectorStream)) {
                            detector.decode(detectorStream)
                            val keyword = detector.getResult(detectorStream).keyword
                            if (keyword.isNotBlank()) {
                                detector.reset(detectorStream)
                                running = false
                                showNotification("Hey BEASTY detected — tap to talk")
                                sendBroadcast(Intent(ACTION_WAKE_DETECTED).setPackage(packageName))
                                break
                            }
                        }
                    }
                }
            } catch (e: Throwable) {
                running = false
                android.util.Log.e("BEASTY_WAKE", "Wake-word detector crashed", e)
                try {
                    showNotification("Wake word error: ${e.javaClass.simpleName}")
                } catch (notificationError: Throwable) {
                    android.util.Log.e("BEASTY_WAKE", "Could not update wake notification", notificationError)
                }
            } finally {
                try { localRecorder?.stop() } catch (_: Throwable) {}
                try { localRecorder?.release() } catch (_: Throwable) {}
                try { localStream?.release() } catch (_: Throwable) {}
                try { localSpotter?.release() } catch (_: Throwable) {}
                if (recorder === localRecorder) recorder = null
                if (stream === localStream) stream = null
                if (spotter === localSpotter) spotter = null
            }
        }.apply {
            name = "BEASTY-WakeWord"
            start()
        }
    }

    private fun stopDetector() {
        running = false
        try { recorder?.stop() } catch (_: Exception) {}
        detectorThread?.interrupt()
        detectorThread = null
    }

    override fun onDestroy() {
        stopDetector()
        super.onDestroy()
    }
}
