package com.routeonomy.app.data

import com.routeonomy.app.domain.BriefingSegment
import com.routeonomy.app.domain.GeoMath
import com.routeonomy.app.domain.GeoPoint
import com.routeonomy.app.domain.Geofence
import com.routeonomy.app.domain.GeofenceGate
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Checks that the bundled demo JSON parses and is internally consistent. */
class BundledContentTest {
    /** Works whether tests run from the app module (Gradle) or the project root (some IDE configs). */
    private fun asset(name: String): String =
        listOf("src/main/assets", "app/src/main/assets").map { File(it, name) }.first { it.exists() }.readText()
    private val segments = SegmentJsonParser.segments(asset("segments.json"))
    private val regions = RouteJsonParser.regions(asset("regions.json"))
    private val trip = RouteJsonParser.trip(asset("demo_trip.json"))
    private val quizzes = QuizJsonParser.questions(asset("quizzes.json"))

    @Test fun regionCandidatesIncludePangyoAndOthers() {
        assertEquals(listOf("pangyo", "dongtan", "namyangju", "hyehwa"), regions.map { it.id })
        assertTrue(regions.all { it.radiusMeters > 0 })
    }

    @Test fun demoTripIsSeoulStationToGiheung() {
        assertEquals("서울역", trip.current.name)
        assertEquals("기흥역", trip.destination.name)
        assertTrue(trip.remainingMinutes > 0)
        val stops = RouteJsonParser.simulationStops(asset("demo_trip.json"))
        assertEquals(listOf("판교 진입 직전", "기흥역 근처"), stops.map { it.name })
        // The last stop doubles as the simulated arrival, so it must sit inside the arrival radius.
        assertTrue(Geofence.arrival(trip.destination).contains(stops.last().location))
        assertTrue(!Geofence.arrival(trip.destination).contains(stops.first().location))
    }

    @Test fun demoRouteRunsFromOriginToDestinationThroughPangyoOnly() {
        val route = RouteJsonParser.routePoints(asset("demo_trip.json"))
        assertEquals(trip.current.location, route.first())
        assertEquals(trip.destination.location, route.last())
        val fences = regions.map(Geofence::of)
        val gate = GeofenceGate(fences)
        // Walk the route in ~100 m steps, as GPX playback would, and collect region entries.
        val entered = mutableListOf<String>()
        route.zipWithNext().forEach { (a, b) ->
            val steps = (GeoMath.distanceMeters(a, b) / 100).toInt().coerceAtLeast(1)
            for (i in 0..steps) {
                val t = i.toDouble() / steps
                val p = GeoPoint(a.latitude + (b.latitude - a.latitude) * t, a.longitude + (b.longitude - a.longitude) * t)
                gate.onLocation(p).filter { it.entered }.forEach { entered += it.id }
            }
        }
        assertEquals(listOf("pangyo"), entered)
        // The general briefing needs time before Pangyo: entry should be well into the route.
        val pangyo = regions.first { it.id == "pangyo" }
        assertTrue(GeoMath.distanceMeters(route.first(), pangyo.center) > 15_000)
    }

    @Test fun segmentsAreUniqueAndTimed() {
        assertEquals(segments.size, segments.map { it.id }.toSet().size)
        assertTrue(segments.all { it.durationSec > 0 && it.script.isNotBlank() })
        assertEquals(1, segments.count { it.type == BriefingSegment.Type.WRAPUP })
    }

    @Test fun regionSegmentsPointToKnownRegionsAndPangyoHasContent() {
        val regionSegments = segments.filter { it.type == BriefingSegment.Type.REGION }
        val ids = regions.map { it.id }.toSet()
        assertTrue(regionSegments.all { it.regionId in ids })
        assertTrue(regionSegments.count { it.regionId == "pangyo" } >= 3)
        assertTrue(segments.filter { it.type != BriefingSegment.Type.REGION }.all { it.regionId == null })
    }

    @Test fun teamScriptIsFirstMainSegment() {
        val first = segments.first { it.type == BriefingSegment.Type.MAIN }
        assertEquals("AI 시대, 반도체가 중요한 이유", first.topic)
    }

    @Test fun everyTopicHasACategoryAndRegionSegmentsAreRegional() {
        assertTrue(segments.filter { it.type != BriefingSegment.Type.WRAPUP }.all { it.category != null })
        assertTrue(segments.filter { it.type == BriefingSegment.Type.REGION }
            .all { it.category == BriefingSegment.Category.REGION })
    }

    @Test fun livingEconomyAndHousingScriptsAreSourced() {
        val living = segments.filter { it.category == BriefingSegment.Category.LIVING }
        assertEquals(listOf("main-rates", "main-fx", "main-ppi"), living.map { it.id })
        assertTrue(living.all { it.type == BriefingSegment.Type.MAIN && it.sources.isNotEmpty() })
        // Region segments play in file order: the housing story comes first after entering Pangyo.
        val pangyo = segments.filter { it.regionId == "pangyo" }
        assertEquals("region-pangyo-housing", pangyo.first().id)
        assertTrue(pangyo.first().sources.isNotEmpty())
    }

    @Test fun missingCategoryFallsBackByType() {
        val row = """{"id":"x","type":"MAIN","topic":"T","script":"S","durationSec":1,"industries":[],"keywords":[],
            "term":{"name":"n","explanation":"e"},"regionId":null,"sources":[]}"""
        assertEquals(BriefingSegment.Category.TECH, SegmentJsonParser.segments("[$row]").single().category)
        val living = row.replace("\"sources\":[]", "\"sources\":[],\"category\":\"LIVING\"")
        assertEquals(BriefingSegment.Category.LIVING, SegmentJsonParser.segments("[$living]").single().category)
    }

    @Test(expected = IllegalArgumentException::class)
    fun duplicateRegionIdsAreRejected() {
        RouteJsonParser.regions("""[{"id":"a","name":"A","latitude":37,"longitude":127,"radiusMeters":1},{"id":"a","name":"B","latitude":37,"longitude":127,"radiusMeters":1}]""")
    }

    @Test fun everyNonWrapupSegmentHasExactlyOneQuiz() {
        val expected = segments.filter { it.type != BriefingSegment.Type.WRAPUP }.map { it.id }.toSet()
        assertEquals(expected, quizzes.map { it.segmentId }.toSet())
        assertTrue(quizzes.all { it.question.isNotBlank() && it.explanation.isNotBlank() && it.choices.none(String::isBlank) })
    }

    @Test(expected = IllegalArgumentException::class)
    fun quizAnswerOutOfRangeIsRejected() {
        QuizJsonParser.questions("""[{"segmentId":"a","question":"Q","choices":["x","y"],"answerIndex":2,"explanation":"E"}]""")
    }

    @Test(expected = IllegalArgumentException::class)
    fun duplicateQuizSegmentIdsAreRejected() {
        val row = """{"segmentId":"a","question":"Q","choices":["x","y"],"answerIndex":0,"explanation":"E"}"""
        QuizJsonParser.questions("[$row,$row]")
    }
}
