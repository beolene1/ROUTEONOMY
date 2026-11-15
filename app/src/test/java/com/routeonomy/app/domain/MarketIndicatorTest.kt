package com.routeonomy.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MarketIndicatorTest {
    private fun fx(vararg values: Double, stale: Boolean = false) = MarketIndicator(IndicatorKind.USD_KRW,
        values.mapIndexed { i, v -> IndicatorPoint("2026-11-%02d".format(10 + i), v) }, stale)

    @Test fun changeComparesTheLastTwoObservations() {
        val indicator = fx(1334.1, 1337.13)
        assertEquals(3.03, indicator.change!!, 1e-9)
        assertTrue(indicator.rising)
        assertFalse(fx(1337.13, 1334.1).rising)
        assertNull(fx(1337.13).change)
    }

    @Test fun usdKrwText() {
        val indicator = fx(1334.1, 1337.13)
        assertEquals("1,337.1원", IndicatorText.value(indicator))
        assertEquals("+3.0원 · 11/11", IndicatorText.detail(indicator))
        assertEquals("저장값 · +3.0원 · 11/11", IndicatorText.detail(fx(1334.1, 1337.13, stale = true)))
        assertEquals("11/10 기준", IndicatorText.detail(fx(1337.13)))
    }

    @Test fun emptyIndicatorIsUnavailable() {
        val empty = MarketIndicator(IndicatorKind.USD_KRW, emptyList(), stale = true)
        assertEquals("조회 불가", IndicatorText.value(empty))
        assertEquals("원·달러 환율 조회 불가", IndicatorText.spoken(empty))
    }

    @Test fun spokenTextSaysDirectionAndSource() {
        assertEquals("원·달러 환율, 1,337.1원, 상승, +3.0원 · 11/11, ECB 기준환율",
            IndicatorText.spoken(fx(1334.1, 1337.13)))
        assertEquals("원·달러 환율, 1,334.1원, 하락, -3.0원 · 11/11, ECB 기준환율",
            IndicatorText.spoken(fx(1337.13, 1334.1)))
    }

    @Test fun dates() {
        assertEquals("11/3", IndicatorText.date("2026-11-03"))
        assertEquals("2026년 10월", IndicatorText.date("2026-10"))
    }

    @Test fun tenYearAndPpiText() {
        val yield10 = MarketIndicator(IndicatorKind.US_10Y,
            listOf(IndicatorPoint("2026-11-12", 5.10), IndicatorPoint("2026-11-13", 5.15)))
        assertEquals("5.15%", IndicatorText.value(yield10))
        assertEquals("+0.05%p · 11/13", IndicatorText.detail(yield10))
        val ppi = MarketIndicator(IndicatorKind.US_PPI,
            listOf(IndicatorPoint("2026-09", 3.1), IndicatorPoint("2026-10", 2.7)))
        assertEquals("+2.7%", IndicatorText.value(ppi))
        assertEquals("전년 동월 대비 · 2026년 10월", IndicatorText.detail(ppi))
        assertTrue(ppi.rising) // prices are still higher than a year ago, even though the rate slowed
        assertFalse(MarketIndicator(IndicatorKind.US_PPI, listOf(IndicatorPoint("2026-10", -0.4))).rising)
        assertEquals("+0.0%", IndicatorText.value(MarketIndicator(IndicatorKind.US_PPI, listOf(IndicatorPoint("2026-10", -0.02)))))
    }
}
