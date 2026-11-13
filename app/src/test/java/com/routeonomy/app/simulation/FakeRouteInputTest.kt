package com.routeonomy.app.simulation

import com.routeonomy.app.domain.GeoPoint
import com.routeonomy.app.domain.Place
import com.routeonomy.app.domain.Region
import com.routeonomy.app.domain.TripContext
import com.routeonomy.app.domain.contains
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeRouteInputTest {
    private val trip = TripContext(
        Place("서울역", GeoPoint(37.55594, 126.97233)),
        Place("기흥역", GeoPoint(37.27566, 127.11595)),
        40,
    )
    private val beforePangyo = Place("판교 진입 직전", GeoPoint(37.41632, 127.09543))
    private val nearGiheung = Place("기흥역 근처", GeoPoint(37.27746, 127.11584))
    private val pangyo = Region("pangyo", "판교", GeoPoint(37.40159, 127.10842), 1650.0)

    @Test fun startsAtOriginWithFullTime() {
        val input = FakeRouteInput(trip, listOf(beforePangyo, nearGiheung))
        assertEquals(40, input.remainingMinutes())
    }

    @Test fun demoStopsShrinkRemainingTime() {
        val input = FakeRouteInput(trip, listOf(beforePangyo, nearGiheung))
        val atBefore = input.moveTo(beforePangyo)
        assertFalse(pangyo.contains(input.location))
        val atEnd = input.moveTo(nearGiheung)
        assertTrue("$atBefore > $atEnd", atBefore > atEnd)
        assertEquals(1, atEnd)
    }
}
