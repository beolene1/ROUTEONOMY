package com.routeonomy.app.domain

/** F7: segments listened to completely in this session, in the order they finished. Skipped ones are excluded. */
object TodayDiscoveries {
    @JvmStatic
    fun from(segments: List<BriefingSegment>, completedIds: Collection<String>): List<BriefingSegment> {
        val byId = segments.associateBy { it.id }
        return completedIds.distinct().mapNotNull { byId[it] }
    }
}
