package com.routeonomy.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class BriefingPlannerTest {
    private val planner = BriefingPlanner()
    private val mainA = segment("a", BriefingSegment.Type.MAIN, 180, listOf("energy"))
    private val mainB = segment("b", BriefingSegment.Type.MAIN, 120, listOf("semiconductor"))
    private val wrap = segment("wrap", BriefingSegment.Type.WRAPUP, 60)

    @Test fun reservesWrapUpAndOneMinuteBuffer() {
        val result = planner.plan(6, listOf(mainA, mainB, wrap))
        assertEquals(listOf("a", "wrap"), result.segments.map { it.id })
    }

    @Test fun interestsRankEligibleSegmentsAndPlayedItemsAreExcluded() {
        val result = planner.plan(10, listOf(mainA, mainB, wrap), setOf("a"), setOf("semiconductor"))
        assertEquals(listOf("b", "wrap"), result.segments.map { it.id })
    }

    @Test fun shortEtaFallsBackToWrapUpOnly() {
        val result = planner.plan(2, listOf(mainA, mainB, wrap))
        assertEquals(listOf("wrap"), result.segments.map { it.id })
    }

    @Test fun etaShorterThanWrapUpAndSafetyBufferProducesNoQueue() {
        val result = planner.plan(1, listOf(mainA, mainB, wrap))
        assertEquals(emptyList<String>(), result.segments.map { it.id })
    }

    @Test fun completedWrapUpDoesNotRepeat() {
        val result = planner.plan(10, listOf(mainA, mainB, wrap), setOf("a", "b", "wrap"))
        assertEquals(emptyList<String>(), result.segments)
    }

    private fun segment(id: String, type: BriefingSegment.Type, seconds: Int, tags: List<String> = emptyList()) =
        BriefingSegment(id, type, id, "script", seconds, tags, emptyList(), SegmentTerm("term", "meaning"), null, emptyList())
}
