package com.routeonomy.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerProgressTest {
    private class MemoryStore(var saved: ProgressSnapshot = ProgressSnapshot.EMPTY) : ProgressStore {
        override fun load() = saved
        override fun save(snapshot: ProgressSnapshot) { saved = snapshot }
    }

    private val store = MemoryStore()
    private val progress = PlayerProgress(store)

    @Test fun stageThresholds() {
        assertEquals(GrowthStage.SEED, GrowthStage.of(0))
        assertEquals(GrowthStage.SEED, GrowthStage.of(299))
        assertEquals(GrowthStage.SPROUT, GrowthStage.of(300))
        assertEquals(GrowthStage.STEM, GrowthStage.of(1_200))
        assertEquals(GrowthStage.FLOWER, GrowthStage.of(2_900))
        assertEquals(GrowthStage.FRUIT, GrowthStage.of(6_500))
        assertEquals(GrowthStage.FRUIT, GrowthStage.of(100_000))
        assertEquals(1, GrowthStage.SEED.level)
        assertEquals(5, GrowthStage.FRUIT.level)
    }

    @Test fun segmentPointsByTypeOncePerRun() {
        assertEquals(5, progress.rewardSegment("m1", BriefingSegment.Type.MAIN))
        assertEquals(0, progress.rewardSegment("m1", BriefingSegment.Type.MAIN))
        assertEquals(10, progress.rewardSegment("r1", BriefingSegment.Type.REGION))
        assertEquals(0, progress.rewardSegment("wrap", BriefingSegment.Type.WRAPUP))
        assertEquals(15, progress.earnedTotal())
        assertEquals(15, progress.earnedThisRun())
        assertEquals(ProgressSnapshot(15, 15), store.saved)
    }

    @Test fun quizAnswerOncePerQuestion() {
        assertEquals(3, progress.rewardQuizAnswer("m1", correct = true))
        assertEquals(1, progress.rewardQuizAnswer("m2", correct = false))
        assertEquals(0, progress.rewardQuizAnswer("m1", correct = true)) // "다시 풀기" or a rebuilt quiz
        assertEquals(4, progress.quizPointsThisRun())
    }

    @Test fun hintCostsOnePointOncePerQuestionAndKeepsStage() {
        assertFalse(progress.canShowHint("m1"))
        assertFalse(progress.payHint("m1"))
        progress.addDemoPoints(300)
        assertTrue(progress.payHint("m1"))
        assertTrue(progress.payHint("m1")) // already paid: free
        assertEquals(299, progress.balance())
        assertEquals(300, progress.earnedTotal())
        assertEquals(GrowthStage.SPROUT, progress.stage()) // spending never lowers the stage
    }

    @Test fun nextStageAndStageReachedThisRun() {
        progress.addDemoPoints(295)
        assertEquals(5, progress.pointsToNextStage())
        assertNull(progress.stageReachedThisRun()) // still 씨앗
        progress.rewardSegment("m1", BriefingSegment.Type.MAIN)
        assertEquals(GrowthStage.SPROUT, progress.stageReachedThisRun())
        assertEquals(5, progress.earnedThisRun()) // demo points are not "earned this run"
        progress.addDemoPoints(10_000)
        assertNull(progress.pointsToNextStage())
    }

    @Test fun loadsSavedProgressAndResets() {
        val loaded = PlayerProgress(MemoryStore(ProgressSnapshot(1_250, 40)))
        assertEquals(GrowthStage.STEM, loaded.stage())
        assertEquals(40, loaded.balance())
        assertNull(loaded.stageReachedThisRun())
        loaded.resetAll()
        assertEquals(GrowthStage.SEED, loaded.stage())
        assertEquals(0, loaded.balance())
    }

    @Test fun brokenSaveIsClamped() {
        val loaded = PlayerProgress(MemoryStore(ProgressSnapshot(10, 99)))
        assertEquals(10, loaded.balance())
    }
}
