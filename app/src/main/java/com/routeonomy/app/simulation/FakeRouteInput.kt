package com.routeonomy.app.simulation

import com.routeonomy.app.domain.GeoMath
import com.routeonomy.app.domain.GeoPoint
import com.routeonomy.app.domain.Place
import com.routeonomy.app.domain.Region
import com.routeonomy.app.domain.RemainingTimeEstimator
import com.routeonomy.app.domain.TripContext

/**
 * Demo-only vehicle position: jumps between fixed stops. Backup for when Pleos FusedLocation is not received.
 */
class FakeRouteInput(private val trip: TripContext, private val stops: List<Place>) {
    var location: GeoPoint = trip.current.location
        private set

    fun stops(): List<Place> = stops

    /** Moves to [place] and returns the estimated remaining minutes. */
    fun moveTo(place: Place): Int {
        location = place.location
        return remainingMinutes()
    }

    /** Moves 100 m inside [region] on the way toward its center and returns the estimated remaining minutes. */
    fun enterRegion(region: Region): Int {
        val toCenter = GeoMath.distanceMeters(location, region.center)
        val insideDistance = (region.radiusMeters - 100.0).coerceAtLeast(0.0)
        if (toCenter > insideDistance) {
            val fraction = (toCenter - insideDistance) / toCenter
            location = GeoPoint(
                location.latitude + (region.center.latitude - location.latitude) * fraction,
                location.longitude + (region.center.longitude - location.longitude) * fraction,
            )
        }
        return remainingMinutes()
    }

    fun remainingMinutes(): Int = RemainingTimeEstimator.estimate(trip, location)
}
