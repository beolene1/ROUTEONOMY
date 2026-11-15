package com.routeonomy.app.domain

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TopicChoiceTest {
    private fun topic(id: String, category: BriefingSegment.Category) = BriefingSegment(id, BriefingSegment.Type.MAIN, id,
        "s", 60, emptyList(), emptyList(), SegmentTerm("t", "m"), null, emptyList(), category)

    private val tech = (1..5).map { topic("tech$it", BriefingSegment.Category.TECH) }
    private val living = (1..3).map { topic("living$it", BriefingSegment.Category.LIVING) }

    @Test fun oneTopicPerCategoryInCategoryOrder() {
        val picked = TopicChoice.onePerCategory(living + tech, null, Random(7))
        assertEquals(listOf(BriefingSegment.Category.TECH, BriefingSegment.Category.LIVING), picked.map { it.category })
        assertTrue(picked[0] in tech && picked[1] in living)
    }

    @Test fun currentPickStandsForItsCategory() {
        repeat(20) { seed ->
            val picked = TopicChoice.onePerCategory(tech + living, living[2], Random(seed))
            assertEquals(living[2], picked[1])
        }
    }
}
