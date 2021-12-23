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

    private lateinit var aidlHelper: OsmAndAidlHelper
    private lateinit var osmAndHelper: OsmAndHelper

    private var currentPoints: HashMap<Int, Point> = hashMapOf()

    fun initOsmAndApi(activity: Activity, osmandMissingListener: OsmAndHelper.OsmandEventListener) {
        this.osmAndHelper = OsmAndHelper(activity, REQUEST_OSMAND_API, osmandMissingListener)
        this.aidlHelper = OsmAndAidlHelper(activity.application, osmandMissingListener)
    }

    // remove all devices and clear all points from osmand
    fun clear() {
        setPoints(emptyList())
    }

    fun setPoints(points: List<Point>) {
        currentPoints = hashMapOf()
        aidlHelper.removeMapLayer(MAP_LAYER)

        // now add the new points
        val osmandPoints: MutableList<AMapPoint> = mutableListOf()

        for (point in points) {
            currentPoints[point.ID] = point
            osmandPoints.add(pointToOsmandPoint(point))
        }

        if (osmandPoints.isNotEmpty()) {
            aidlHelper.addMapLayer(MAP_LAYER, "Traccar", 5.5f, osmandPoints, true)
        }
    }

    // we probably need to call this when osmand has restarted and the API has reconnected
    fun refreshPoints() {
        val points: List<Point> = currentPoints.values.toList()
        setPoints(points)
    }

    fun updatePosition(position: Position) {
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