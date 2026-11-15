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

    private val region1 = segment("r1", BriefingSegment.Type.REGION, 120, region = "pangyo")
    private val region2 = segment("r2", BriefingSegment.Type.REGION, 150, region = "pangyo")
    private val otherRegion = segment("o1", BriefingSegment.Type.REGION, 60, region = "dongtan")

    @Test fun beforeRegionLeavesWrapUpForRegionSection() {
        val result = planner.planBeforeRegion(5, listOf(mainA, mainB, wrap, region1))
        assertEquals(listOf("a"), result.segments.map { it.id })
    }

    @Test fun beforeRegionWithLittleTimeIsEmpty() {
        assertEquals(emptyList<String>(), planner.planBeforeRegion(1, listOf(mainA, mainB, wrap)).segments.map { it.id })
    }

    @Test fun regionSectionUsesOnlyThatRegionThenWrapUp() {
        val all = listOf(mainA, region1, region2, otherRegion, wrap)
        assertEquals(listOf("r1", "r2", "wrap"), planner.planRegion(7, "pangyo", all).segments.map { it.id })
        assertEquals(listOf("r1", "wrap"), planner.planRegion(5, "pangyo", all).segments.map { it.id })
        assertEquals(listOf("o1", "wrap"), planner.planRegion(5, "dongtan", all).segments.map { it.id })
    }

    @Test fun regionSectionSkipsPlayedAndKeepsWrapUpWhenShort() {
        val all = listOf(region1, region2, wrap)
        assertEquals(listOf("r2", "wrap"), planner.planRegion(10, "pangyo", all, setOf("r1")).segments.map { it.id })
        assertEquals(listOf("wrap"), planner.planRegion(1, "pangyo", all).segments.map { it.id })
        assertEquals(emptyList<String>(), planner.planRegion(10, "pangyo", all, setOf("r1", "r2", "wrap")).segments.map { it.id })
    }

    @Test fun orderDecidesMainSequenceAheadOfContentOrder() {
        val result = planner.planBeforeRegion(10, listOf(mainA, mainB, wrap), order = listOf("b", "a"))
        assertEquals(listOf("b", "a"), result.segments.map { it.id })
    }

    @Test fun regionSectionFillsLeftoverTimeWithUnheardMainBeforeWrapUp() {
        val all = listOf(mainA, mainB, region1, region2, wrap)
        // 15 min: 900 - 60 slack - 60 wrap-up = 780; r1+r2 = 270, then b (order first) 120, a 180
        assertEquals(listOf("r1", "r2", "b", "a", "wrap"),
            planner.planRegion(15, "pangyo", all, order = listOf("b", "a")).segments.map { it.id })
        // After both regional segments were heard the remaining time still plays main topics
        assertEquals(listOf("a", "wrap"), planner.planRegion(6, "pangyo", all, setOf("r1", "r2")).segments.map { it.id })
    }

    private fun segment(id: String, type: BriefingSegment.Type, seconds: Int, tags: List<String> = emptyList(), region: String? = null) =
        BriefingSegment(id, type, id, "script", seconds, tags, emptyList(), SegmentTerm("term", "meaning"), region, emptyList())
}
