package com.routeonomy.app.simulation

import com.routeonomy.app.domain.GeoMath
import com.routeonomy.app.domain.GeoPoint

/**
 * Demo-only drive along the route points, at constant speed over [tripMinutes] (same model as
 * tools/demo-route.gpx), sped up by [speed]. The screen asks for [positionAt] about once a second
 * and feeds it into the same location path as Pleos FusedLocation. Not vehicle data.
 */
class DemoDrive(private val route: List<GeoPoint>, private val tripMinutes: Int, val speed: Int) {
    private val legs = route.zipWithNext { a, b -> GeoMath.distanceMeters(a, b) }
    private val totalMeters = legs.sum()

    init { require(route.size >= 2 && speed > 0 && tripMinutes > 0) }

    /** Real-time length of the whole drive at this speed. */
    val durationMillis: Long get() = tripMinutes * 60_000L / speed

    fun isFinished(elapsedMillis: Long): Boolean = elapsedMillis >= durationMillis

    /** Point on the route after [elapsedMillis] of real time; the destination once finished. */
    fun positionAt(elapsedMillis: Long): GeoPoint {
        if (totalMeters <= 0.0 || elapsedMillis <= 0) return route.first()
        if (isFinished(elapsedMillis)) return route.last()
        var meters = totalMeters * elapsedMillis / durationMillis
        for ((i, leg) in legs.withIndex()) {
            if (meters <= leg) {
                val t = if (leg == 0.0) 0.0 else meters / leg
                val a = route[i]
                val b = route[i + 1]
                return GeoPoint(a.latitude + (b.latitude - a.latitude) * t, a.longitude + (b.longitude - a.longitude) * t)
            }
            meters -= leg
        }
        return route.last()
    }
}
