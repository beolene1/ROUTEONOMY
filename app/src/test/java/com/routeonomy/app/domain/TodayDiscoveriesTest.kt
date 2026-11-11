package com.routeonomy.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TodayDiscoveriesTest {
    private val a = segment("a")
    private val b = segment("b")
    private val c = segment("c")
    private val all = listOf(a, b, c)

    @Test fun onlyCompletedSegmentsAppearAndSkippedOrUnplayedAreExcluded() {
        // b was skipped (played but not completed), c was never played.
        assertEquals(listOf("a"), TodayDiscoveries.from(all, listOf("a")).map { it.id })
    }

    @Test fun keepsCompletionOrderNotContentOrder() {
        assertEquals(listOf("c", "a"), TodayDiscoveries.from(all, listOf("c", "a")).map { it.id })
    }

    @Test fun ignoresUnknownIdsAndDuplicates() {
        assertEquals(listOf("b"), TodayDiscoveries.from(all, listOf("b", "missing", "b")).map { it.id })
    }

    @Test fun emptyWhenNothingCompleted() {
        assertTrue(TodayDiscoveries.from(all, emptyList()).isEmpty())
    }

    private fun segment(id: String) = BriefingSegment(
        id, BriefingSegment.Type.MAIN, "주제 $id", "대본", 30,
        emptyList(), listOf("키워드"), SegmentTerm("용어", "설명"), null, listOf("https://example.com/$id"),
    )
}
