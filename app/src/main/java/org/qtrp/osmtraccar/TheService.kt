package org.qtrp.osmtraccar

import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Binder
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

object TheServiceRunning {
    var isRunning = false
}

class TheService : Service(), OsmAndHelper.OsmandEventListener, TraccarApi.EventListener {
    interface EventListener {
        fun serviceLogMessage(level: Int, msg: String)
    }

    private var notificationReceivers: MutableList<BroadcastReceiver> = mutableListOf()
    private var mEventListener: EventListener? = null

    private var startMode: Int = 0             // indicates how to behave if the service is killed
    private var binder = LocalBinder()
    private var allowRebind: Boolean = false   // indicates whether onRebind should be used


    private val pointShower = PointShower()
    private lateinit var traccarApi: TraccarApi

    private var osmandPackage = OsmAndAidlHelper.OSMAND_PLUS_PACKAGE_NAME

    companion object {
        const val NOTIFICATION_CHANNEL_PERSISTENT = "notification_channel_persistent"

        const val NOTIFICATION_ID_PERSISTENT = 42

        const val ACTION_STOP_SERVICE = "action_stop_service"
        const val ACTION_SHOW_OSMAND  = "action_show_osmand"
    }


    fun begin(activity: Activity) {
        TheServiceRunning.isRunning = true

        log(Log.INFO, "service begin!")

        traccarApi = TraccarApi(this, this)

        pointShower.setOsmandInitActivity(activity)

        initOsmandApi()
    }

    private fun initOsmandApi() {
        updateNotification("connecting to OsmAnd")
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
        updateNotification("connecting to Traccar")
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

    fun showOsmAnd() {
        pointShower.showOsmAnd(this)
    }

    override fun traccarSocketConnectedState(isConnected: Boolean) {
        if (isConnected) {
            log(Log.INFO, "traccar connected")
            updateNotification("connected")
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
        // Notification ID cannot be 0.
        registerNotificationActions()
        startForeground(NOTIFICATION_ID_PERSISTENT, makeNotification("starting"))
    }

    private fun makeNotification(text: String): Notification {
        createNotificationChannels()

        val pendingIntent: PendingIntent =
            Intent(this, MainActivity::class.java).let { notificationIntent ->
                PendingIntent.getActivity(this, 0, notificationIntent, 0)
            }

        val stopActionIntent = PendingIntent.getBroadcast(
            this,
            0,
            Intent(ACTION_STOP_SERVICE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopAction = NotificationCompat.Action(R.drawable.osmtraccar_stop_icon, "stop", stopActionIntent)

        val showOsmAndActionIntent = PendingIntent.getBroadcast(
            this,
            0,
            Intent(ACTION_SHOW_OSMAND),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val showOsmAndAction = NotificationCompat.Action(R.drawable.osmtraccar_go_icon, "show osmand", showOsmAndActionIntent)

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_PERSISTENT)
            .setContentTitle("Traccar -> OsmAnd")
            .setContentText(text)
            .setSmallIcon(R.drawable.osmtraccar_notif_icon)
            .setContentIntent(pendingIntent)
            .setTicker(text)
            .addAction(stopAction)
            .addAction(showOsmAndAction)
            .build()
    }

    private fun registerNotificationActions() {
        registerNotificationReceiver(object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    pleaseStop()
                }
            }, IntentFilter(ACTION_STOP_SERVICE)
        )

        registerNotificationReceiver(object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    pointShower.showOsmAnd(this@TheService)
                }
            }, IntentFilter(ACTION_SHOW_OSMAND)
        )
    }

    private fun registerNotificationReceiver(rec: BroadcastReceiver, filter: IntentFilter) {
        registerReceiver(rec, filter)
        notificationReceivers.add(rec)
    }
    
    private fun unregisterNotificationActions() {
        for (rec in notificationReceivers) {
            unregisterReceiver(rec)
        }
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
        unregisterNotificationActions()
        traccarApi.unsubscribePositionUpdates()
        pointShower.clear()
    }


    fun hello(eventListener: EventListener) {
        mEventListener = eventListener

        log(Log.INFO, "hello from service")
    }

    private fun updateNotification(text: String) {
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID_PERSISTENT, makeNotification(text))
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
        if (notificationChannelAlreadyCreated(NOTIFICATION_CHANNEL_PERSISTENT)) {
            // Create the NotificationChannel
            val name = "Persistent"
            val descriptionText = "Persistent notification for long-running service"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val mChannel = NotificationChannel(NOTIFICATION_CHANNEL_PERSISTENT, name, importance)
            mChannel.setSound(null, null)
            mChannel.description = descriptionText
            // Register the channel with the system; you can't change the importance
            // or other notification behaviors after this
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(mChannel)
        }
    }

    private fun notificationChannelAlreadyCreated(id: String): Boolean {
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        return (notificationManager.getNotificationChannel(id) != null)
    }
}