package com.routeonomy.app.pleos

import org.junit.Assert.assertEquals
import org.junit.Test

class SpeechGateTest {
    private val spoken = mutableListOf<String>()
    private var stops = 0
    private val done = mutableListOf<Int>()
    private val errors = mutableListOf<Int>()
    private val timers = mutableListOf<() -> Unit>()

    private val gate = SpeechGate(
        object : SpeechGate.Engine {
            override fun speak(text: String) { spoken += text }
            override fun stop() { stops++ }
        },
        { _, task -> timers += task },
        object : SpeechGate.Listener {
            override fun onDone(utteranceId: Int) { done += utteranceId }
            override fun onError(utteranceId: Int, message: String) { errors += utteranceId }
        },
    )

    @Test fun naturalDoneIsReportedOnce() {
        gate.speak(1, "a")
        gate.onEngineDone()
        gate.onEngineDone()
        assertEquals(listOf(1), done)
    }

    @Test fun nextSentenceWaitsForStoppedOneAndDropsItsEnd() {
        gate.speak(1, "a")
        gate.speak(2, "b")
        assertEquals(1, stops)
        assertEquals(listOf("a"), spoken)
        gate.onEngineDone() // late done of "a"
        assertEquals(listOf("a", "b"), spoken)
        assertEquals(emptyList<Int>(), done)
        gate.onEngineDone()
        assertEquals(listOf(2), done)
    }

    @Test fun timeoutStartsNextWhenStopReportsNothing() {
        gate.speak(1, "a")
        gate.speak(2, "b")
        timers.single()()
        assertEquals(listOf("a", "b"), spoken)
        gate.onEngineDone()
        assertEquals(listOf(2), done)
    }

    @Test fun onlyLatestRequestStartsAfterRapidPresses() {
        gate.speak(1, "a")
        gate.stop()
        gate.speak(2, "b")
        gate.speak(3, "c")
        gate.onEngineStopped()
        assertEquals(listOf("a", "c"), spoken)
        assertEquals(1, stops)
    }

    @Test fun pauseDropsTheStoppedEnd() {
        gate.speak(1, "a")
        gate.stop()
        gate.onEngineDone()
        gate.onEngineStopped()
        assertEquals(emptyList<Int>(), done)
        assertEquals(listOf("a"), spoken)
    }

    @Test fun staleTimerDoesNotEndALaterStop() {
        gate.speak(1, "a")
        gate.speak(2, "b")
        gate.onEngineStopped() // "b" starts
        gate.speak(3, "c")     // second stop
        timers[0]()            // first stop's timer fires late
        assertEquals(listOf("a", "b"), spoken)
        timers[1]()
        assertEquals(listOf("a", "b", "c"), spoken)
    }

    @Test fun unrequestedStopKeepsSentence() {
        gate.speak(1, "a")
        gate.onEngineStopped()
        gate.onEngineDone()
        assertEquals(listOf(1), done)
    }

    @Test fun secondEndOfStoppedSentenceBeforeNextStartIsDropped() {
        gate.speak(1, "a")
        gate.onEngineStarted()
        gate.speak(2, "b")
        gate.onEngineStopped() // stopped "a": first end, "b" starts
        gate.onEngineDone()    // stopped "a": second end, before "b" started
        assertEquals(emptyList<Int>(), done)
        gate.onEngineStarted()
        gate.onEngineDone()
        assertEquals(listOf(2), done)
    }

    @Test fun naturalNextSentenceIsNotGatedOnStart() {
        gate.speak(1, "a")
        gate.onEngineStarted()
        gate.onEngineDone()
        gate.speak(2, "b")
        gate.onEngineDone() // no onStart for "b": still accepted
        assertEquals(listOf(1, 2), done)
    }

    @Test fun errorDuringStopIsDroppedAndStartsHeldSentence() {
        gate.speak(1, "a")
        gate.speak(2, "b")
        gate.onEngineError("x")
        assertEquals(emptyList<Int>(), errors)
        assertEquals(listOf("a", "b"), spoken)
    }

    @Test fun errorOfSpeakingSentenceIsReported() {
        gate.speak(1, "a")
        gate.onEngineError("x")
        assertEquals(listOf(1), errors)
        gate.speak(2, "b")
        assertEquals(listOf("a", "b"), spoken) // nothing was speaking: no stop wait
        assertEquals(0, stops)
    }
}
