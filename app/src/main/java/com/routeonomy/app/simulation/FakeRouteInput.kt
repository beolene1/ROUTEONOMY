package com.routeonomy.app.simulation

import com.routeonomy.app.domain.GeoPoint
import com.routeonomy.app.domain.Place
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

    fun remainingMinutes(): Int = RemainingTimeEstimator.estimate(trip, location)
}
