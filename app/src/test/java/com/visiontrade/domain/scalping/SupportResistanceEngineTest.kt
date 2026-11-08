package com.visiontrade.domain.scalping

import java.math.BigDecimal
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SupportResistanceEngineTest {

    private val engine = SupportResistanceEngine()

    @Test
    fun clustersNearbyConfirmedSwingHighsIntoResistanceZone() {
        val highs = listOf(100, 105, 110, 105, 100, 106, 111, 106, 100)
        val candles = highs.mapIndexed { index, high ->
            candle(index + 1L, high, high - 5)
        }

        val zones = engine.detectZones(
            candles = candles,
            pivotLeftBars = 1,
            pivotRightBars = 1,
            tolerance = BigDecimal("2"),
            minimumTouches = 2
        )

        val resistance = zones.single { it.type == PriceZoneType.RESISTANCE }
        assertEquals(BigDecimal("110"), resistance.zone.low)
        assertEquals(BigDecimal("111"), resistance.zone.high)
        assertEquals(2, resistance.zone.touches)
    }

    @Test
    fun pivotIsNotEmittedUntilRightSideConfirmationCandleCloses() {
        val candles = listOf(
            candle(1, 100, 95),
            candle(2, 110, 100)
        )

        val beforeConfirmation = engine.detectZones(
            candles, 1, 1, BigDecimal.ZERO, minimumTouches = 1
        )
        assertTrue(beforeConfirmation.isEmpty())

        val afterConfirmation = engine.detectZones(
            candles + candle(3, 105, 98),
            1, 1, BigDecimal.ZERO, minimumTouches = 1
        )
        assertTrue(afterConfirmation.any { it.type == PriceZoneType.RESISTANCE })
    }

    @Test
    fun unclosedCandlesCannotConfirmPivots() {
        val candles = listOf(
            candle(1, 100, 95),
            candle(2, 110, 100),
            candle(3, 105, 98, isClosed = false)
        )
        val zones = engine.detectZones(
            candles, 1, 1, BigDecimal.ZERO, minimumTouches = 1
        )
        assertTrue(zones.isEmpty())
    }

    @Test
    fun mixedTimeframesAreRejected() {
        val first = candle(1, 100, 95)
        val second = candle(2, 110, 100).copy(timeframe = MarketTimeframe.FIVE_MINUTES)
        assertFailsWith<IllegalArgumentException> {
            engine.detectZones(listOf(first, second), 1, 1, BigDecimal.ONE, 1)
        }
    }

    private fun candle(
        timestamp: Long,
        high: Int,
        low: Int,
        isClosed: Boolean = true
    ) = CryptoCandle(
        timestampMillis = timestamp,
        open = BigDecimal((high + low).toString()).divide(BigDecimal("2")),
        high = BigDecimal(high),
        low = BigDecimal(low),
        close = BigDecimal((high + low).toString()).divide(BigDecimal("2")),
        volume = BigDecimal("10"),
        turnover = BigDecimal("1000"),
        timeframe = MarketTimeframe.FIVE_MINUTES,
        isClosed = isClosed
    )
}
