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

    // Two business days from the real feed (2026-11), trimmed to the fields that matter.
    private val treasury = """<feed><entry><content type="application/xml"><m:properties>
        <d:Id m:type="Edm.Int32">350</d:Id><d:NEW_DATE m:type="Edm.DateTime">2026-11-03T00:00:00</d:NEW_DATE>
        <d:BC_7YEAR m:type="Edm.Double">4.80</d:BC_7YEAR><d:BC_10YEAR m:type="Edm.Double">4.95</d:BC_10YEAR>
        <d:BC_10YEARDISPLAY m:type="Edm.Double">9.99</d:BC_10YEARDISPLAY></m:properties></content></entry>
        <entry><content type="application/xml"><m:properties>
        <d:Id m:type="Edm.Int32">349</d:Id><d:NEW_DATE m:type="Edm.DateTime">2026-11-02T00:00:00</d:NEW_DATE>
        <d:BC_10YEAR m:type="Edm.Double">4.91</d:BC_10YEAR></m:properties></content></entry></feed>"""

    @Test fun treasuryReadsTheTenYearColumnOnly() {
        assertEquals(listOf(IndicatorPoint("2026-11-02", 4.91), IndicatorPoint("2026-11-03", 4.95)),
            IndicatorParsers.treasury10y(treasury))
    }

    @Test fun tenYearMergesPreviousAndCurrentMonth() {
        val requested = mutableListOf<String>()
        val october = treasury.replace("2026-11-02", "2026-10-30").replace("2026-11-03", "2026-10-31")
        val points = IndicatorSources.load(IndicatorKind.US_10Y, today) { url ->
            requested += url.substringAfterLast("=")
            if (url.endsWith("202610")) october else treasury
        }
        assertEquals(listOf("202610", "202611"), requested)
        assertEquals(listOf("2026-10-30", "2026-10-31", "2026-11-02", "2026-11-03"), points.map { it.date })
    }

    private fun bls(vararg rows: String) =
        """{"status":"REQUEST_SUCCEEDED","Results":{"series":[{"seriesID":"WPUFD4","data":[${rows.joinToString(",")}]}]}}"""
    private fun row(year: Int, period: String, value: String) =
        """{"year":"$year","period":"$period","periodName":"x","value":"$value","footnotes":[{}]}"""

    @Test fun ppiIsYearOverYearPercent() {
        val json = bls(row(2026, "M10", "160.248"), row(2026, "M09", "159.535"), row(2025, "M13", "150.0"),
            row(2025, "M10", "156.0"), row(2025, "M09", "155.0"), row(2025, "M08", "154.0"))
        val points = IndicatorSources.load(IndicatorKind.US_PPI, today) { json }
        assertEquals(listOf("2026-09", "2026-10"), points.map { it.date })
        assertEquals((160.248 / 156.0 - 1) * 100, points.last().value, 1e-9)
        assertEquals("https://api.bls.gov/publicAPI/v1/timeseries/data/WPUFD4", IndicatorSources.blsUrl())
    }

    @Test(expected = IOException::class) fun blsRefusalIsAFailure() {
        IndicatorParsers.bls("""{"status":"REQUEST_NOT_PROCESSED","message":["daily threshold"],"Results":{}}""", "WPUFD4")
    }
}
