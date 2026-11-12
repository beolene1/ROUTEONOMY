package com.routeonomy.app.pleos

import android.app.Activity
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager

/**
 * AVD experiment (public Android APIs, not Pleos SDK):
 * 1) open the navigation app in the adjacent window instead of full screen;
 * 2) hold media audio focus so navigation voice guidance (MAY_DUCK per Pleos audio policy)
 *    shows up as a focus-loss event we could pause the briefing on.
 */
class WindowAndFocusProbe(private val activity: Activity, private val log: (String) -> Unit) {
    private val audio = activity.getSystemService(AudioManager::class.java)
    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build(),
        )
        .setOnAudioFocusChangeListener { change -> log("오디오 포커스 변화: ${focusName(change)}") }
        .build()
    private var holdingFocus = false

    fun openNaviAdjacent() {
        val query = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MAPS)
        val target = activity.packageManager.queryIntentActivities(query, 0).firstOrNull()
        if (target == null) { log("APP_MAPS 앱을 찾지 못함"); return }
        val info = target.activityInfo
        log("내비 옆 창 열기 시도: ${info.packageName}/${info.name}")
        val launch = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MAPS)
            .setClassName(info.packageName, info.name)
            .addFlags(Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT or Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            activity.startActivity(launch)
            activity.window.decorView.postDelayed({ log("우리 앱 멀티윈도우 상태: ${activity.isInMultiWindowMode}") }, 1500)
        } catch (e: Exception) {
            log("내비 열기 실패: ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    fun toggleFocus() {
        if (holdingFocus) {
            audio.abandonAudioFocusRequest(focusRequest)
            holdingFocus = false
            log("오디오 포커스 반납")
        } else {
            val result = audio.requestAudioFocus(focusRequest)
            holdingFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            log("오디오 포커스 요청 결과: ${if (holdingFocus) "GRANTED" else "거절($result)"}")
        }
    }

    fun close() {
        if (holdingFocus) audio.abandonAudioFocusRequest(focusRequest)
        holdingFocus = false
    }

    private fun focusName(change: Int) = when (change) {
        AudioManager.AUDIOFOCUS_GAIN -> "GAIN(다시 얻음)"
        AudioManager.AUDIOFOCUS_LOSS -> "LOSS(완전히 잃음)"
        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> "LOSS_TRANSIENT(잠깐 멈춰야 함)"
        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> "LOSS_TRANSIENT_CAN_DUCK(볼륨 낮춤 · 내비 안내 가능성)"
        else -> "기타($change)"
    }
}
