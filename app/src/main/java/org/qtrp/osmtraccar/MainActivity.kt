package org.qtrp.osmtraccar

import android.app.ApplicationErrorReport.TYPE_NONE
import android.graphics.Color
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.text.InputType.TYPE_NULL
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.util.Log
import android.view.View
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.qtrp.osmtraccar.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity(), OsmAndHelper.OnOsmandMissingListener {
    private var pointShower = PointShower()
    private var traccarApi = TraccarApi()
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

        binding.logEdit.inputType = TYPE_NONE
        binding.logEdit.setHorizontallyScrolling(true)

        setContentView(view)

        traccarApi.setConnData(Secret.connData)
    }

    fun initOsmAndApi(view: View) {
        pointShower.initOsmAndApi(this, this)
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

    fun getTraccarPoints(view: View) {
        val scope = CoroutineScope(Job() + Dispatchers.IO)

        scope.launch {
            val points = traccarApi.getPoints()
            runOnUiThread {
                logMsg(Log.VERBOSE, "points: $points")
            }
            pointShower.setPoints(points)
        }
    }

    override fun osmandMissing() {
        Toast.makeText(this, "oh no, osmand is missing", Toast.LENGTH_SHORT).show()
    }

    private fun logMsg(priority: Int, msg: String) {
        Log.println(priority, TAG, msg)

        val colour = when(priority) {
            Log.ERROR -> Color.RED
            Log.VERBOSE -> Color.YELLOW
            Log.WARN -> Color.MAGENTA
            else -> Color.WHITE
        }

        val visualMsg = SpannableString(msg)
        if (colour != Color.WHITE) {
            visualMsg.setSpan(ForegroundColorSpan(colour), 0, visualMsg.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        binding.logEdit.append(visualMsg)
        binding.logEdit.scrollTo(1, binding.logEdit.lineCount)
    }
}