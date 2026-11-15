package com.routeonomy.app.domain

import com.routeonomy.app.domain.BriefingSegment.Category
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.random.Random

class PlaybackOrderTest {
    private val t1 = main("t1", Category.TECH)
    private val t2 = main("t2", Category.TECH)
    private val t3 = main("t3", Category.TECH)
    private val t4 = main("t4", Category.TECH)
    private val t5 = main("t5", Category.TECH)
    private val l1 = main("l1", Category.LIVING)
    private val r1 = main("r1", Category.REGION)
    private val pangyo = BriefingSegment("p", BriefingSegment.Type.REGION, "p", "s", 60, emptyList(), emptyList(),
        SegmentTerm("t", "m"), "pangyo", emptyList())
    private val all = listOf(t1, t2, t3, t4, t5, l1, r1, pangyo)

    @Test fun pickedFirstThenTwoOfItsCategoryPerOtherTopic() {
        val order = PlaybackOrder.build(all, "t3", Random(1))
        assertEquals("t3", order[0])
        assertEquals(listOf("t1", "t2", "t4", "t5"), order.filter { it.startsWith("t") && it != "t3" })
        // t3 counts as the first of two: t3, t1 | other | t2, t4 | other | t5
        assertEquals(listOf(true, true, false, true, true, false, true), order.map { it.startsWith("t") })
        assertEquals(setOf("l1", "r1"), order.filterNot { it.startsWith("t") }.toSet())
    }

    @Test fun otherTopicsFillOnceThePickedCategoryRunsOut() {
        val order = PlaybackOrder.build(all, "l1", Random(2))
        assertEquals("l1", order[0])
        assertEquals(7, order.size)
        assertEquals(setOf("t1", "t2", "t3", "t4", "t5", "r1"), order.drop(1).toSet())
    }

    @Test fun regionSegmentsAreNotPartOfTheOrderAndUnknownPickFallsBackToFirst() {
        val order = PlaybackOrder.build(all, "missing", Random(3))
        assertEquals("t1", order[0])
        assertEquals(false, "p" in order)
    }

    private fun main(id: String, category: Category) =
        BriefingSegment(id, BriefingSegment.Type.MAIN, id, "s", 60, emptyList(), emptyList(), SegmentTerm("t", "m"), null,
            emptyList(), category)
}
