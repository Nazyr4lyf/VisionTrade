package com.visiontrade.domain

import com.visiontrade.domain.indicators.IndicatorEngine
import com.visiontrade.domain.models.Candle
import com.visiontrade.domain.models.DataStatus
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class IndicatorTests {

    private val engine = IndicatorEngine()

    @Test
    fun testEMACalculation() {
        val candles = (1..20).map { i ->
            val close = 100.0 + i
            Candle(
                open = close - 1.0,
                high = close + 2.0,
                low = close - 2.0,
                close = close
            )
        }

        val ema20 = engine.calculateEMA(candles, 20)

        assertEquals(DataStatus.AVAILABLE, ema20.status)
        assertNotNull(ema20.value)
        assertTrue(ema20.value!! > 100.0)
        assertTrue(ema20.value!! < 130.0)
    }

    @Test
    fun testRSICalculation() {
        val candles = (1..20).map { i ->
            val close = 100.0 + i
            Candle(
                open = close - 1.0,
                high = close + 2.0,
                low = close - 2.0,
                close = close
            )
        }

        val rsi = engine.calculateRSI(candles, 14)

        assertEquals(DataStatus.AVAILABLE, rsi.status)
        assertNotNull(rsi.value)
        assertTrue(rsi.value!! >= 0.0)
        assertTrue(rsi.value!! <= 100.0)
    }

    @Test
    fun testADXCalculation() {
        val candles = (1..40).map { i ->
            val base = 100.0 + i

            Candle(
                open = base,
                high = base + 5.0,
                low = base - 2.0,
                close = base + 2.0
            )
        }

        val adx = engine.calculateADX(candles, 14)

        assertEquals(DataStatus.AVAILABLE, adx.status)
        assertNotNull(adx.value)
        assertTrue(adx.value!! >= 0.0)
        assertTrue(adx.value!! <= 100.0)
    }

    @Test
    fun testInsufficientDataEMA() {
        val candles = listOf(
            Candle(
                open = 100.0,
                high = 105.0,
                low = 99.0,
                close = 102.0
            ),
            Candle(
                open = 102.0,
                high = 107.0,
                low = 101.0,
                close = 105.0
            )
        )

        val ema20 = engine.calculateEMA(candles, 20)

        assertEquals(DataStatus.UNAVAILABLE, ema20.status)
        assertEquals(null, ema20.value)
    }

    @Test
    fun testInsufficientDataRSI() {
        val candles = listOf(
            Candle(
                open = 100.0,
                high = 105.0,
                low = 99.0,
                close = 102.0
            )
        )

        val rsi = engine.calculateRSI(candles, 14)

        assertEquals(DataStatus.UNAVAILABLE, rsi.status)
        assertEquals(null, rsi.value)
    }

    @Test
    fun testInsufficientDataADX() {
        val candles = listOf(
            Candle(
                open = 100.0,
                high = 105.0,
                low = 99.0,
                close = 102.0
            )
        )

        val adx = engine.calculateADX(candles, 14)

        assertEquals(DataStatus.UNAVAILABLE, adx.status)
        assertEquals(null, adx.value)
    }
}
