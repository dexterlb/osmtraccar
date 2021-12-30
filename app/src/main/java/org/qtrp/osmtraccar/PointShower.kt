package org.qtrp.osmtraccar

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.net.Uri
import android.util.Log
import net.osmand.aidlapi.map.ALatLon
import net.osmand.aidlapi.maplayer.point.AMapPoint


class PointShowerException(message:String) : Exception(message) {
}

class PointShower() {
    companion object {
        const val REQUEST_OSMAND_API = 1001
        const val MAP_LAYER = "traccar_items"
    }

    private lateinit var aidlHelper: OsmAndAidlHelper
    private lateinit var osmAndHelper: OsmAndHelper
    private lateinit var osmAndPackage: String
    private lateinit var log: (Int, String) -> Unit

    private lateinit var osmandInitActivity: Activity

    private var mLastPoint: Point? = null

    fun setOsmandInitActivity(activity: Activity) {
        osmandInitActivity = activity
    }

    fun initOsmAndApi(eventListener: OsmAndHelper.OsmandEventListener, osmandPackage: String) {
        log = eventListener::osmandLog

        this.osmAndPackage = osmandPackage
        osmAndHelper = OsmAndHelper(osmandInitActivity, REQUEST_OSMAND_API, eventListener)
        aidlHelper = OsmAndAidlHelper(osmandInitActivity.application, eventListener, osmandPackage)
    }

    // remove all devices and clear all points from osmand
    fun clear() {
        if (!this::aidlHelper.isInitialized) {
            return
        }
        setPoints(emptyList())
    }

    fun setPoints(points: List<Point>) {
        aidlHelper.removeMapLayer(MAP_LAYER)

        // now add the new points
        val osmandPoints: MutableList<AMapPoint> = mutableListOf()

        for (point in points) {
            updateLastPoint(point)
            osmandPoints.add(pointToOsmandPoint(point))
        }

        if (osmandPoints.isNotEmpty()) {
            aidlHelper.addMapLayer(MAP_LAYER, "Traccar", 5.5f, osmandPoints, true)
        }
    }

    fun updatePoint(point: Point) {
        val pointID = point.ID
        val pointName = point.name

        log(Log.VERBOSE, "updating point $pointID ($pointName)")
        updateLastPoint(point)

        val osmandPoint = pointToOsmandPoint(point)
        aidlHelper.updateMapPoint(MAP_LAYER, osmandPoint.id, osmandPoint.shortName, osmandPoint.fullName, osmandPoint.typeName, osmandPoint.color, osmandPoint.location, osmandPoint.details, osmandPoint.params)
    }

    fun showOsmAnd(context: Context) {
        val launchIntent = context.getPackageManager().getLaunchIntentForPackage(osmAndPackage)
        if (launchIntent != null) {
            context.startActivity(launchIntent)
        }

        val lastPoint = mLastPoint
        if (lastPoint != null) {
            showPoint(lastPoint)
        }
    }

    fun showPoint(point: Point) {
        val osmandPoint = pointToOsmandPoint(point)
        aidlHelper.showMapPoint(MAP_LAYER, osmandPoint.id, osmandPoint.shortName, osmandPoint.fullName, osmandPoint.typeName, osmandPoint.color, osmandPoint.location, osmandPoint.details, osmandPoint.params)
    }

    private fun updateLastPoint(point: Point) {
        val lastPoint = mLastPoint
        if (lastPoint == null || point.time > lastPoint.time) {
            mLastPoint = point
        }
    }

    private fun pointToOsmandPoint(point: Point): AMapPoint {
        val colour = when (point.status) {
            Point.Status.ONLINE -> Color.GREEN
            else                -> Color.RED
        }


        val params = mutableMapOf(
            AMapPoint.POINT_IMAGE_URI_PARAM to makeImageUri(point).toString(),
            AMapPoint.POINT_STALE_LOC_PARAM to point.isStale().toString()
        )

        return AMapPoint(
            String.format("point_%d", point.ID),
            point.name,
            point.name,
            point.type,
            MAP_LAYER,
            colour,
            ALatLon(point.lat, point.lon),
            emptyList(),
            params
        )
    }

    private fun makeImageUri(point: Point): Uri {
//        if (point.imageURL != null) {
            val id = if (point.isStale()) {
                R.drawable.img_point_placeholder_stale
            } else {
                R.drawable.img_point_placeholder_active
            }

            return AndroidUtils.resourceToUri(osmandInitActivity.application, id)
//        } else {
//            return AndroidUtils.getUriForFile(app, File(photoPath))
//        }
    }
}