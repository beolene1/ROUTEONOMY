package com.routeonomy.app.data

import com.routeonomy.app.domain.IndicatorKind
import com.routeonomy.app.domain.IndicatorPoint
import java.io.IOException
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IndicatorSourcesTest {
    private val today = LocalDate.of(2026, 11, 15)

    // Shape of a real response (2026-11-15), shortened and with keys out of order.
    private val frankfurter = """{"amount":1.0,"base":"USD","start_date":"2026-11-10","end_date":"2026-11-13",
        "rates":{"2026-11-12":{"KRW":1334.1},"2026-11-10":{"KRW":1339.0},"2026-11-13":{"KRW":1337.13},"2026-11-11":{"KRW":1336.9}}}"""

    @Test fun frankfurterIsSortedOldestFirst() {
        val points = IndicatorParsers.frankfurter(frankfurter, "KRW")
        assertEquals(listOf("2026-11-10", "2026-11-11", "2026-11-12", "2026-11-13"), points.map { it.date })
        assertEquals(1337.13, points.last().value, 1e-9)
    }

    @Test fun usdKrwRequestsAboutSixWeeksWithUsdBase() {
        var requested = ""
        IndicatorSources.load(IndicatorKind.USD_KRW, today) { url -> requested = url; frankfurter }
        assertEquals("https://api.frankfurter.dev/v1/2026-10-01..?base=USD&symbols=KRW", requested)
    }

    @Test fun keepsOnlyTheChartWindow() {
        val rates = (1..30).joinToString(",") { day -> "\"2026-10-%02d\":{\"KRW\":%d}".format(day, 1300 + day) }
        val points = IndicatorSources.load(IndicatorKind.USD_KRW, today) { """{"rates":{$rates}}""" }
        assertEquals(IndicatorSources.CHART_POINTS, points.size)
        assertEquals("2026-10-30", points.last().date)
    }

    @Test(expected = IOException::class) fun emptyResponseIsAFailure() {
        IndicatorSources.load(IndicatorKind.USD_KRW, today) { """{"rates":{}}""" }
    }

    @Test fun codecRoundTripsAndToleratesBrokenData() {
        val points = listOf(IndicatorPoint("2026-11-12", 1334.1), IndicatorPoint("2026-11-13", 1337.13))
        assertEquals(points, IndicatorCodec.decode(IndicatorCodec.encode(points)))
        assertTrue(IndicatorCodec.decode(null).isEmpty())
        assertTrue(IndicatorCodec.decode("not json").isEmpty())
    }
}
