package com.routeonomy.app.pleos

import android.content.Context
import ai.pleos.playground.location.fused.FusedLocation
import ai.pleos.playground.location.fused.listener.FusedLocationListener
import ai.pleos.playground.location.fused.listener.ProximityAlertListener
import ai.pleos.playground.location.fused.model.LocationData
import ai.pleos.playground.location.fused.model.ProximityAlertEvent
import ai.pleos.playground.location.fused.model.ProximityAlertRequest
import ai.pleos.playground.location.fused.model.ProximityTransition
import com.routeonomy.app.domain.GeoMath
import com.routeonomy.app.domain.GeoPoint
import com.routeonomy.app.domain.Region

/**
 * AVD experiment: does Pleos FusedLocation follow the emulator GPS (Extended Controls / GPX playback)
 * even though the built-in navi's position is fixed? Also registers a proximity alert for one region
 * to see whether ENTER/EXIT arrive. Requires runtime permission FUSED_LOCATION (dangerous).
 */
class FusedLocationProbe(context: Context, private val region: Region?, private val log: (String) -> Unit) {
    private val fused = FusedLocation(context.applicationContext)
    private var running = false
    private var lastLogMillis = 0L
    private var alertId: String? = null

    private val locationListener = object : FusedLocationListener {
        override fun onChanged(location: LocationData?) {
            if (location == null) { log("Pleos 위치: null"); return }
            val now = System.currentTimeMillis()
            if (now - lastLogMillis < 3000) return // keep the on-screen log readable; Logcat gets every 3 s
            lastLogMillis = now
            val here = GeoPoint(location.latitude, location.longitude)
            val toRegion = region?.let { " · ${it.name}까지 ${GeoMath.distanceMeters(here, it.center).toInt()}m" } ?: ""
            log("Pleos 위치: %.5f, %.5f heading=%.0f%s".format(location.latitude, location.longitude, location.heading, toRegion))
        }
        override fun onError(exception: Exception) = log("Pleos 위치 오류: ${exception.javaClass.simpleName}: ${exception.message}")
    }

    private val alertListener = object : ProximityAlertListener {
        override fun onRegistered(alertId: String) { this@FusedLocationProbe.alertId = alertId; log("근접 알림 등록됨: ${region?.name} ($alertId)") }
        override fun onTransition(event: ProximityAlertEvent) = log("★ 근접 알림: ${region?.name} ${event.transition}")
        override fun onError(exception: Exception) = log("근접 알림 오류: ${exception.javaClass.simpleName}: ${exception.message}")
    }

    fun isRunning() = running

    fun start() {
        if (running) return
        if (!fused.isSupported()) { log("Pleos 위치 서비스 없음(isSupported=false)"); return }
        try {
            fused.initialize()
            fused.registerFusedLocationCallback(locationListener)
            region?.let {
                fused.addProximityAlert(
                    ProximityAlertRequest(it.center.latitude, it.center.longitude, it.radiusMeters.toFloat(),
                        setOf(ProximityTransition.ENTER, ProximityTransition.EXIT)),
                    alertListener,
                )
            }
            running = true
            log("Pleos 위치 수신 시작")
        } catch (e: Exception) {
            log("Pleos 위치 시작 실패: ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    fun stop() {
        if (!running) return
        runCatching {
            alertId?.let(fused::removeProximityAlert)
            fused.unregisterFusedLocationCallback(locationListener)
        }
        fused.release()
        alertId = null
        running = false
        log("Pleos 위치 수신 중지")
    }
}
