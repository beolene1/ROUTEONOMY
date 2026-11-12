package com.routeonomy.app.domain

import kotlin.math.roundToInt

/** A circular area the drive reacts to: a briefing region or the destination. */
data class Geofence(val id: String, val center: GeoPoint, val radiusMeters: Double) {
    fun contains(point: GeoPoint): Boolean = GeoMath.distanceMeters(center, point) <= radiusMeters

    companion object {
        const val DESTINATION_ID = "destination"
        /** Arrival radius around the destination (demo default). */
        const val ARRIVAL_RADIUS_METERS = 300.0

        @JvmStatic fun of(region: Region) = Geofence(region.id, region.center, region.radiusMeters)
        @JvmStatic fun arrival(destination: Place) = Geofence(DESTINATION_ID, destination.location, ARRIVAL_RADIUS_METERS)
    }
}

/**
 * Turns location fixes and proximity-alert events into real entries and exits.
 *
 * An entry counts only after a fix outside that fence was seen in this run, so an alert fired at registration
 * time (already inside, e.g. the emulator left at the last GPX point) does not start a region section or
 * mark arrival. Fixes and alerts may report the same crossing; each crossing is reported once.
 */
class GeofenceGate(private val fences: List<Geofence>) {
    data class Transition(val id: String, val entered: Boolean)

    private val armed = mutableSetOf<String>()
    private val inside = mutableSetOf<String>()

    /** Forgets everything, e.g. when the first real fix replaces the simulated start position. */
    fun reset() { armed.clear(); inside.clear() }

    fun onLocation(point: GeoPoint): List<Transition> {
        val transitions = mutableListOf<Transition>()
        for (fence in fences) {
            if (fence.contains(point)) {
                if (onEnter(fence.id)) transitions += Transition(fence.id, true)
            } else if (onExit(fence.id)) {
                transitions += Transition(fence.id, false)
            }
        }
        return transitions
    }

    /** Proximity ENTER (or a fix inside). True only for a new, armed entry. */
    fun onEnter(id: String): Boolean = id in armed && inside.add(id)

    /** Proximity EXIT (or a fix outside). Arms the fence; true when it ends an accepted entry. */
    fun onExit(id: String): Boolean {
        armed += id
        return inside.remove(id)
    }
}

/**
 * Remaining minutes in proportion to the straight-line distance left to the destination.
 * The Pleos navi's remainTime stays fixed on the AVD, so the demo trip time is scaled by distance instead.
 */
object RemainingTimeEstimator {
    @JvmStatic
    fun estimate(trip: TripContext, location: GeoPoint): Int {
        val total = GeoMath.distanceMeters(trip.current.location, trip.destination.location)
        if (total <= 0.0) return 1
        val left = GeoMath.distanceMeters(location, trip.destination.location)
        return (trip.remainingMinutes * left / total).roundToInt().coerceAtLeast(1)
    }
}
