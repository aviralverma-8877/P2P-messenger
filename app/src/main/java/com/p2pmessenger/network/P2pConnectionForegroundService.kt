package com.p2pmessenger.network

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.p2pmessenger.R
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Keeps [P2pSocketManager]'s listening socket alive in the background so contacts can reach us
 * even when the app isn't in the foreground -- Android would otherwise tear down our socket
 * along with the process.
 */
@AndroidEntryPoint
class P2pConnectionForegroundService : Service() {

    @Inject
    lateinit var socketManager: P2pSocketManager

    companion object {
        private const val CHANNEL_ID = "p2p_connection"
        private const val NOTIFICATION_ID = 1
    }

    override fun onCreate() {
        super.onCreate()
        createChannelIfNeeded()
        startForeground(NOTIFICATION_ID, buildNotification())
        socketManager.startListening()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        socketManager.stopListening()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannelIfNeeded() {
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.p2p_service_channel_name),
            NotificationManager.IMPORTANCE_MIN,
        )
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.p2p_service_notification_title))
            .setContentText(getString(R.string.p2p_service_notification_text))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .build()
}
