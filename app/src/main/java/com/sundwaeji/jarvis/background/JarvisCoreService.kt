package com.sundwaeji.jarvis.background

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.sundwaeji.jarvis.MainActivity
import com.sundwaeji.jarvis.wake.WakeEngineState
import com.sundwaeji.jarvis.wake.WakeWordEngine

class JarvisCoreService : Service() {
    private var wakeEngine: WakeWordEngine? = null
    private var wakeState = WakeEngineState.STOPPED
    private var wakePausedForConversation = false

    override fun onCreate() {
        super.onCreate()
        createChannel()
        wakeEngine = WakeWordEngine(
            context = this,
            onState = { state, detail ->
                wakeState = state
                updateNotification(detail)
                sendBroadcast(Intent(ACTION_WAKE_STATE).setPackage(packageName)
                    .putExtra(EXTRA_WAKE_STATE, state.name).putExtra(EXTRA_WAKE_DETAIL, detail))
            },
            onWakeDetected = {
                sendBroadcast(Intent(ACTION_WAKE_DETECTED).setPackage(packageName))
                updateNotification("Wake detected. Open JARVIS to continue.")
            },
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        startForeground(NOTIFICATION_ID, notification())
        when (intent?.action) {
            ACTION_PAUSE_WAKE -> {
                wakePausedForConversation = true
                wakeEngine?.stop()
                updateNotification("마이크를 음성 명령에 사용 중")
            }
            ACTION_RESUME_WAKE -> {
                wakePausedForConversation = false
                wakeEngine?.start()
            }
            else -> if (!wakePausedForConversation) wakeEngine?.start()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        wakeEngine?.stop()
        wakeEngine = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun notification(): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            1,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stopIntent = PendingIntent.getService(
            this,
            2,
            Intent(this, JarvisCoreService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_CORE)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("JARVIS 온라인")
            .setContentText(wakeText())
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(0, "OPEN", openIntent)
            .addAction(0, "STOP", stopIntent)
            .build()
    }

    private fun wakeText(): String = when (wakeState) {
        WakeEngineState.LISTENING -> "로컬 호출어 감지 대기 중"
        WakeEngineState.UNAVAILABLE -> "이 기기에서 로컬 호출어를 사용할 수 없습니다"
        WakeEngineState.ERROR -> "호출어 엔진을 복구 중"
        WakeEngineState.DETECTED -> "호출 감지됨"
        else -> "백그라운드 시스템 준비 중"
    }

    private fun updateNotification(detail: String?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification())
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_CORE, "JARVIS Core", NotificationManager.IMPORTANCE_LOW).apply {
                description = "JARVIS 백그라운드 상태"
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    companion object {
        const val ACTION_STOP = "com.sundwaeji.jarvis.action.STOP_CORE"
        const val ACTION_WAKE_STATE = "com.sundwaeji.jarvis.action.WAKE_STATE"
        const val ACTION_WAKE_DETECTED = "com.sundwaeji.jarvis.action.WAKE_DETECTED"
        const val ACTION_PAUSE_WAKE = "com.sundwaeji.jarvis.action.PAUSE_WAKE"
        const val ACTION_RESUME_WAKE = "com.sundwaeji.jarvis.action.RESUME_WAKE"
        const val EXTRA_WAKE_STATE = "wake_state"
        const val EXTRA_WAKE_DETAIL = "wake_detail"
        private const val CHANNEL_CORE = "jarvis_core"
        private const val NOTIFICATION_ID = 1001
    }
}
