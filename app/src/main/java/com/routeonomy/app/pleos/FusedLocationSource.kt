package com.routeonomy.app.pleos

import android.content.Context
import ai.pleos.playground.location.fused.FusedLocation
import ai.pleos.playground.location.fused.listener.FusedLocationListener
import ai.pleos.playground.location.fused.listener.ProximityAlertListener
import ai.pleos.playground.location.fused.model.LocationData
import ai.pleos.playground.location.fused.model.ProximityAlertEvent
import ai.pleos.playground.location.fused.model.ProximityAlertRequest
import ai.pleos.playground.location.fused.model.ProximityTransition
import com.routeonomy.app.domain.GeoPoint
import com.routeonomy.app.domain.Geofence

/**
 * Pleos FusedLocation adapter: vehicle position plus ENTER/EXIT proximity alerts for each fence.
 * Verified on the AVD (2026-11-12): follows Extended Controls location and GPX playback (about every 3 s).
 * Requires the runtime permission [PERMISSION]. Callbacks may arrive off the main thread.
 */
class FusedLocationSource(context: Context, private val fences: List<Geofence>, private val listener: Listener) {
    interface Listener {
        fun onLocation(point: GeoPoint)
        fun onFenceTransition(fenceId: String, entered: Boolean)
        fun onStatus(message: String)
    }

    companion object {
        const val PERMISSION = "pleos.car.permission.FUSED_LOCATION"
    }

    private val fused = FusedLocation(context.applicationContext)
    private val alertIds = mutableListOf<String>()
    @Volatile private var running = false

    private val locationListener = object : FusedLocationListener {
        override fun onChanged(location: LocationData?) {
            if (location != null) listener.onLocation(GeoPoint(location.latitude, location.longitude))
        }
        override fun onError(exception: Exception) = listener.onStatus("Pleos 위치 오류: ${exception.message}")
    }

    fun isRunning() = running

    fun start() {
        if (running) return
        if (!fused.isSupported()) { listener.onStatus("Pleos 위치 서비스 없음"); return }
        try {
            fused.initialize()
            running = true // from here on stop() must undo whatever got registered
            fused.registerFusedLocationCallback(locationListener)
            fences.forEach(::addAlert)
            listener.onStatus("Pleos 위치 수신 중")
        } catch (e: Exception) {
            stop()
            listener.onStatus("Pleos 위치 시작 실패: ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    /** One listener per fence, so a transition maps to its fence without tracking alert ids. */
    private fun addAlert(fence: Geofence) {
        fused.addProximityAlert(
            ProximityAlertRequest(fence.center.latitude, fence.center.longitude, fence.radiusMeters.toFloat(),
                setOf(ProximityTransition.ENTER, ProximityTransition.EXIT)),
            object : ProximityAlertListener {
                override fun onRegistered(alertId: String) {
                    synchronized(alertIds) {
                        if (running) alertIds += alertId
                        else runCatching { fused.removeProximityAlert(alertId) } // confirmed after stop()
                    }
                }
                override fun onTransition(event: ProximityAlertEvent) =
                    listener.onFenceTransition(fence.id, event.transition == ProximityTransition.ENTER)
                override fun onError(exception: Exception) = listener.onStatus("근접 알림 오류(${fence.id}): ${exception.message}")
            },
        )
    }

    fun stop() {
        synchronized(alertIds) {
            if (!running) return
            running = false
            alertIds.forEach { runCatching { fused.removeProximityAlert(it) } }
            alertIds.clear()
        }
        runCatching { fused.unregisterFusedLocationCallback(locationListener) }
        runCatching { fused.release() }
    }
}
