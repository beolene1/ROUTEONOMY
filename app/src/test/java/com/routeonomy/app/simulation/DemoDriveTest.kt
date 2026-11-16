package com.routeonomy.app.simulation

import com.routeonomy.app.domain.GeoMath
import com.routeonomy.app.domain.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoDriveTest {
    private val a = GeoPoint(37.0, 127.0)
    private val b = GeoPoint(37.1, 127.0)
    private val c = GeoPoint(37.1, 127.1)
    private val drive = DemoDrive(listOf(a, b, c), 40, 4) // 40 min trip at 4x = 10 min

    @Test fun durationScalesWithSpeed() {
        assertEquals(600_000L, drive.durationMillis)
        assertFalse(drive.isFinished(599_999))
        assertTrue(drive.isFinished(600_000))
    }

    @Test fun startsAtOriginAndEndsAtDestination() {
        assertEquals(a, drive.positionAt(0))
        assertEquals(c, drive.positionAt(600_000))
        assertEquals(c, drive.positionAt(10_000_000))
    }

    @Test fun movesAtConstantSpeedAlongTheLegs() {
        val ab = GeoMath.distanceMeters(a, b)
        val total = ab + GeoMath.distanceMeters(b, c)
        val half = drive.positionAt(300_000)
        val travelled = if (half.longitude == 127.0) GeoMath.distanceMeters(a, half) else ab + GeoMath.distanceMeters(b, half)
        assertEquals(total / 2, travelled, 5.0)
    }
}
