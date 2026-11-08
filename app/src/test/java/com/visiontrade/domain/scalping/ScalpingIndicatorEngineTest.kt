package com.visiontrade.domain.scalping

import java.math.BigDecimal
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ScalpingIndicatorEngineTest {

    private val engine = ScalpingIndicatorEngine()

    @Test
    fun emaUsesSmaSeedAndReturnsNullForInsufficientHistory() {
        assertEquals(2.0, engine.ema(listOf(1.0, 2.0, 3.0), 3)!!, 0.000001)
        assertNull(engine.ema(listOf(1.0, 2.0), 3))
    }

    @Test
    fun rsiHandlesRisingFallingAndFlatMarkets() {
        assertEquals(100.0, engine.rsi((1..10).map { it.toDouble() }, 3)!!, 0.000001)
        assertEquals(0.0, engine.rsi((10 downTo 1).map { it.toDouble() }, 3)!!, 0.000001)
        assertEquals(50.0, engine.rsi(List(10) { 5.0 }, 3)!!, 0.000001)
    }

    @Test
    fun vwapUsesTypicalPriceWeightedByVolume() {
        val candles = listOf(
            candle(1, 9, 11, 8, 10, 2),
            candle(2, 19, 21, 18, 20, 1)
        )
        val expected = (((11.0 + 8.0 + 10.0) / 3.0) * 2.0 +
            ((21.0 + 18.0 + 20.0) / 3.0)) / 3.0
        assertEquals(expected, engine.vwap(candles)!!, 0.000001)
    }

    @Test
    fun relativeVolumeComparesLatestClosedCandleToPriorBaseline() {
        val candles = (1..21).map { index ->
            candle(index.toLong(), 99, 101, 98, 100, if (index == 21) 30 else 10)
        }
        assertEquals(3.0, engine.relativeVolume(candles, 20)!!, 0.000001)
    }

    @Test
    fun unclosedCandlesAreExcludedFromSnapshot() {
        val closed = (1..60).map { index ->
            candle(index.toLong(), 100, 102, 99, 101, 10)
        }
        val withOpenCandle = closed + candle(61, 100, 1000, 1, 900, 999, isClosed = false)
        val snapshot = engine.calculate(withOpenCandle)

        assertNotNull(snapshot.ema20)
        assertNotNull(snapshot.ema50)
        assertNotNull(snapshot.rsi7)
        assertNotNull(snapshot.atr14)
        assertNotNull(snapshot.adx14)
        assertNotNull(snapshot.vwap)
        assertEquals(10.0, snapshot.relativeVolume!!, 0.000001)
    }

    @Test
    fun mixedTimeframesAreRejected() {
        val first = candle(1, 100, 102, 99, 101, 10)
        val second = first.copy(
            timestampMillis = 2,
            timeframe = MarketTimeframe.FIVE_MINUTES
        )
        assertFailsWith<IllegalArgumentException> {
            engine.calculate(listOf(first, second))
        }
    }

    @Test
    fun duplicateClosedCandleTimestampsAreRejected() {
        val first = candle(1, 100, 102, 99, 101, 10)
        val second = first.copy(close = BigDecimal("100.5"))
        assertFailsWith<IllegalArgumentException> {
            engine.calculate(listOf(first, second))
        }
    }

    @Test
    fun adxIsBoundedWhenEnoughHistoryExists() {
        val candles = (1..40).map { index ->
            val base = 100.0 + index * 0.5
            candle(index.toLong(), base, base + 1.0, base - 0.5, base + 0.7, 10)
        }
        val value = engine.adx(candles, 14)
        assertNotNull(value)
        assertTrue(value in 0.0..100.0)
    }

    private fun candle(
        timestamp: Long,
        open: Number,
        high: Number,
        low: Number,
        close: Number,
        volume: Number,
        isClosed: Boolean = true
    ) = CryptoCandle(
        timestampMillis = timestamp,
        open = open.toBigDecimal(),
        high = high.toBigDecimal(),
        low = low.toBigDecimal(),
        close = close.toBigDecimal(),
        volume = volume.toBigDecimal(),
        turnover = close.toBigDecimal() * volume.toBigDecimal(),
        timeframe = MarketTimeframe.ONE_MINUTE,
        isClosed = isClosed
    )

    private fun Number.toBigDecimal(): BigDecimal =
        BigDecimal(this.toString())
}
