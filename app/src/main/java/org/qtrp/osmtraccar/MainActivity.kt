package org.qtrp.osmtraccar

import android.graphics.Color
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.PersistableBundle
import android.view.View
import android.widget.Toast
import net.osmand.aidlapi.map.ALatLon

class MainActivity : AppCompatActivity(), OsmAndHelper.OnOsmandMissingListener {
    private var pointShower: PointShower = PointShower()
    private var testPos = Position(
        42,
        42.67440,
        23.33044,
        "today"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
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

    override fun osmandMissing() {
        Toast.makeText(this, "oh no, osmand is missing", Toast.LENGTH_SHORT).show()
    }


}