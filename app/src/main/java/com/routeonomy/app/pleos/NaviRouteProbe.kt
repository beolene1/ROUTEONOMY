package com.routeonomy.app.pleos

import android.content.Context
import android.util.Log
import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.data.CurrentLocationInfo
import ai.pleos.playground.navi.data.DestinationArrivedInfo
import ai.pleos.playground.navi.data.DestinationInfo
import ai.pleos.playground.navi.data.DrivingInfo
import ai.pleos.playground.navi.constants.NavigationStatus
import ai.pleos.playground.navi.data.RouteInfo
import ai.pleos.playground.navi.data.RouteStartInfo
import ai.pleos.playground.navi.data.RouteStateInfo
import ai.pleos.playground.navi.data.TBTInfo
import ai.pleos.playground.navi.data.TripInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * AVD experiment (not product code yet): asks the Pleos navi to route to a place and records which
 * NaviHelper events actually arrive, so we can decide whether real route/location/arrival can replace
 * the Fake inputs. Connects lazily on the first button press. Logcat tag: [TAG].
 */
class NaviRouteProbe(context: Context, private val onLog: (String) -> Unit) {
    private val helper = NaviHelper(context.applicationContext)
    private var state = State.IDLE
    private val pending = mutableListOf<() -> Unit>()
    private val lines = ArrayDeque<String>()
    private val time = SimpleDateFormat("HH:mm:ss", Locale.US)

    private enum class State { IDLE, CONNECTING, CONNECTED }

    private val listener = object : NaviHelperEventListener {
        override fun onRouteStarted(routeStartInfo: RouteStartInfo) =
            log("경로 시작: ${routeStartInfo.name}, ${routeStartInfo.duration}s, ${routeStartInfo.distance}m")
        override fun onRoutingCompleted() = log("경로 탐색 완료")
        override fun onRouteEnded() = log("경로 종료")
        override fun onRouteCancelled() = log("경로 취소됨")
        override fun onReRouteEnded() = log("재탐색 완료")
        override fun onDestinationChanged() = log("목적지 변경 알림")
        override fun onDestinationArrived(destinationArrivedInfo: DestinationArrivedInfo) =
            log("★ 목적지 도착: ${destinationArrivedInfo.name}")
        override fun onDestinationInfo(destinationInfo: DestinationInfo) =
            log("목적지 정보: ${destinationInfo.name}, remainTime=${destinationInfo.remainTime}, remainDistance=${destinationInfo.remainDistance}m")
        override fun onCurrentLocationInfo(currentLocationInfo: CurrentLocationInfo) =
            log("현재 위치: %.5f, %.5f (%s)".format(currentLocationInfo.latitude, currentLocationInfo.longitude, currentLocationInfo.address))
        override fun onRouteStateInfo(routeStateInfo: RouteStateInfo) =
            log("경로 상태: mode=${routeStateInfo.naviMode}, routing=${routeStateInfo.isRouting}, 약관=${routeStateInfo.termsAgreement}")
        override fun onNavigationStatus(status: NavigationStatus) = log("내비 상태: $status")
        override fun onDrivingInfo(drivingInfo: DrivingInfo) = log("주행 정보: $drivingInfo")
        override fun onTripInfo(tripInfo: TripInfo) = log("trip: eta=${tripInfo.eta}, distance=${tripInfo.distance}")
        override fun onTBTInfo(tbtInfoList: List<TBTInfo>) {
            // Pushed often during guidance: log only when the next turn changes.
            val next = tbtInfoList.firstOrNull()?.let { "${it.type} · ${it.description}" } ?: "없음"
            if (next != lastTbt) { lastTbt = next; log("다음 안내(TBT ${tbtInfoList.size}개): $next") }
        }
        override fun onError(errorCode: NaviErrorCode) = log("오류: $errorCode")
    }
    private var lastTbt: String? = null

    /** Asks the navi to start a route to the given place (permission NAVI_ROUTE_SEARCH). */
    fun requestRoute(latitude: Double, longitude: Double, name: String) = whenConnected {
        log("경로 요청 → $name")
        helper.requestRoute(RouteInfo(latitude = latitude, longitude = longitude, poiName = name, poiId = "", address = "", poiSubId = ""))
    }

    /** Polls the state the navi currently holds (permission NAVI_ROUTE). */
    fun queryState() = whenConnected {
        log("상태 조회")
        helper.getRouteStateInfo()
        helper.getDestinationInfo()
        helper.getCurrentLocationInfo()
        lastTbt = null
        helper.getTBTInfo()
    }

    fun cancelRoute() = whenConnected {
        log("경로 취소 요청")
        helper.cancelRoute()
    }

    fun close() {
        if (state == State.CONNECTED) helper.removeListener(listener)
        if (state != State.IDLE) helper.release()
        state = State.IDLE
        pending.clear()
    }

    private fun whenConnected(action: () -> Unit) {
        when (state) {
            State.CONNECTED -> runSafely(action)
            State.CONNECTING -> pending += action
            State.IDLE -> {
                state = State.CONNECTING
                pending += action
                log("NaviHelper 연결 중")
                helper.initialize(
                    onConnected = {
                        state = State.CONNECTED
                        helper.addListener(listener)
                        log("NaviHelper 연결됨")
                        pending.toList().also { pending.clear() }.forEach(::runSafely)
                    },
                    onFailed = { error ->
                        state = State.IDLE
                        pending.clear()
                        log("NaviHelper 연결 실패: ${error.message ?: error.javaClass.simpleName}")
                    },
                )
            }
        }
    }

    private fun runSafely(action: () -> Unit) {
        try {
            action()
        } catch (e: Exception) {
            log("호출 예외: ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    /** Lets other experiment sources (e.g. the audio monitor) share the on-screen log. */
    fun note(message: String) = log(message)

    private fun log(message: String) {
        Log.i(TAG, message)
        synchronized(lines) {
            lines.addFirst("${time.format(Date())} $message")
            while (lines.size > 14) lines.removeLast()
            onLog(lines.joinToString("\n"))
        }
    }

    companion object {
        const val TAG = "RouteonomyNavi"
    }
}
