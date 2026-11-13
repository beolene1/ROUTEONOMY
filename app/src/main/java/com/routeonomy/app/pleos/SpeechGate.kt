package com.routeonomy.app.pleos

/**
 * Orders speak/stop calls for an engine whose callbacks carry no utterance id (Pleos TTS 3.3.1:
 * `onDone()`, `onStop()`, `onError(type)`).
 *
 * After a stop, the next sentence is held until the stopped one reports an end (done, stop or error)
 * or [settleMillis] passes, and that end is dropped. Otherwise a late `onDone` of the stopped sentence
 * would be read as the end of the new one. For that held sentence only, once the engine has shown that it
 * reports `onStart`, an end arriving before its `onStart` is dropped too (a second end of the stopped one).
 * Sentences that follow a natural end are never gated, so a missing `onStart` cannot stall normal playback.
 * Plain Kotlin so the ordering is unit-tested on the JVM.
 * Must be used from a single thread (the main thread in the app).
 */
class SpeechGate(
    private val engine: Engine,
    private val scheduler: Scheduler,
    private val listener: Listener,
    private val settleMillis: Long = DEFAULT_SETTLE_MILLIS,
) {
    interface Engine {
        fun speak(text: String)
        fun stop()
    }

    fun interface Scheduler {
        fun postDelayed(delayMillis: Long, task: () -> Unit)
    }

    interface Listener {
        fun onDone(utteranceId: Int)
        fun onError(utteranceId: Int, message: String)
    }

    private var speakingId: Int? = null
    private var stopping = false
    private var stopGeneration = 0
    private var pendingId = 0
    private var pendingText: String? = null
    private var engineReportsStart = false
    private var awaitingStart = false

    fun speak(utteranceId: Int, text: String) {
        if (speakingId != null) beginStop()
        if (stopping) {
            pendingId = utteranceId
            pendingText = text
        } else start(utteranceId, text)
    }

    fun stop() {
        pendingText = null
        if (speakingId != null) beginStop()
    }

    fun onEngineStarted() {
        engineReportsStart = true
        awaitingStart = false
    }

    fun onEngineDone() {
        if (stopping) { settle(); return }
        if (isStaleEnd()) return
        val id = speakingId ?: return // duplicate or unexpected: nothing is speaking
        speakingId = null
        listener.onDone(id)
    }

    /** `onStop` only ends a stop we asked for; an unrequested stop leaves the sentence as it is. */
    fun onEngineStopped() {
        if (stopping) settle()
    }

    fun onEngineError(message: String) {
        if (stopping) { settle(); return }
        if (isStaleEnd()) return
        val id = speakingId ?: return
        speakingId = null
        listener.onError(id, message)
    }

    private fun isStaleEnd() = speakingId != null && engineReportsStart && awaitingStart

    private fun beginStop() {
        speakingId = null
        stopping = true
        val generation = ++stopGeneration
        engine.stop()
        scheduler.postDelayed(settleMillis) { if (stopping && generation == stopGeneration) settle() }
    }

    private fun settle() {
        stopping = false
        val text = pendingText ?: return
        pendingText = null
        start(pendingId, text, afterStop = true)
    }

    private fun start(utteranceId: Int, text: String, afterStop: Boolean = false) {
        speakingId = utteranceId
        awaitingStart = afterStop
        engine.speak(text)
    }

    companion object {
        const val DEFAULT_SETTLE_MILLIS = 400L
    }
}
