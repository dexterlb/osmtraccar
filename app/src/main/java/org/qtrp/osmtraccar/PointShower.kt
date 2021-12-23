package org.qtrp.osmtraccar

import android.app.Activity
import android.graphics.Color
import net.osmand.aidlapi.map.ALatLon
import net.osmand.aidlapi.maplayer.point.AMapPoint

class PointShowerException(message:String) : Exception(message) {
}

class PointShower() {

    companion object {
        const val REQUEST_OSMAND_API = 1001
        const val MAP_LAYER = "traccar_items"
    }

    private var mAidlHelper: OsmAndAidlHelper? = null
    private var mOsmAndHelper: OsmAndHelper? = null

    private var currentPoints: HashMap<Int, Point> = hashMapOf()

    fun initOsmAndApi(activity: Activity, osmandMissingListener: OsmAndHelper.OnOsmandMissingListener) {
        this.mOsmAndHelper = OsmAndHelper(activity, REQUEST_OSMAND_API, osmandMissingListener)
        if (this.mOsmAndHelper == null) {
            throw PointShowerException("oh no, cannot create osmand helper")
        }
        this.mAidlHelper = OsmAndAidlHelper(activity.application, osmandMissingListener)
        if (this.mAidlHelper == null) {
            throw PointShowerException("oh no, cannot create aidl helper")
        }
    }

    // remove all devices and clear all points from osmand
    fun clear() {
        setPoints(emptyList())
    }

    fun setPoints(points: List<Point>) {
        var aidlHelper = this.mAidlHelper;
        var osmAndHelper = this.mOsmAndHelper;
        if (aidlHelper == null || osmAndHelper == null) {
            throw PointShowerException("osmand helper not initialised")
        }

        // for some reason removeMapLayer doesn't properly remove all points, so we remove them manually
        // for ((_, point) in currentPoints) {
        //    val osmandPoint = pointToOsmandPoint(point)
        //    aidlHelper.removeMapPoint(MAP_LAYER, osmandPoint.id)
        // }

        currentPoints = hashMapOf()
        aidlHelper.removeMapLayer(MAP_LAYER)

        // now add the new points
        var osmandPoints: MutableList<AMapPoint> = mutableListOf()

        for (point in points) {
            currentPoints[point.ID] = point
            osmandPoints.add(pointToOsmandPoint(point))
        }

        if (!osmandPoints.isEmpty()) {
            aidlHelper.addMapLayer(MAP_LAYER, "Traccar", 5.5f, osmandPoints, true)
        }
    }

    // we probably need to call this when osmand has restarted and the API has reconnected
    fun refreshPoints() {
        val points: List<Point> = currentPoints.values.toList()
        setPoints(points)
    }

    fun updatePosition(position: Position) {
        var aidlHelper = this.mAidlHelper;
        var osmAndHelper = this.mOsmAndHelper;
        if (aidlHelper == null || osmAndHelper == null) {
            throw PointShowerException("osmand helper not initialised")
        }


        val currentPoint = currentPoints[position.pointID]
        if (currentPoint == null) {
            throw PointShowerException("trying to update non-existant point")
        }

        val point = currentPoint.copy(position = position)
        currentPoints[position.pointID] = point

        val osmandPoint = pointToOsmandPoint(point)

        aidlHelper.updateMapPoint(MAP_LAYER, osmandPoint.id, osmandPoint.shortName, osmandPoint.fullName, osmandPoint.typeName, osmandPoint.color, osmandPoint.location, osmandPoint.details, osmandPoint.params)
    }

    private fun pointToOsmandPoint(point: Point): AMapPoint {
        val pos = point.position

        val colour = when (point.status) {
            PointStatus.ONLINE -> Color.GREEN
            else               -> Color.RED
        }

        return AMapPoint(
            String.format("point_%d", point.ID),
            point.name,
            point.name,
            point.type,
            MAP_LAYER,
            colour,
            ALatLon(pos.lat, pos.lon),
            emptyList(),
            null
        )
    }

}