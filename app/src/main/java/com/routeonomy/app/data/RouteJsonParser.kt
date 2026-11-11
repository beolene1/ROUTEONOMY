package com.routeonomy.app.data

import com.routeonomy.app.domain.GeoPoint
import com.routeonomy.app.domain.Place
import com.routeonomy.app.domain.Region
import com.routeonomy.app.domain.TripContext
import org.json.JSONArray
import org.json.JSONObject

/** Parses region candidates and the demo trip from local JSON text. */
object RouteJsonParser {
    fun regions(json: String): List<Region> {
        val rows = JSONArray(json)
        val regions = (0 until rows.length()).map { index ->
            val row = rows.getJSONObject(index)
            val radius = row.getDouble("radiusMeters")
            require(radius > 0) { "radiusMeters must be positive: ${row.getString("id")}" }
            Region(row.getString("id"), row.getString("name"), row.point(), radius)
        }
        val duplicate = regions.groupBy { it.id }.filterValues { it.size > 1 }.keys
        require(duplicate.isEmpty()) { "duplicate region id: $duplicate" }
        return regions
    }

    fun trip(json: String): TripContext {
        val root = JSONObject(json)
        val minutes = root.getInt("remainingMinutes")
        require(minutes > 0) { "remainingMinutes must be positive" }
        return TripContext(root.getJSONObject("origin").place(), root.getJSONObject("destination").place(), minutes)
    }

    /** Demo-only positions the simulation panel can jump to, in route order. */
    fun simulationStops(json: String): List<Place> {
        val rows = JSONObject(json).optJSONArray("simulationStops") ?: return emptyList()
        return (0 until rows.length()).map { index -> rows.getJSONObject(index).place() }
    }

    private fun JSONObject.place() = Place(getString("name"), point())

    private fun JSONObject.point(): GeoPoint {
        val point = GeoPoint(getDouble("latitude"), getDouble("longitude"))
        require(point.latitude in -90.0..90.0 && point.longitude in -180.0..180.0) { "invalid coordinate: $point" }
        return point
    }
}
