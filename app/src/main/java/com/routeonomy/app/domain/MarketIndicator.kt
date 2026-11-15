package com.routeonomy.app.domain

import java.util.Locale
import kotlin.math.abs

/** Public economic indicators shown on the home screen. Each has its own release cycle and source. */
enum class IndicatorKind(val title: String, val source: String) {
    USD_KRW("원·달러 환율", "ECB 기준환율"),
    US_10Y("미국 10년물 국채금리", "미국 재무부"),
    US_PPI("미국 생산자물가", "미국 노동통계국"),
}

/** One observation. [date] is `yyyy-MM-dd` for daily series and `yyyy-MM` for monthly ones. */
data class IndicatorPoint(val date: String, val value: Double)

/**
 * The latest observations of one indicator, oldest first.
 * [stale] means these are the last saved values because the latest lookup failed.
 */
data class MarketIndicator(val kind: IndicatorKind, val points: List<IndicatorPoint>, val stale: Boolean = false) {
    val latest: IndicatorPoint? get() = points.lastOrNull()
    val previous: IndicatorPoint? get() = points.getOrNull(points.size - 2)

    /** Change from the previous observation, or null with fewer than two points. */
    val change: Double? get() {
        val last = latest ?: return null
        val before = previous ?: return null
        return last.value - before.value
    }

    /** Arrow direction: the US PPI card shows a year-over-year rate, so its sign is what matters. */
    val rising: Boolean get() = if (kind == IndicatorKind.US_PPI) (latest?.value ?: 0.0) >= 0 else (change ?: 0.0) >= 0
}

/** Korean card text for an indicator. Kept out of the UI so it can be unit tested. */
object IndicatorText {
    fun value(indicator: MarketIndicator): String {
        val v = indicator.latest?.value ?: return "조회 불가"
        return when (indicator.kind) {
            IndicatorKind.USD_KRW -> String.format(Locale.US, "%,.1f원", v)
            IndicatorKind.US_10Y -> String.format(Locale.US, "%.2f%%", v)
            IndicatorKind.US_PPI -> String.format(Locale.US, "%+.1f%%", if (abs(v) < 0.05) 0.0 else v) // no "-0.0%"
        }
    }

    /** e.g. "+3.2원 · 11/13" (vs. the previous observation), "+0.05%p · 11/13", "전년 동월 대비 · 2026년 10월". */
    fun detail(indicator: MarketIndicator): String {
        val last = indicator.latest ?: return "저장된 값이 없어요"
        val date = date(last.date)
        val change = indicator.change
        val prefix = if (indicator.stale) "저장값 · " else ""
        return prefix + when (indicator.kind) {
            IndicatorKind.USD_KRW ->
                if (change == null) "$date 기준" else String.format(Locale.US, "%+.1f원 · %s", change, date)
            IndicatorKind.US_10Y ->
                if (change == null) "$date 기준" else String.format(Locale.US, "%+.2f%%p · %s", change, date)
            IndicatorKind.US_PPI -> "전년 동월 대비 · $date"
        }
    }

    /** Screen reader text: says whether the value went up or down and that it may be a saved value. */
    fun spoken(indicator: MarketIndicator): String {
        if (indicator.latest == null) return "${indicator.kind.title} 조회 불가"
        val direction = when {
            indicator.kind == IndicatorKind.US_PPI -> if (indicator.rising) "상승" else "하락"
            indicator.change == null -> ""
            abs(indicator.change!!) < 1e-9 -> "보합"
            indicator.rising -> "상승"
            else -> "하락"
        }
        return listOf(indicator.kind.title, value(indicator), direction, detail(indicator), indicator.kind.source)
            .filter { it.isNotEmpty() }.joinToString(", ")
    }

    /** `2026-11-13` → `11/13`, `2026-10` → `2026년 10월`. */
    fun date(date: String): String {
        val parts = date.split("-")
        return when (parts.size) {
            3 -> "${parts[1].toInt()}/${parts[2].toInt()}"
            2 -> "${parts[0]}년 ${parts[1].toInt()}월"
            else -> date
        }
    }
}
