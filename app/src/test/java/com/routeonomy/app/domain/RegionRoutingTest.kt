package com.routeonomy.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RegionRoutingTest {
    private val seoulStation = GeoPoint(37.55594, 126.97233)
    private val giheung = GeoPoint(37.27566, 127.11595)
    private val pangyo = Region("pangyo", "판교", GeoPoint(37.40159, 127.10842), 1650.0)
    private val dongtan = Region("dongtan", "동탄", GeoPoint(37.19934, 127.09702), 2000.0)
    private val namyangju = Region("namyangju", "남양주", GeoPoint(37.65329, 127.24439), 1500.0)
    private val hyehwa = Region("hyehwa", "혜화역", GeoPoint(37.58216, 127.00189), 800.0)
    private val candidates = listOf(hyehwa, namyangju, dongtan, pangyo)

    @Test fun distanceMatchesKnownScale() {
        val km = GeoMath.distanceMeters(seoulStation, giheung) / 1000
        assertTrue("was $km", km in 33.0..34.5)
        assertEquals(0.0, GeoMath.distanceMeters(giheung, giheung), 1e-6)
    }

    @Test fun radiusBoundaryIsInclusive() {
        val region = Region("r", "r", GeoPoint(37.0, 127.0), 1000.0)
        val north = { meters: Double -> GeoPoint(37.0 + meters / 111_195.0, 127.0) }
        assertTrue(region.contains(north(990.0)))
        assertFalse(region.contains(north(1010.0)))
    }

    @Test fun demoTripPredictsPangyoAmongCandidates() {
        val prediction = RegionPredictor.predict(seoulStation, giheung, 40, candidates)!!
        assertEquals("pangyo", prediction.region.id)
        assertEquals(1.039, prediction.detourRatio, 0.01)
        // 20.9 km of 34.9 km straight-line path → about 60% of 40 minutes.
        assertTrue("was ${prediction.estimatedMinutesToRegion}", prediction.estimatedMinutesToRegion in 22..26)
    }

    @Test fun hyehwaJustAboveThresholdIsExcluded() {
        val prediction = RegionPredictor.predict(seoulStation, giheung, 40, listOf(hyehwa, dongtan, namyangju))
        assertNull(prediction)
        assertEquals("hyehwa", RegionPredictor.predict(seoulStation, giheung, 40, listOf(hyehwa), maxDetourRatio = 1.2)!!.region.id)
    }

    @Test fun regionsContainingStartOrDestinationAreNotPredicted() {
        val atPangyo = pangyo.center
        assertNull(RegionPredictor.predict(atPangyo, giheung, 20, listOf(pangyo)))
        assertNull(RegionPredictor.predict(seoulStation, atPangyo, 20, listOf(pangyo)))
    }

    @Test fun nearTieFavorsRegionWithContent() {
        val twin = Region("twin", "twin", GeoPoint(37.4020, 127.1090), 500.0)
        val withoutPreference = RegionPredictor.predict(seoulStation, giheung, 40, listOf(pangyo, twin))!!
        val withContent = RegionPredictor.predict(seoulStation, giheung, 40, listOf(pangyo, twin), setOf("pangyo"))!!
        assertEquals("pangyo", withContent.region.id)
        assertTrue(withoutPreference.region.id in setOf("pangyo", "twin"))
    }

    @Test fun regionAtReturnsContainingRegion() {
        assertEquals("pangyo", RegionPredictor.regionAt(GeoPoint(37.3977, 127.1006), candidates)?.id)
        assertNull(RegionPredictor.regionAt(seoulStation, candidates))
    }

    @Test fun firstEntrySwitchesOnceAndLaterEntriesAreIgnored() {
        val transition = RegionTransition()
        assertTrue(transition.onRegionEntered("pangyo"))
        assertFalse(transition.onRegionEntered("pangyo"))
        assertFalse(transition.onRegionEntered("dongtan"))
        assertEquals("pangyo", transition.enteredRegionId())
        assertEquals("pangyo", transition.consumePendingSwitch())
        assertNull(transition.consumePendingSwitch())
        assertFalse(transition.hasPendingSwitch())
    }

    @Test fun regionAtStartNeverSwitches() {
        val transition = RegionTransition()
        transition.ignoreRegionsAtStart(listOf("pangyo"))
        assertFalse(transition.onRegionEntered("pangyo"))
        assertTrue(transition.onRegionEntered("dongtan"))
    }
}
