package com.routeonomy.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationTrackingTest {
    private val seoulStation = GeoPoint(37.55594, 126.97233)
    private val giheung = Place("기흥역", GeoPoint(37.27566, 127.11595))
    private val pangyo = Region("pangyo", "판교", GeoPoint(37.40159, 127.10842), 1650.0)
    private val beforePangyo = GeoPoint(37.41632, 127.09543)
    private val insidePangyo = GeoPoint(37.40300, 127.10700)
    private val nearGiheung200m = GeoPoint(37.27746, 127.11584)
    private val fences = listOf(Geofence.of(pangyo), Geofence.arrival(giheung))
    private val trip = TripContext(Place("서울역", seoulStation), giheung, 40)

    @Test fun driveThroughRegionThenArrive() {
        val gate = GeofenceGate(fences)
        assertTrue(gate.onLocation(seoulStation).isEmpty())
        assertTrue(gate.onLocation(beforePangyo).isEmpty())
        assertEquals(listOf(GeofenceGate.Transition("pangyo", true)), gate.onLocation(insidePangyo))
        assertTrue("staying inside reports nothing", gate.onLocation(insidePangyo).isEmpty())
        val atEnd = gate.onLocation(nearGiheung200m)
        assertEquals(listOf(GeofenceGate.Transition("pangyo", false), GeofenceGate.Transition(Geofence.DESTINATION_ID, true)), atEnd)
    }

    @Test fun proximityAlertAndFixReportOneCrossingOnce() {
        val gate = GeofenceGate(fences)
        gate.onLocation(beforePangyo)
        assertTrue(gate.onEnter("pangyo"))
        assertTrue(gate.onLocation(insidePangyo).isEmpty())
        assertFalse(gate.onEnter("pangyo"))
        assertTrue(gate.onExit("pangyo"))
        assertFalse(gate.onExit("pangyo"))
    }

    @Test fun alertWhileAlreadyInsideAtStartIsIgnoredUntilLeftOnce() {
        val gate = GeofenceGate(fences)
        // Emulator left at the destination by the previous run: registration-time ENTER and fixes inside.
        assertFalse(gate.onEnter(Geofence.DESTINATION_ID))
        assertTrue(gate.onLocation(nearGiheung200m).isEmpty())
        // The next run starts from Seoul Station, then arrives again.
        assertTrue(gate.onLocation(seoulStation).isEmpty())
        assertEquals(listOf(GeofenceGate.Transition(Geofence.DESTINATION_ID, true)), gate.onLocation(nearGiheung200m))
    }

    @Test fun resetForgetsTheSimulatedStart() {
        val gate = GeofenceGate(fences)
        gate.onLocation(seoulStation) // simulated start arms every fence
        gate.reset()                  // first real fix arrives inside Pangyo
        assertTrue(gate.onLocation(insidePangyo).isEmpty())
    }

    @Test fun remainingTimeScalesWithDistanceLeft() {
        assertEquals(40, RemainingTimeEstimator.estimate(trip, seoulStation))
        assertEquals(1, RemainingTimeEstimator.estimate(trip, giheung.location))
        val atPangyo = RemainingTimeEstimator.estimate(trip, insidePangyo)
        assertTrue("$atPangyo", atPangyo in 10..20)
    }
}
