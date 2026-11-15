package com.routeonomy.app.data

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.edit
import com.routeonomy.app.domain.IndicatorKind
import com.routeonomy.app.domain.MarketIndicator
import java.time.LocalDate
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Looks up the home indicators in the background and keeps the last successful values on the device.
 * Each indicator is fetched on its own, so one failing source does not block the others.
 */
class IndicatorRepository(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("market_indicators", Context.MODE_PRIVATE)
    private val executor: ExecutorService = Executors.newFixedThreadPool(3)
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var closed = false

    /** Last saved values (marked stale), or an empty indicator when nothing was saved yet. */
    fun cached(kind: IndicatorKind): MarketIndicator =
        MarketIndicator(kind, IndicatorCodec.decode(prefs.getString(kind.name, null)), stale = true)

    /**
     * Fetches every kind and reports each result on the main thread: fresh values on success,
     * otherwise the saved values (stale) or an empty indicator.
     */
    fun refresh(kinds: List<IndicatorKind>, listener: (MarketIndicator) -> Unit) {
        for (kind in kinds) {
            executor.execute {
                val result = try {
                    val points = IndicatorSources.load(kind, LocalDate.now(), IndicatorSources::httpGet)
                    prefs.edit { putString(kind.name, IndicatorCodec.encode(points)) }
                    MarketIndicator(kind, points)
                } catch (e: Exception) { // network, HTTP, or parse failure: fall back to saved values
                    Log.w(TAG, "indicator $kind lookup failed: ${e.message}")
                    cached(kind)
                }
                main.post { if (!closed) listener(result) }
            }
        }
    }

    fun close() {
        closed = true
        executor.shutdownNow()
    }

    private companion object {
        const val TAG = "IndicatorRepository"
    }
}
