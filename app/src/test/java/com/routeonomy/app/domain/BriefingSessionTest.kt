package com.routeonomy.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BriefingSessionTest {
    private val seoulStation = GeoPoint(37.55594, 126.97233)
    private val giheung = GeoPoint(37.27566, 127.11595)
    private val pangyo = Region("pangyo", "판교", GeoPoint(37.40159, 127.10842), 1650.0)

    private val m1 = segment("m1", BriefingSegment.Type.MAIN, "m1 첫 문장. m1 둘째 문장.")
    private val m2 = segment("m2", BriefingSegment.Type.MAIN, "m2 첫 문장. m2 둘째 문장.")
    private val r1 = segment("r1", BriefingSegment.Type.REGION, "r1 문장.", region = "pangyo")
    private val wrap = segment("wrap", BriefingSegment.Type.WRAPUP, "마무리.")

    private class FakeSpeech : SpeechOutput {
        val spoken = mutableListOf<Pair<Int, String>>()
        var stops = 0
        override fun speak(utteranceId: Int, text: String) { spoken += utteranceId to text }
        override fun stop() { stops++ }
        fun lastId() = spoken.last().first
        fun lastText() = spoken.last().second
    }

    private val speech = FakeSpeech()
    private var now = 0L
    private val session = BriefingSession(listOf(m1, m2, r1, wrap), listOf(pangyo), giheung, seoulStation, 40, speech, { now })
        .also { it.setSpeechReady(true) }

    /** Plays the current segment to its end, one sentence at a time. */
    private fun finishSegment() {
        val id = session.currentSegment()!!.id
        while (session.isPlaying() && session.currentSegment()?.id == id) session.onUtteranceDone(speech.lastId())
    }

    @Test fun startsWithPredictedRegionAheadAndFirstSentence() {
        assertEquals("pangyo", session.prediction()?.region?.id)
        session.startFromDashboard()
        assertEquals("m1 첫 문장.", speech.lastText())
        assertEquals(BriefingSession.State.PLAYING, session.state())
    }

    @Test fun lateDoneOfSkippedSentenceDoesNotFinishTheNextOne() {
        session.startFromDashboard()
        val skipped = speech.lastId()
        session.nextTopic()
        assertEquals("m2 첫 문장.", speech.lastText())
        val spokenBefore = speech.spoken.size
        session.onUtteranceDone(skipped) // the stopped sentence reports late
        assertEquals(spokenBefore, speech.spoken.size)
        assertEquals("m2", session.currentSegment()?.id)
        session.onUtteranceDone(speech.lastId())
        assertEquals("m2 둘째 문장.", speech.lastText())
    }

    @Test fun duplicateDoneIsCountedOnce() {
        session.startFromDashboard()
        val first = speech.lastId()
        session.onUtteranceDone(first)
        session.onUtteranceDone(first)
        assertEquals("m1 둘째 문장.", speech.lastText())
        val second = speech.lastId()
        session.onUtteranceDone(second)
        session.onUtteranceDone(second)
        assertEquals(setOf("m1"), session.completedSegmentIds())
        assertEquals("m2 첫 문장.", speech.lastText())
    }

    @Test fun skippedSegmentIsNotCompleted() {
        session.startFromDashboard()
        session.nextTopic()
        assertTrue(session.completedSegmentIds().isEmpty())
        finishSegment()
        assertEquals(setOf("m2"), session.completedSegmentIds())
    }

    @Test fun finishedTopicStaysCompletedWhenReplayedAndSkipped() {
        session.startFromDashboard()
        finishSegment() // m1 heard to the end
        session.previousTopic() // replay m1
        session.nextTopic()     // skip the replay
        session.nextTopic()     // skip m2
        assertEquals(setOf("m1"), session.completedSegmentIds())
    }

    @Test fun regionEntryWhilePlayingSwitchesOnceAfterCurrentSegment() {
        session.startFromDashboard()
        assertFalse(session.onRegionEntered("pangyo"))
        assertFalse(session.onRegionEntered("pangyo")) // GPS and proximity alert report the same entry
        assertTrue(session.hasPendingRegionSwitch())
        assertEquals("m1", session.currentSegment()?.id)
        finishSegment()
        assertEquals("r1", session.currentSegment()?.id)
        assertTrue(speech.lastText().startsWith("지금 판교에 들어섰습니다"))
        assertFalse(session.hasPendingRegionSwitch())
        assertEquals(setOf("m1"), session.completedSegmentIds())
    }

    @Test fun regionEntryBeforeStartSwitchesQueueWithoutSpeaking() {
        assertTrue(session.onRegionEntered("pangyo"))
        assertEquals("r1", session.currentSegment()?.id)
        assertTrue(speech.spoken.isEmpty())
    }

    @Test fun pauseIgnoresDoneAndResumesTheSameSentence() {
        session.startFromDashboard()
        val paused = speech.lastId()
        session.pause()
        assertEquals(BriefingSession.State.PAUSED, session.state())
        session.onUtteranceDone(paused)
        session.togglePlayback()
        assertEquals("m1 첫 문장.", speech.lastText())
        assertTrue(speech.lastId() != paused)
        assertTrue(session.completedSegmentIds().isEmpty())
    }

    @Test fun previousTopicReplaysEarlierAndRequeuesInterrupted() {
        session.startFromDashboard()
        finishSegment() // m1 heard, m2 starts
        session.previousTopic()
        assertEquals("m1", session.currentSegment()?.id)
        assertEquals("m1 첫 문장.", speech.lastText())
        session.nextTopic()
        assertEquals("m2", session.currentSegment()?.id)
    }

    @Test fun previousTopicWithOnlyOneTopicRestartsIt() {
        session.startFromDashboard()
        session.onUtteranceDone(speech.lastId())
        session.previousTopic()
        assertEquals("m1 첫 문장.", speech.lastText())
        assertEquals("m1", session.currentSegment()?.id)
    }

    @Test fun studyTimeCountsOnlyFinishedSentences() {
        session.startFromDashboard()
        now = 1_000
        session.onUtteranceDone(speech.lastId())
        now = 1_600
        session.pause()
        assertEquals(1_000, session.studyMillis())
    }

    @Test fun dashboardTopicPlaysFirstAndLocksAfterStart() {
        assertTrue(session.canChangeDashboardTopic())
        session.shiftDashboardTopic(1)
        assertEquals("m2", session.dashboardTopic()?.id)
        session.startFromDashboard()
        assertEquals("m2 첫 문장.", speech.lastText())
        assertFalse(session.canChangeDashboardTopic())
    }

    @Test fun heldAutoplayDoesNotStartOnRegionEntry() {
        session.startFromDashboard()
        session.nextTopic()
        finishSegment() // A queue ends: waits for the region
        assertEquals(BriefingSession.State.WAITING_FOR_REGION, session.state())
        val spokenBefore = speech.spoken.size
        session.setAutoplayHeld(true)
        assertTrue(session.onRegionEntered("pangyo"))
        assertEquals(spokenBefore, speech.spoken.size)
        session.setAutoplayHeld(false)
        session.startFromDashboard()
        assertTrue(speech.lastText().startsWith("지금 판교에 들어섰습니다"))
    }

    @Test fun errorStopsPlaybackAndPlayRestartsSegment() {
        session.startFromDashboard()
        session.onUtteranceDone(speech.lastId())
        session.onUtteranceError(speech.lastId())
        assertEquals(BriefingSession.State.ERROR, session.state())
        session.togglePlayback()
        assertEquals("m1 첫 문장.", speech.lastText())
    }

    @Test fun waitingForRegionResumesWhenEstimateLeavesRoom() {
        session.startFromDashboard()
        session.nextTopic()
        finishSegment()
        assertEquals(BriefingSession.State.WAITING_FOR_REGION, session.state())
        val spokenBefore = speech.spoken.size
        session.updatePosition(giheung, 10) // past the region: no prediction left
        session.refreshRoute()
        assertFalse(session.isWaitingForRegion())
        assertTrue(speech.spoken.size > spokenBefore)
    }

    @Test fun nothingLeftClearsWhenQueueRefills() {
        val short = BriefingSession(listOf(m1, wrap), emptyList(), giheung, seoulStation, 1, speech, { now })
            .also { it.setSpeechReady(true) }
        short.togglePlayback()
        assertEquals(BriefingSession.State.NOTHING_LEFT, short.state())
        short.updatePosition(seoulStation, 40)
        short.refreshRoute()
        assertEquals(BriefingSession.State.IDLE, short.state())
    }

    @Test fun splitSentencesKeepsUnpunctuatedScript() {
        assertEquals(listOf("가.", "나?"), BriefingSession.splitSentences("가. 나?"))
        assertEquals(listOf("문장"), BriefingSession.splitSentences(" 문장 "))
        assertEquals(emptyList<String>(), BriefingSession.splitSentences("  "))
    }

    private fun segment(id: String, type: BriefingSegment.Type, script: String, seconds: Int = 240, region: String? = null) =
        BriefingSegment(id, type, id, script, seconds, emptyList(), emptyList(), SegmentTerm("term", "meaning"), region, emptyList())
}
