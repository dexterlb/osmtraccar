package org.qtrp.osmtraccar

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Color
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.IBinder
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.util.Log
import android.view.View
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.qtrp.osmtraccar.databinding.ActivityMainBinding

@Suppress("UNUSED_PARAMETER")
class MainActivity : AppCompatActivity(), TheService.EventListener, TraccarEventListener {
    var mService: TheService? = null


    private val TAG = "main"
    private lateinit var binding: ActivityMainBinding


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root

        binding.logEdit.setHorizontallyScrolling(true)

        setContentView(view)

        connectService(start = false)
    }

    fun requestStartService(view: View) {
        connectService(start = true)
    }

    private fun connectService(start: Boolean) {
        val isRunning = TheServiceRunning.isRunning
        if (!isRunning && !start) {
            return
        }

        val conn = object : ServiceConnection {
            // Called when the connection with the service is established
            override fun onServiceConnected(className: ComponentName, service: IBinder) {
                // Because we have bound to an explicit
                // service that is running in our own process, we can
                // cast its IBinder to a concrete class and directly access it.
                val binder = service as TheService.LocalBinder
                val serviceObj = binder.service()
                serviceObj.hello(this@MainActivity)
                mService = serviceObj
                onServiceBound()
                if (!isRunning && start) {
                    serviceObj.begin(this@MainActivity)
                }
            }

            // Called when the connection with the service disconnects unexpectedly
            override fun onServiceDisconnected(className: ComponentName) {
                mService = null
                onServiceDied()
            }
        }

        Intent(this, TheService::class.java).also { intent ->
            if (!isRunning) {
                log(Log.INFO, "start service")

                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    applicationContext.startForegroundService(intent)
                } else {
                    // startForegroundService not available on older APIs
                    applicationContext.startService(intent)
                }
            }

            bindService(intent, conn, 0)
        }
    }

    fun requestStopService(view: View) {
        val service = this.mService
        if (service == null) {
            log(Log.ERROR, "trying to tell service to stop, but it is already stopped?")
            return
        }

        service.pleaseStop()
    }

    private fun onServiceBound() {
        log(Log.INFO, "service bound")
        binding.buttonStop.isEnabled = true
        binding.buttonStart.isEnabled = false
    }

    private fun onServiceDied() {
        log(Log.INFO, "service died")
        binding.buttonStop.isEnabled = false
        binding.buttonStart.isEnabled = true
    }

    fun traccarLogin(view: View) {
        val traccarApi = TraccarApi(this, this)

        val onLogin: (TraccarConnData) -> Unit = { connData ->
            val scope = CoroutineScope(Job() + Dispatchers.IO)

            scope.launch {
                try {
                    if (connData.pass == "") {
                        log(Log.WARN, "trying to login with empty password; this is probably not what you want.")
                    }
                    traccarApi.login(connData.url, connData.email, connData.pass)
                } catch(e: Exception) {
                    log(Log.ERROR, "unable to login: $e\nyou entered the correct URL, email and password, right?")
                }
            }
        }

        var oldData: TraccarConnData? = null
        var oldURL = traccarApi.getURL()
        var oldEmail = traccarApi.getEmail()
        if (oldURL != null && oldEmail != null) {
            oldData = TraccarConnData(
                email = oldEmail,
                url = oldURL,
                pass = ""
            )
        }

        LoginDialogFragment(onLogin, oldData).show(supportFragmentManager, "login")
    }

    override fun serviceLogMessage(level: Int, msg: String) {
        log(level, msg)
    }

    private fun log(priority: Int, msg: String) {
        Log.println(priority, TAG, msg)

        val colour = when(priority) {
            Log.ERROR -> Color.RED
            Log.VERBOSE -> Color.GREEN
            Log.WARN -> Color.MAGENTA
            else -> Color.WHITE
        }

        val visualMsg = SpannableString(msg)
        if (colour != Color.WHITE) {
            visualMsg.setSpan(ForegroundColorSpan(colour), 0, visualMsg.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        runOnUiThread {
            binding.logEdit.text.append(visualMsg)
            binding.logEdit.text.append('\n')
            binding.logEdit.scrollTo(0, binding.logEdit.lineCount)
        }
    }

    override fun traccarApiLogMessage(level: Int, msg: String) {
        log(level, msg)
    }

    override fun traccarSocketConnectedState(isConnected: Boolean) {
        throw Exception("not using traccar socket here but it sends events?")
    }

    override fun traccarPositionUpdate(pos: Position) {
        throw Exception("not using traccar socket here but it sends events?")
    }
}