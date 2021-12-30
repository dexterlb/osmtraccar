package org.qtrp.osmtraccar

import android.app.*
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import androidx.core.app.NotificationCompat

class TheService : Service() {
    private var startMode: Int = 0             // indicates how to behave if the service is killed
    private var binder = LocalBinder()
    private var allowRebind: Boolean = false   // indicates whether onRebind should be used

    companion object {
        val NOTIFICATION_CHANNEL_PERSISTENT = "notification_channel_persistent"

        val NOTIFICATION_ID_PERSISTENT = 42
    }

    override fun onCreate() {
        // The service is being created

        createNotificationChannels()

        val pendingIntent: PendingIntent =
            Intent(this, MainActivity::class.java).let { notificationIntent ->
                PendingIntent.getActivity(this, 0, notificationIntent, 0)
            }

        val notification: Notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_PERSISTENT)
            .setContentTitle("ble ble")
            .setContentText("working")
            // .setSmallIcon(R.drawable.icon)
            .setContentIntent(pendingIntent)
            .setTicker("tick tick tick")
            .build()

        // Notification ID cannot be 0.
        startForeground(NOTIFICATION_ID_PERSISTENT, notification)

    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // The service is starting, due to a call to startService()

        return startMode
    }

    override fun onBind(intent: Intent): IBinder {
        // A client is binding to the service with bindService()
        return binder
    }

    override fun onUnbind(intent: Intent): Boolean {
        // All clients have unbound with unbindService()
        return allowRebind
    }

    override fun onRebind(intent: Intent) {
        // A client is binding to the service with bindService(),
        // after onUnbind() has already been called
    }

    override fun onDestroy() {
        // The service is no longer used and is being destroyed
    }

    inner class LocalBinder : Binder() {
        fun service(): TheService {
            return this@TheService
        }
    }

    private fun createNotificationChannels() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O
            && !notificationChannelAlreadyCreated(NOTIFICATION_CHANNEL_PERSISTENT)) {
            // Create the NotificationChannel
            val name = "Persistent"
            val descriptionText = "Persistent notification for long-running service"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val mChannel = NotificationChannel(NOTIFICATION_CHANNEL_PERSISTENT, name, importance)
            mChannel.description = descriptionText
            // Register the channel with the system; you can't change the importance
            // or other notification behaviors after this
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(mChannel)
        }
    }

    private fun notificationChannelAlreadyCreated(id: String): Boolean {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O) {
            return true
        }

        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        return (notificationManager.getNotificationChannel(id) != null)
    }
}