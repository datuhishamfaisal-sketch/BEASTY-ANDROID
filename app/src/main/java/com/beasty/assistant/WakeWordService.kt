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
import androidx.core.app.NotificationCompat
import com.openwakeword.OpenWakeWord

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
        private const val TAG = "BEASTY_WAKE"
    }

    @Volatile private var detector: OpenWakeWord? = null
    @Volatile private var listening = false
    @Volatile private var foregroundReady = false

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
                    stopDetector()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf(startId)
                    return START_NOT_STICKY
                }
                ACTION_PAUSE -> {
                    stopDetector()
                    updateNotification("Microphone paused")
                }
                ACTION_START, ACTION_RESUME -> {
                    if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
                        throw SecurityException("RECORD_AUDIO permission is missing")
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
                    updateNotification("Hey Jarvis detected")
                    sendBroadcast(Intent(ACTION_WAKE_DETECTED).setPackage(packageName))
                }, "BEASTY-WakeRelease").start()
            }
        }
        checkpoint("openwakeword_start_requested")
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
        stopDetector()
        super.onDestroy()
    }
}
