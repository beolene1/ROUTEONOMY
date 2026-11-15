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

    /** Monthly PPI: a year of year-over-year rates. */
    const val PPI_CHART_POINTS = 12

    /** US PPI final demand (BLS series WPUFD4); the card shows its year-over-year change. */
    const val PPI_SERIES = "WPUFD4"

    fun load(kind: IndicatorKind, today: LocalDate, get: (String) -> String): List<IndicatorPoint> = when (kind) {
        IndicatorKind.USD_KRW -> IndicatorParsers.frankfurter(get(frankfurterUrl(today)), "KRW").takeLast(CHART_POINTS)
        // This month alone is too short early in the month, so the previous month is merged in.
        IndicatorKind.US_10Y -> listOf(today.minusMonths(1), today)
            .flatMap { IndicatorParsers.treasury10y(get(treasuryUrl(it))) }
            .distinctBy { it.date }.sortedBy { it.date }.takeLast(CHART_POINTS)
        IndicatorKind.US_PPI -> IndicatorParsers.yearOverYear(IndicatorParsers.bls(get(blsUrl()), PPI_SERIES))
            .takeLast(PPI_CHART_POINTS)
    }.also { if (it.isEmpty()) throw IOException("no observations for $kind") }

    /** Daily Treasury par yield curve rates for one month (XML feed, no key). */
    fun treasuryUrl(month: LocalDate): String =
        "https://home.treasury.gov/resource-center/data-chart-center/interest-rates/pages/xml" +
            "?data=daily_treasury_yield_curve&field_tdr_date_value_month=" +
            String.format(java.util.Locale.US, "%04d%02d", month.year, month.monthValue)

    /** BLS public API v1: no key, up to 25 requests a day, the last three years of monthly values. */
    fun blsUrl(): String = "https://api.bls.gov/publicAPI/v1/timeseries/data/$PPI_SERIES"

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

    private val treasuryDate = Regex("""<d:NEW_DATE[^>]*>(\d{4}-\d{2}-\d{2})""")
    // The tag name must end right there: BC_10YEARDISPLAY is a different field.
    private val treasury10y = Regex("""<d:BC_10YEAR(?:\s[^>]*)?>([0-9.]+)</d:BC_10YEAR>""")

    /** Treasury XML: one `<m:properties>` block per business day with NEW_DATE and BC_10YEAR (percent). */
    fun treasury10y(xml: String): List<IndicatorPoint> =
        xml.split("<m:properties>").drop(1).mapNotNull { day ->
            val date = treasuryDate.find(day)?.groupValues?.get(1) ?: return@mapNotNull null
            val value = treasury10y.find(day)?.groupValues?.get(1)?.toDoubleOrNull() ?: return@mapNotNull null
            IndicatorPoint(date, value)
        }.sortedBy { it.date }

    /** BLS v1 JSON: monthly values as `yyyy-MM` points. Annual averages (M13) are skipped. */
    fun bls(json: String, series: String): List<IndicatorPoint> {
        val root = JSONObject(json)
        if (root.optString("status") != "REQUEST_SUCCEEDED") throw IOException("BLS: ${root.optJSONArray("message")}")
        val all = root.getJSONObject("Results").getJSONArray("series")
        val data = (0 until all.length()).map { all.getJSONObject(it) }
            .firstOrNull { it.optString("seriesID") == series }?.getJSONArray("data") ?: return emptyList()
        return (0 until data.length()).mapNotNull { i ->
            val row = data.getJSONObject(i)
            val month = row.optString("period").removePrefix("M").toIntOrNull()?.takeIf { it in 1..12 } ?: return@mapNotNull null
            val value = row.optString("value").toDoubleOrNull() ?: return@mapNotNull null
            IndicatorPoint(String.format(java.util.Locale.US, "%s-%02d", row.optString("year"), month), value)
        }.sortedBy { it.date }
    }

    /** Percent change from the same month a year earlier, for each month that has one. */
    fun yearOverYear(monthly: List<IndicatorPoint>): List<IndicatorPoint> {
        val byMonth = monthly.associate { it.date to it.value }
        return monthly.mapNotNull { point ->
            val (year, month) = point.date.split("-")
            val before = byMonth["${year.toInt() - 1}-$month"] ?: return@mapNotNull null
            IndicatorPoint(point.date, (point.value / before - 1) * 100)
        }
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
