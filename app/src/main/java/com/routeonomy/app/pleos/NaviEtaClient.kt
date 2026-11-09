package com.routeonomy.app.pleos

import android.content.Context
import ai.pleos.playground.navi.data.DestinationInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener

/** Requests destination information and forwards the SDK's raw remainTime/remainDistance. */
class NaviEtaClient(context: Context, private val onInfo: (Int, Int) -> Unit, private val onError: (String) -> Unit) {
    private val helper = NaviHelper(context.applicationContext)
    private var initialized = false
    private val listener = object : NaviHelperEventListener {
        override fun onDestinationInfo(destinationInfo: DestinationInfo) {
            onInfo(destinationInfo.remainTime, destinationInfo.remainDistance)
        }
        override fun onError(errorCode: ai.pleos.playground.navi.constants.NaviErrorCode) {
            onError("NaviHelper 오류: $errorCode")
        }
    }

    fun start() {
        helper.initialize(
            onConnected = {
                initialized = true
                helper.addListener(listener)
                helper.getDestinationInfo()
            },
            onFailed = { error -> onError("NaviHelper 연결 실패: ${error.message ?: error.javaClass.simpleName}") },
        )
    }

    fun refresh() {
        if (initialized) helper.getDestinationInfo() else onError("NaviHelper 연결 대기 중입니다.")
    }

    fun close() {
        if (initialized) helper.removeListener(listener)
        helper.release()
        initialized = false
    }
}
