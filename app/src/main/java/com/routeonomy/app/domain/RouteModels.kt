package com.routeonomy.app.domain

/** WGS84 coordinate in degrees. */
data class GeoPoint(val latitude: Double, val longitude: Double)

/** A candidate area whose content can be briefed after the vehicle enters it. */
data class Region(val id: String, val name: String, val center: GeoPoint, val radiusMeters: Double)

/** A named place such as the trip origin or destination. */
data class Place(val name: String, val location: GeoPoint)

/**
 * Route-judgment input: where the vehicle is, where it is going, and the remaining time to the destination.
 * MVP fills this from local demo JSON; a Pleos adapter can fill it from location and NaviHelper data later.
 */
data class TripContext(val current: Place, val destination: Place, val remainingMinutes: Int)
