package com.routeonomy.app.data

import com.routeonomy.app.domain.IndicatorKind
import com.routeonomy.app.domain.IndicatorPoint
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import org.json.JSONArray
import org.json.JSONObject

/**
 * Public, keyless sources for the home indicators. The app calls them directly (no server, no API key).
 * [get] downloads a URL as text; tests pass a fake.
 */
object IndicatorSources {
    /** How many observations the card's line chart shows. */
    const val CHART_POINTS = 20

    fun load(kind: IndicatorKind, today: LocalDate, get: (String) -> String): List<IndicatorPoint> = when (kind) {
        IndicatorKind.USD_KRW -> IndicatorParsers.frankfurter(get(frankfurterUrl(today)), "KRW")
        IndicatorKind.US_10Y -> throw UnsupportedOperationException("not connected yet")
        IndicatorKind.US_PPI -> throw UnsupportedOperationException("not connected yet")
    }.takeLast(CHART_POINTS).also { if (it.isEmpty()) throw IOException("no observations for $kind") }

    /** ECB reference rates converted to a USD base by Frankfurter; about six weeks gives 20+ business days. */
    fun frankfurterUrl(today: LocalDate): String =
        "https://api.frankfurter.dev/v1/${today.minusDays(45)}..?base=USD&symbols=KRW"

    /** Plain HTTPS GET with short timeouts; redirects within HTTPS are followed. */
    fun httpGet(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 8_000
            connection.readTimeout = 10_000
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("User-Agent", "Routeonomy/1.0 (Android)")
            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_OK) throw IOException("HTTP $code for $url")
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}

/** Response parsers. Each returns observations oldest first. */
object IndicatorParsers {
    /** Frankfurter time series: `{"rates":{"2026-11-12":{"KRW":1334.1}, ...}}`. */
    fun frankfurter(json: String, symbol: String): List<IndicatorPoint> {
        val rates = JSONObject(json).getJSONObject("rates")
        return rates.keys().asSequence()
            .mapNotNull { date -> rates.getJSONObject(date).optDouble(symbol).takeUnless { it.isNaN() }?.let { IndicatorPoint(date, it) } }
            .sortedBy { it.date }
            .toList()
    }
}

/** Saved observations as JSON in SharedPreferences. */
object IndicatorCodec {
    fun encode(points: List<IndicatorPoint>): String =
        JSONArray().apply { points.forEach { put(JSONObject().put("d", it.date).put("v", it.value)) } }.toString()

    fun decode(json: String?): List<IndicatorPoint> {
        if (json.isNullOrEmpty()) return emptyList()
        return try {
            val array = JSONArray(json)
            (0 until array.length()).map { array.getJSONObject(it).let { o -> IndicatorPoint(o.getString("d"), o.getDouble("v")) } }
        } catch (e: org.json.JSONException) {
            emptyList()
        }
    }
}
