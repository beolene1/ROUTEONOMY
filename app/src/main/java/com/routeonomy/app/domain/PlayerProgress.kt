package com.routeonomy.app.domain

/** Growth stages; a stage is reached when the points earned so far reach [minPoints]. */
enum class GrowthStage(val label: String, val emoji: String, val minPoints: Int) {
    SEED("씨앗", "🌰", 0),
    SPROUT("새싹", "🌱", 300),
    STEM("줄기", "🌿", 1_200),
    FLOWER("꽃", "🌸", 2_900),
    FRUIT("열매", "🍎", 6_500);

    /** 1-based level shown as "Lv.N". */
    val level: Int get() = ordinal + 1
    val next: GrowthStage? get() = entries.getOrNull(ordinal + 1)

    companion object {
        @JvmStatic
        fun of(earnedPoints: Int): GrowthStage = entries.last { earnedPoints >= it.minPoints }
    }
}

/**
 * Point rules. Thresholds assume about 60 points per drive and two drives a day:
 * 씨앗→새싹 2~3 days, →줄기 7~8 days, →꽃 2 weeks, →열매 1 month.
 */
object PointRules {
    const val MAIN_SEGMENT = 5
    const val REGION_SEGMENT = 10
    const val QUIZ_CORRECT = 3
    const val QUIZ_WRONG = 1
    const val HINT_COST = 1

    @JvmStatic
    fun forSegment(type: BriefingSegment.Type): Int = when (type) {
        BriefingSegment.Type.MAIN -> MAIN_SEGMENT
        BriefingSegment.Type.REGION -> REGION_SEGMENT
        BriefingSegment.Type.WRAPUP -> 0
    }
}

/** Stored progress: [earnedTotal] decides the stage and never goes down; [balance] is spendable. */
data class ProgressSnapshot(val earnedTotal: Int, val balance: Int) {
    companion object {
        @JvmField val EMPTY = ProgressSnapshot(0, 0)
    }
}

interface ProgressStore {
    fun load(): ProgressSnapshot
    fun save(snapshot: ProgressSnapshot)
}

/**
 * Points and growth stage across drives. Each reward is given at most once per app run (one drive):
 * a segment heard twice, a quiz answered again after "다시 풀기" or a rebuilt quiz earn nothing more,
 * and a hint is paid once per question. Saved to [store] on every change.
 */
class PlayerProgress(private val store: ProgressStore) {
    private var earned: Int
    private var balance: Int
    private var earnedThisRun = 0
    private var quizPointsThisRun = 0
    private var stageAtStart: GrowthStage
    private val rewardedSegments = HashSet<String>()
    private val answeredQuestions = HashSet<String>()
    private val paidHints = HashSet<String>()

    init {
        val saved = store.load()
        earned = saved.earnedTotal.coerceAtLeast(0)
        balance = saved.balance.coerceIn(0, earned)
        stageAtStart = GrowthStage.of(earned)
    }

    fun earnedTotal(): Int = earned
    fun balance(): Int = balance
    fun earnedThisRun(): Int = earnedThisRun
    fun quizPointsThisRun(): Int = quizPointsThisRun
    fun stage(): GrowthStage = GrowthStage.of(earned)
    /** Points still needed for the next stage; null at the last stage. */
    fun pointsToNextStage(): Int? = stage().next?.let { it.minPoints - earned }
    /** The stage reached during this run, or null when it did not change. */
    fun stageReachedThisRun(): GrowthStage? = stage().takeIf { it != stageAtStart }

    /** A segment heard to the end. Returns the points given (0 when already rewarded this run). */
    fun rewardSegment(segmentId: String, type: BriefingSegment.Type): Int {
        if (!rewardedSegments.add(segmentId)) return 0
        return earn(PointRules.forSegment(type))
    }

    /** First answer to the question of [segmentId] this run. Returns the points given. */
    fun rewardQuizAnswer(segmentId: String, correct: Boolean): Int {
        if (!answeredQuestions.add(segmentId)) return 0
        val points = earn(if (correct) PointRules.QUIZ_CORRECT else PointRules.QUIZ_WRONG)
        quizPointsThisRun += points
        return points
    }

    /** True once the question of [segmentId] earned its answer reward this run (repeat answers earn nothing). */
    fun isQuizAnswerRewarded(segmentId: String): Boolean = segmentId in answeredQuestions

    fun isHintPaid(segmentId: String): Boolean = segmentId in paidHints
    fun canShowHint(segmentId: String): Boolean = isHintPaid(segmentId) || balance >= PointRules.HINT_COST

    /** Pays for the hint of [segmentId] once. Returns false when it cannot be paid. */
    fun payHint(segmentId: String): Boolean {
        if (isHintPaid(segmentId)) return true
        if (balance < PointRules.HINT_COST) return false
        balance -= PointRules.HINT_COST
        paidHints += segmentId
        save()
        return true
    }

    /** Demo tool: adds points without a reward record; not counted as earned this run. */
    fun addDemoPoints(points: Int) {
        if (points <= 0) return
        earned += points
        balance += points
        save()
    }

    /** Demo tool: leaves [gap] points to the next stage, so one heard segment shows a stage-up. */
    @JvmOverloads
    fun moveNearNextStage(gap: Int = PointRules.MAIN_SEGMENT) {
        val toNext = pointsToNextStage() ?: return
        addDemoPoints(toNext - gap)
    }

    /** Demo tool: back to 씨앗 with no points. Also forgets this run's rewards, so quiz answers can earn again. */
    fun resetAll() {
        earned = 0
        balance = 0
        earnedThisRun = 0
        quizPointsThisRun = 0
        rewardedSegments.clear()
        answeredQuestions.clear()
        paidHints.clear()
        stageAtStart = GrowthStage.SEED
        save()
    }

    private fun earn(points: Int): Int {
        if (points <= 0) return 0
        earned += points
        balance += points
        earnedThisRun += points
        save()
        return points
    }

    private fun save() = store.save(ProgressSnapshot(earned, balance))
}
