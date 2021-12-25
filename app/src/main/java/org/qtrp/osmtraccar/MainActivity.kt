package org.qtrp.osmtraccar

import android.app.ApplicationErrorReport.TYPE_NONE
import android.graphics.Color
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
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
class MainActivity : AppCompatActivity(), OsmAndHelper.OsmandEventListener {
    private val pointShower = PointShower()
    private lateinit var traccarApi: TraccarApi
    private val TAG = "main"
    private lateinit var binding: ActivityMainBinding

    private var testPos = Position(
        42,
        42.67440,
        23.33044,
        "today"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root

        binding.logEdit.setHorizontallyScrolling(true)

        setContentView(view)

        traccarApi = TraccarApi(this, ::logMsg)

        pointShower.initOsmAndApi(this, this)
    }

    fun traccarLogin(view: View) {
        val scope = CoroutineScope(Job() + Dispatchers.IO)

        scope.launch {
            traccarApi.login(Secret.connData.url, Secret.connData.user, Secret.connData.pass)
        }
    }

    fun displayTestPoint(view: View) {
        val testPoint = Point(
            ID = testPos.pointID,
            name = "fmi",
            position = testPos,
            status = PointStatus.ONLINE,
            type = "building",
            imageURL = null,
        )
        pointShower.setPoints(listOf(testPoint))
    }

    fun moveTestPoint(view: View) {
        testPos = testPos.copy(lon = testPos.lon + 0.001)
        pointShower.updatePosition(testPos)
    }

    fun refreshPoints(view: View) {
        pointShower.refreshPoints()
    }

    fun clearPoints(view: View) {
        pointShower.clear()
    }

    fun getTraccarPoints(view: View) {
        val scope = CoroutineScope(Job() + Dispatchers.IO)

        scope.launch {
            val points = traccarApi.getPoints()
            logMsg(Log.VERBOSE, "points: $points")
            pointShower.setPoints(points)

            traccarApi.subscribeToPositionUpdates {
                pointShower.updatePosition(it)
            }
        }
    }

    override fun osmandMissing() {
        logMsg(Log.ERROR, "oh no, OsmAnd seems to be missing!")
    }

    override fun osmandLog(priority: Int, msg: String) {
        logMsg(priority, msg)
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