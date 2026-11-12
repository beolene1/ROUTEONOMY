package com.routeonomy.app.domain

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Great-circle distance helpers. Pleos exposes no route polyline, so route judgments use coordinates only. */
object GeoMath {
    private const val EARTH_RADIUS_METERS = 6_371_000.0

    @JvmStatic
    fun distanceMeters(a: GeoPoint, b: GeoPoint): Double {
        val lat1 = Math.toRadians(a.latitude)
        val lat2 = Math.toRadians(b.latitude)
        val dLat = lat2 - lat1
        val dLng = Math.toRadians(b.longitude - a.longitude)
        val h = sin(dLat / 2).pow(2) + cos(lat1) * cos(lat2) * sin(dLng / 2).pow(2)
        return 2 * EARTH_RADIUS_METERS * asin(sqrt(h.coerceIn(0.0, 1.0)))
    }
}

fun Region.contains(point: GeoPoint): Boolean = GeoMath.distanceMeters(center, point) <= radiusMeters

/** A region the trip is predicted to pass, with a rough time until entry. */
data class RegionPrediction(
    val region: Region,
    val detourRatio: Double,
    val estimatedMinutesToRegion: Int,
)

/**
 * Predicts which candidate region the trip will pass, from straight-line distances only.
 * The prediction is a hint for display and A-section sizing; the actual switch happens on region entry.
 */
object RegionPredictor {
    const val MAX_DETOUR_RATIO = 1.15
    /** Ratios this close count as a tie, so a region with content can win. */
    private const val TIE_RATIO = 0.02

    @JvmStatic
    @JvmOverloads
    fun predict(
        current: GeoPoint,
        destination: GeoPoint,
        remainingMinutes: Int,
        regions: List<Region>,
        regionsWithContent: Set<String> = emptySet(),
        maxDetourRatio: Double = MAX_DETOUR_RATIO,
    ): RegionPrediction? {
        val direct = GeoMath.distanceMeters(current, destination)
        if (direct <= 0.0) return null
        val ranked = regions
            .filter { !it.contains(current) && !it.contains(destination) }
            .map { region ->
                val toRegion = GeoMath.distanceMeters(current, region.center)
                val afterRegion = GeoMath.distanceMeters(region.center, destination)
                val minutes = (remainingMinutes.coerceAtLeast(0) * toRegion / (toRegion + afterRegion)).toInt()
                RegionPrediction(region, (toRegion + afterRegion) / direct, minutes)
            }
            .filter { it.detourRatio <= maxDetourRatio }
            .sortedBy { it.detourRatio }
        val best = ranked.firstOrNull() ?: return null
        return ranked
            .takeWhile { it.detourRatio - best.detourRatio <= TIE_RATIO }
            .firstOrNull { it.region.id in regionsWithContent }
            ?: best
    }

    /** First candidate whose radius contains [point], nearest center first. */
    @JvmStatic
    fun regionAt(point: GeoPoint, regions: List<Region>): Region? =
        regions.filter { it.contains(point) }.minByOrNull { GeoMath.distanceMeters(it.center, point) }
}

/**
 * Tracks the single A→B region switch of one drive (MVP: one switch per drive).
 * Entry marks a pending switch; the player applies it after the current segment finishes.
 */
class RegionTransition {
    private val ignoredRegionIds = mutableSetOf<String>()
    private var enteredRegionId: String? = null
    private var pending = false

    /** Regions the drive started inside never trigger a switch. */
    fun ignoreRegionsAtStart(regionIds: Collection<String>) { ignoredRegionIds += regionIds }

    /** Returns true only for the first accepted entry of this drive. */
    fun onRegionEntered(regionId: String): Boolean {
        if (enteredRegionId != null || regionId in ignoredRegionIds) return false
        enteredRegionId = regionId
        pending = true
        return true
    }

    fun enteredRegionId(): String? = enteredRegionId
    fun hasPendingSwitch(): Boolean = pending

    /** Returns the region to switch to once, then clears the pending flag. */
    fun consumePendingSwitch(): String? {
        if (!pending) return null
        pending = false
        return enteredRegionId
    }
}
