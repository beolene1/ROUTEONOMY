package com.routeonomy.app.pleos

import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.constant.ErrorType
import ai.pleos.playground.tts.constant.Mode
import ai.pleos.playground.tts.listener.EventListener
import ai.pleos.playground.tts.listener.OnInitializeListener
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.routeonomy.app.domain.SpeechOutput
import java.util.Locale

/**
 * Pleos TTS 3.3.1 (ON_DEVICE) adapter: initialize, speak, stop and release.
 * SDK callbacks are moved to the main thread and ordered by [SpeechGate]; [Listener] is called on the main thread.
 * Callback order is logged under [TAG] so the stop/done sequence can be checked with logcat on the AVD.
 */
class PleosTtsSpeaker(context: Context, private val listener: Listener) : SpeechOutput {
    interface Listener {
        fun onReady()
        fun onInitFailed(message: String)
        fun onDone(utteranceId: Int)
        fun onError(utteranceId: Int, message: String)
    }

    private val main = Handler(Looper.getMainLooper())
    private val tts = TextToSpeech(context.applicationContext, Mode.ON_DEVICE)
    private var released = false

    private val gate = SpeechGate(
        object : SpeechGate.Engine {
            override fun speak(text: String) { Log.d(TAG, "speak"); tts.speak(text, true) }
            override fun stop() { Log.d(TAG, "stop"); tts.stop() }
        },
        { delay, task -> main.postDelayed({ if (!released) task() }, delay) },
        object : SpeechGate.Listener {
            override fun onDone(utteranceId: Int) = listener.onDone(utteranceId)
            override fun onError(utteranceId: Int, message: String) = listener.onError(utteranceId, message)
        },
    )

    private val events = object : EventListener {
        override fun onStart() { Log.d(TAG, "onStart"); onMain { gate.onEngineStarted() } }
        override fun onReady() { }
        override fun onDone() { Log.d(TAG, "onDone"); onMain { gate.onEngineDone() } }
        override fun onError(error: ErrorType) { Log.d(TAG, "onError $error"); onMain { gate.onEngineError(error.toString()) } }
        override fun onStop() { Log.d(TAG, "onStop"); onMain { gate.onEngineStopped() } }
        override fun onUpdatedRms(rms: Double) { }
    }

    fun initialize() {
        tts.initialize(object : OnInitializeListener {
            override fun onSuccess() {
                if (released) return
                tts.addEventListener(events)
                tts.setLocale(Locale.KOREA)
                onMain { listener.onReady() }
            }
            override fun onFailure(error: ErrorType) = onMain { listener.onInitFailed(error.toString()) }
        })
    }

    override fun speak(utteranceId: Int, text: String) { if (!released) gate.speak(utteranceId, text) }
    override fun stop() { if (!released) gate.stop() }

    fun release() {
        if (released) return
        released = true
        runCatching { tts.removeEventListener(events) }
        runCatching { tts.stop() }
        runCatching { tts.release() }
    }

    private fun onMain(block: () -> Unit) { main.post { if (!released) block() } }

    companion object {
        const val TAG = "RouteonomyTts"
    }
}
