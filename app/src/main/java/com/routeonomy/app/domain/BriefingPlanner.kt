package com.routeonomy.app.domain

/** Selects a time-bounded playlist without Android or SDK dependencies. */
class BriefingPlanner {
    fun plan(
        etaMinutes: Int,
        available: List<BriefingSegment>,
        playedIds: Set<String> = emptySet(),
        interests: Set<String> = emptySet(),
    ): BriefingPlan {
        val wrapUp = available.firstOrNull { it.type == BriefingSegment.Type.WRAPUP && it.id !in playedIds }
            ?: return BriefingPlan(emptyList())
        val etaSeconds = etaMinutes.coerceAtLeast(1) * 60
        if (etaSeconds < wrapUp.durationSec + 60) return BriefingPlan(emptyList())
        val availableSeconds = etaSeconds - wrapUp.durationSec - 60
        val main = available.asSequence()
            .filter { it.type == BriefingSegment.Type.MAIN && it.id !in playedIds }
            .withIndex()
            .sortedWith(compareByDescending<IndexedValue<BriefingSegment>> {
                it.value.industries.count(interests::contains)
            }.thenBy { it.index })
            .map { it.value }
        val selected = mutableListOf<BriefingSegment>()
        var budget = availableSeconds
        for (segment in main) {
            if (segment.durationSec <= budget) {
                selected += segment
                budget -= segment.durationSec
            }
        }
        return BriefingPlan(selected + wrapUp)
    }

    fun insertRegionAfterCurrent(
        currentAndQueued: List<BriefingSegment>,
        region: BriefingSegment,
        etaMinutes: Int,
    ): BriefingPlan {
        require(region.type == BriefingSegment.Type.REGION)
        if (currentAndQueued.isEmpty()) return plan(etaMinutes, listOf(region) + currentAndQueued)
        val current = currentAndQueued.first()
        val tail = currentAndQueued.drop(1).filter { it.type == BriefingSegment.Type.MAIN }
        val wrapUp = currentAndQueued.lastOrNull { it.type == BriefingSegment.Type.WRAPUP }
            ?: return BriefingPlan(listOf(current, region) + tail)
        val budget = (etaMinutes.coerceAtLeast(1) * 60 - current.durationSec - region.durationSec - wrapUp.durationSec - 60).coerceAtLeast(0)
        val kept = tail.takeWhile { it.durationSec <= budget }
        return BriefingPlan(listOf(current, region) + kept + wrapUp)
    }
}
