package com.routeonomy.app.pleos

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.os.Handler
import android.os.Looper

/**
 * AVD experiment: watches the system's active audio playbacks (public Android API, not Pleos SDK)
 * to see whether navigation voice guidance is observable, so briefing playback could pause around it.
 * Reports every change of the active usage set and whether navigation guidance is playing.
 */
class NaviVoiceMonitor(context: Context, private val onChange: (Boolean, String) -> Unit) {
    private val audio = context.getSystemService(AudioManager::class.java)
    private var lastSummary: String? = null
    private val callback = object : AudioManager.AudioPlaybackCallback() {
        override fun onPlaybackConfigChanged(configs: MutableList<AudioPlaybackConfiguration>) = report(configs)
    }

    fun start() {
        audio.registerAudioPlaybackCallback(callback, Handler(Looper.getMainLooper()))
        report(audio.activePlaybackConfigurations)
    }

    fun close() = audio.unregisterAudioPlaybackCallback(callback)

    private fun report(configs: List<AudioPlaybackConfiguration>) {
        val usages = configs.map { it.audioAttributes.usage }
        val summary = usages.map(::usageName).sorted().joinToString(", ").ifEmpty { "없음" }
        if (summary == lastSummary) return
        lastSummary = summary
        onChange(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE in usages, summary)
    }

    private fun usageName(usage: Int): String = when (usage) {
        AudioAttributes.USAGE_MEDIA -> "MEDIA"
        AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE -> "NAVI_GUIDANCE"
        AudioAttributes.USAGE_ASSISTANT -> "ASSISTANT"
        AudioAttributes.USAGE_NOTIFICATION -> "NOTIFICATION"
        AudioAttributes.USAGE_ASSISTANCE_SONIFICATION -> "SONIFICATION"
        else -> "usage$usage"
    }
}
