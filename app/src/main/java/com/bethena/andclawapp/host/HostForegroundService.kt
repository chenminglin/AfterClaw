package com.bethena.andclawapp.host

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.bethena.andclawapp.R
import com.bethena.andclawapp.hostController

class HostForegroundService : Service() {
    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        applicationContext.hostController().setServiceRunning(true)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action ?: ACTION_ENSURE_HOST) {
            ACTION_ENSURE_HOST -> applicationContext.hostController().bootstrapHost()
            ACTION_INSTALL_OPENCLAW -> applicationContext.hostController().installOrUpdateOpenClaw()
            ACTION_START_GATEWAY -> applicationContext.hostController().startGateway()
            ACTION_STOP_GATEWAY -> applicationContext.hostController().stopGateway()
            ACTION_STOP_SERVICE -> {
                applicationContext.hostController().stopGateway()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        applicationContext.hostController().setServiceRunning(false)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < 26) {
            return
        }
        val manager = getSystemService(NotificationManager::class.java)
        val existing = manager.getNotificationChannel(CHANNEL_ID)
        if (existing != null) {
            return
        }
        val channel = NotificationChannel(
            CHANNEL_ID,
            "AndClaw Host",
            NotificationManager.IMPORTANCE_LOW,
        )
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(getString(R.string.host_notification_title))
            .setContentText(getString(R.string.host_notification_text))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "andclaw.host.runtime"
        private const val NOTIFICATION_ID = 1007

        const val ACTION_ENSURE_HOST = "com.bethena.andclawapp.action.ENSURE_HOST"
        const val ACTION_INSTALL_OPENCLAW = "com.bethena.andclawapp.action.INSTALL_OPENCLAW"
        const val ACTION_START_GATEWAY = "com.bethena.andclawapp.action.START_GATEWAY"
        const val ACTION_STOP_GATEWAY = "com.bethena.andclawapp.action.STOP_GATEWAY"
        const val ACTION_STOP_SERVICE = "com.bethena.andclawapp.action.STOP_SERVICE"

        fun start(context: Context, action: String = ACTION_ENSURE_HOST) {
            val intent = Intent(context, HostForegroundService::class.java).setAction(action)
            ContextCompat.startForegroundService(context, intent)
        }
    }
}
