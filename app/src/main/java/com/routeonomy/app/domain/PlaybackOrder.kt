package com.routeonomy.app.domain

import kotlin.random.Random

/**
 * Order of MAIN topics for one drive, built once when the topic is picked: the picked topic, then two
 * topics of its category for every one random topic of another category. When one side runs out the
 * other fills the rest, so there is something left to play until arrival.
 */
object PlaybackOrder {
    const val PICKED_CATEGORY_RUN = 2

    @JvmStatic
    @JvmOverloads
    fun build(segments: List<BriefingSegment>, pickedId: String?, random: Random = Random.Default): List<String> {
        val mains = segments.filter { it.type == BriefingSegment.Type.MAIN }
        val picked = mains.firstOrNull { it.id == pickedId } ?: mains.firstOrNull() ?: return emptyList()
        val same = ArrayDeque(mains.filter { it.id != picked.id && it.category == picked.category })
        val others = ArrayDeque(mains.filter { it.category != picked.category }.shuffled(random))
        val order = mutableListOf(picked.id)
        var run = 1 // the picked topic counts toward the first run
        while (same.isNotEmpty() || others.isNotEmpty()) {
            if ((run < PICKED_CATEGORY_RUN || others.isEmpty()) && same.isNotEmpty()) {
                order += same.removeFirst().id
                run++
            } else {
                order += others.removeFirst().id
                run = 0
            }
        }
        return order
    }
}
