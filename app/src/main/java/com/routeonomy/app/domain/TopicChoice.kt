package com.routeonomy.app.domain

import kotlin.random.Random

/** Topics offered in the Change topic popup: one per category, so a long topic list stays short. */
object TopicChoice {
    /**
     * One topic per category, in category order. The [shown] topic stands for its own category so the
     * current pick stays selectable; other categories get a random unheard topic.
     */
    @JvmStatic
    @JvmOverloads
    fun onePerCategory(topics: List<BriefingSegment>, shown: BriefingSegment?, random: Random = Random.Default): List<BriefingSegment> =
        topics.groupBy { it.category }.entries
            .sortedBy { it.key?.ordinal ?: Int.MAX_VALUE }
            .map { (category, group) -> if (shown != null && shown.category == category && shown in group) shown else group.random(random) }
}
