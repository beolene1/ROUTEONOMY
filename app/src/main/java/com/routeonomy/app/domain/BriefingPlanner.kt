package com.routeonomy.app.domain

/**
 * Selects a time-bounded playlist without Android or SDK dependencies.
 * MAIN topics follow [order] (see [PlaybackOrder]) when given, else interest matches then content order.
 */
class BriefingPlanner {
    fun plan(
        etaMinutes: Int,
        available: List<BriefingSegment>,
        playedIds: Set<String> = emptySet(),
        interests: Set<String> = emptySet(),
        order: List<String> = emptyList(),
    ): BriefingPlan {
        val wrapUp = available.firstOrNull { it.type == BriefingSegment.Type.WRAPUP && it.id !in playedIds }
            ?: return BriefingPlan(emptyList())
        val etaSeconds = etaMinutes.coerceAtLeast(1) * 60
        if (etaSeconds < wrapUp.durationSec + 60) return BriefingPlan(emptyList())
        val selected = mutableListOf<BriefingSegment>()
        fill(selected, rankedMain(available, playedIds, interests, order), etaSeconds - wrapUp.durationSec - 60)
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
        order: List<String> = emptyList(),
    ): BriefingPlan {
        val selected = mutableListOf<BriefingSegment>()
        fill(selected, rankedMain(available, playedIds, interests, order), minutesToRegion.coerceAtLeast(0) * 60 - 60)
        return BriefingPlan(selected)
    }

    /**
     * B section after entering [regionId]: that region's segments within the remaining time, after
     * reserving WRAPUP and one minute of slack; time still left goes to unheard MAIN topics; then WRAPUP.
     */
    fun planRegion(
        remainingMinutes: Int,
        regionId: String,
        available: List<BriefingSegment>,
        playedIds: Set<String> = emptySet(),
        order: List<String> = emptyList(),
    ): BriefingPlan {
        val wrapUp = available.firstOrNull { it.type == BriefingSegment.Type.WRAPUP && it.id !in playedIds }
        val budget = remainingMinutes.coerceAtLeast(0) * 60 - 60 - (wrapUp?.durationSec ?: 0)
        val selected = mutableListOf<BriefingSegment>()
        val regional = available.filter {
            it.type == BriefingSegment.Type.REGION && it.regionId == regionId && it.id !in playedIds
        }
        val left = fill(selected, regional.asSequence(), budget)
        fill(selected, rankedMain(available, playedIds, emptySet(), order), left)
        return BriefingPlan(selected + listOfNotNull(wrapUp))
    }

    /** Adds every candidate that still fits, in order; returns the seconds left. */
    private fun fill(selected: MutableList<BriefingSegment>, candidates: Sequence<BriefingSegment>, seconds: Int): Int {
        var budget = seconds
        for (segment in candidates) {
            if (segment.durationSec <= budget) {
                selected += segment
                budget -= segment.durationSec
            }
        }
        return budget
    }

    private fun rankedMain(
        available: List<BriefingSegment>,
        playedIds: Set<String>,
        interests: Set<String>,
        order: List<String>,
    ): Sequence<BriefingSegment> {
        val position = order.withIndex().associate { it.value to it.index }
        return available.asSequence()
            .filter { it.type == BriefingSegment.Type.MAIN && it.id !in playedIds }
            .withIndex()
            .sortedWith(compareBy<IndexedValue<BriefingSegment>> { position[it.value.id] ?: Int.MAX_VALUE }
                .thenByDescending { it.value.industries.count(interests::contains) }
                .thenBy { it.index })
            .map { it.value }
    }
}
