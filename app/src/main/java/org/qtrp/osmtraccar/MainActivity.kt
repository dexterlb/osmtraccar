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

class MainActivity : AppCompatActivity(), OsmAndHelper.OnOsmandMissingListener {
    private var pointShower = PointShower()
    private var traccarApi = TraccarApi()
    private val TAG = "main"

    private var testPos = Position(
        42,
        42.67440,
        23.33044,
        "today"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        traccarApi.setConnData(Secret.connData)
    }

    fun initOsmAndApi(view: View) {
        pointShower.initOsmAndApi(this, this)
    }

    fun displayTestPoint(view: View) {
        val testPoint = Point(
            testPos.pointID,
            "fmi",
            testPos,
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
            Log.i(TAG, "data: " + points[0].name)
        }
    }

    override fun osmandMissing() {
        Toast.makeText(this, "oh no, osmand is missing", Toast.LENGTH_SHORT).show()
    }


}