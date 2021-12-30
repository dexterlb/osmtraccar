package org.qtrp.osmtraccar

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import android.view.View
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

object TheServiceRunning {
    var isRunning = false
}

class TheService : Service(), OsmAndHelper.OsmandEventListener, TraccarEventListener {
    interface EventListener {
        fun serviceLogMessage(level: Int, msg: String)
    }

    private var mEventListener: EventListener? = null

    private var startMode: Int = 0             // indicates how to behave if the service is killed
    private var binder = LocalBinder()
    private var allowRebind: Boolean = false   // indicates whether onRebind should be used


    private val pointShower = PointShower()
    private lateinit var traccarApi: TraccarApi

    private var osmandPackage = OsmAndAidlHelper.OSMAND_PLUS_PACKAGE_NAME

    companion object {
        val NOTIFICATION_CHANNEL_PERSISTENT = "notification_channel_persistent"

        val NOTIFICATION_ID_PERSISTENT = 42
    }


    fun begin(activity: Activity) {
        TheServiceRunning.isRunning = true

        log(Log.INFO, "service begin!")

        traccarApi = TraccarApi(this, this)

        pointShower.setOsmandInitActivity(activity)
        initOsmandApi()
    }

    private fun initOsmandApi() {
        pointShower.initOsmAndApi(this, osmandPackage)
        pointShower.clear()
    }


    fun pleaseStop() {
        traccarApi.unsubscribePositionUpdates()
        pointShower.clear()
    }

    override fun osmandBound() {
        // connected to osmand, now we can connect to traccar
        traccarConnect()
    }

    override fun osmandMissing() {
        if (osmandPackage == OsmAndAidlHelper.OSMAND_PLUS_PACKAGE_NAME) {
            osmandPackage = OsmAndAidlHelper.OSMAND_FREE_PACKAGE_NAME
            log(Log.INFO, "failed connecting to OsmAnd Plus. Trying regular OsmAnd.")
            initOsmandApi()
            return
        }
        log(Log.ERROR, "oh no, OsmAnd seems to be missing!")
        stopSelf()
    }

    override fun osmandLog(priority: Int, msg: String) {
        log(priority, "[osmand] $msg")
    }

    fun traccarConnect() {
        val scope = CoroutineScope(Job() + Dispatchers.IO)

        scope.launch {
            val points = try {
                traccarApi.getPoints()
            } catch (e: Exception) {
                log(Log.ERROR, "could not get data from traccar: $e")
                traccarSocketConnectedState(false)
                return@launch
            }
            log(Log.VERBOSE, "points: $points")
            pointShower.setPoints(points)

            traccarApi.subscribeToPositionUpdates()
        }
    }

    override fun traccarSocketConnectedState(isConnected: Boolean) {
        if (isConnected) {
            log(Log.INFO, "traccar connected")
        } else {
            log(Log.INFO, "traccar disconnected")
            pointShower.clear()
            stopSelf()
        }
    }

    override fun traccarPositionUpdate(pos: Position) {
        pointShower.updatePosition(pos)
    }

    override fun traccarApiLogMessage(level: Int, msg: String) {
        log(level, "[traccar] $msg")
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
        mEventListener = null   // TODO: is this the only case when the eventListener will become defunct?
        return allowRebind
    }

    override fun onRebind(intent: Intent) {
        // A client is binding to the service with bindService(),
        // after onUnbind() has already been called
    }

    override fun onDestroy() {
        // The service is no longer used and is being destroyed
        TheServiceRunning.isRunning = false
        traccarApi.unsubscribePositionUpdates()
        pointShower.clear()
    }


    fun hello(eventListener: EventListener) {
        mEventListener = eventListener

        log(Log.INFO, "hello from service")
    }

    private fun log(priority: Int, msg: String) {
        val eventListener = mEventListener
        if (eventListener != null) {
            eventListener.serviceLogMessage(priority, msg)
        } else {
            Log.println(priority, "the_service", "[background] $msg")
        }
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