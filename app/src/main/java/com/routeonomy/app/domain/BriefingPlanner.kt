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
        val main = rankedMain(available, playedIds, interests)
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

    /**
     * A section before a predicted region: MAIN segments that fit before the estimated entry,
     * keeping one minute of slack. WRAPUP is left for the region section.
     */
    fun planBeforeRegion(
        minutesToRegion: Int,
        available: List<BriefingSegment>,
        playedIds: Set<String> = emptySet(),
        interests: Set<String> = emptySet(),
    ): BriefingPlan {
        var budget = minutesToRegion.coerceAtLeast(0) * 60 - 60
        val selected = mutableListOf<BriefingSegment>()
        for (segment in rankedMain(available, playedIds, interests)) {
            if (segment.durationSec <= budget) {
                selected += segment
                budget -= segment.durationSec
            }
        }
        return BriefingPlan(selected)
    }

    /**
     * B section after entering [regionId]: that region's segments within the remaining time,
     * after reserving WRAPUP and one minute of slack, followed by WRAPUP.
     */
    fun planRegion(
        remainingMinutes: Int,
        regionId: String,
        available: List<BriefingSegment>,
        playedIds: Set<String> = emptySet(),
    ): BriefingPlan {
        val wrapUp = available.firstOrNull { it.type == BriefingSegment.Type.WRAPUP && it.id !in playedIds }
        var budget = remainingMinutes.coerceAtLeast(0) * 60 - 60 - (wrapUp?.durationSec ?: 0)
        val selected = mutableListOf<BriefingSegment>()
        for (segment in available) {
            if (segment.type != BriefingSegment.Type.REGION || segment.regionId != regionId || segment.id in playedIds) continue
            if (segment.durationSec <= budget) {
                selected += segment
                budget -= segment.durationSec
            }
        }
        return BriefingPlan(selected + listOfNotNull(wrapUp))
    }

    private fun rankedMain(available: List<BriefingSegment>, playedIds: Set<String>, interests: Set<String>) =
        available.asSequence()
            .filter { it.type == BriefingSegment.Type.MAIN && it.id !in playedIds }
            .withIndex()
            .sortedWith(compareByDescending<IndexedValue<BriefingSegment>> {
                it.value.industries.count(interests::contains)
            }.thenBy { it.index })
            .map { it.value }
}
