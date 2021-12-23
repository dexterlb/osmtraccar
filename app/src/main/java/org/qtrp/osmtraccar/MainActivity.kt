package org.qtrp.osmtraccar

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
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
            logMsg(Log.VERBOSE, "points: $points")
            pointShower.setPoints(points)
        }
    }

    override fun osmandMissing() {
        Toast.makeText(this, "oh no, osmand is missing", Toast.LENGTH_SHORT).show()
    }

    private fun logMsg(priority: Int, msg: String) {
        Log.println(priority, TAG, msg)
        binding.logEdit.setText(binding.logEdit.text.toString() + "\n" + msg)
        binding.logEdit.scrollTo(0, binding.logEdit.lineCount)
    }
}