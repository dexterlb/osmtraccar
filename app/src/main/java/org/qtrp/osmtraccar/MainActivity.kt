package org.qtrp.osmtraccar

import android.graphics.Color
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
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
class MainActivity : AppCompatActivity(), OsmAndHelper.OsmandEventListener, TraccarEventListener {
    private val pointShower = PointShower()
    private lateinit var traccarApi: TraccarApi
    private val TAG = "main"
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root

        binding.logEdit.setHorizontallyScrolling(true)

        setContentView(view)

        traccarApi = TraccarApi(this, this)

        pointShower.initOsmAndApi(this, this)
        pointShower.clear()
    }

    fun traccarLogin(view: View) {
        val onLogin: (TraccarConnData) -> Unit = { connData ->
            val scope = CoroutineScope(Job() + Dispatchers.IO)

            scope.launch {
                try {
                    if (connData.pass == "") {
                        logMsg(Log.WARN, "trying to login with empty password; this is probably not what you want.")
                    }
                    traccarApi.login(connData.url, connData.email, connData.pass)
                } catch(e: Exception) {
                    logMsg(Log.ERROR, "unable to login: $e\nyou entered the correct URL, email and password, right?")
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

    fun traccarConnect(view: View) {
        val scope = CoroutineScope(Job() + Dispatchers.IO)

        scope.launch {
            val points = try {
                traccarApi.getPoints()
            } catch (e: Exception) {
                logMsg(Log.ERROR, "could not get data from traccar: $e")
                traccarSocketConnectedState(false)
                return@launch
            }
            logMsg(Log.VERBOSE, "points: $points")
            pointShower.setPoints(points)

            traccarApi.subscribeToPositionUpdates()
        }

        binding.buttonTraccarConnect.isEnabled = false
    }

    fun traccarDisconnect(view: View) {
        traccarApi.unsubscribePositionUpdates()
    }

    override fun osmandMissing() {
        logMsg(Log.ERROR, "oh no, OsmAnd seems to be missing!")
    }

    override fun osmandLog(priority: Int, msg: String) {
        logMsg(priority, "[osmand] $msg")
    }

    override fun traccarSocketConnectedState(isConnected: Boolean) {
        runOnUiThread {
            if (isConnected) {
                binding.buttonTraccarDisconnect.isEnabled = true
                binding.buttonTraccarConnect.isEnabled = false
            } else {
                binding.buttonTraccarDisconnect.isEnabled = false
                binding.buttonTraccarConnect.isEnabled = true
                pointShower.clear()
            }
        }
    }

    override fun traccarPositionUpdate(pos: Position) {
        pointShower.updatePosition(pos)
    }

    override fun traccarApiLogMessage(level: Int, msg: String) {
        logMsg(level, "[traccar] $msg")
    }

    override fun onDestroy() {
        pointShower.clear()
        traccarApi.unsubscribePositionUpdates()
        super.onDestroy()
    }

    private fun logMsg(priority: Int, msg: String) {
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
}