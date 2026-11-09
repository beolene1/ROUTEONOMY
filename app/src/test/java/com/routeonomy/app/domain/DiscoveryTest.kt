package com.routeonomy.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class DiscoveryTest {
    @Test fun `discovery only includes recorded segment ids`() {
        val listened = BriefingSegment("done", BriefingSegment.Type.MAIN, "완료 주제", "대본", 30,
            emptyList(), listOf("키워드"), SegmentTerm("용어", "설명"), null, listOf("https://example.com"))
        val skipped = listened.copy(id = "skipped", topic = "건너뛴 주제")
        val all = listOf(listened, skipped)
        assertEquals(listOf(listened), all.filter { it.id in setOf("done") })
    }
}
