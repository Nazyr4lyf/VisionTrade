package com.visiontrade.domain.scalping

import java.math.BigDecimal
import org.junit.Test
import kotlin.test.assertEquals

class ScalpingStrategyEngineTest {

    private val engine = ScalpingStrategyEngine()

    @Test
    fun staleMarketDataAlwaysReturnsNoTrade() {
        val result = engine.analyze(
            symbol = "BTCUSDT",
            marketType = CryptoMarketType.SPOT,
            fiveMinuteCandles = emptyList(),
            oneMinuteCandles = emptyList(),
            marketDataReceivedAtMillis = 1_000L,
            nowMillis = 10_000L
        )
        assertEquals(ScalpingSignalAction.NO_TRADE, result.action)
        assertEquals(MarketRegime.UNKNOWN, result.regime)
    }

    @Test
    fun insufficientHistoryReturnsNoTrade() {
        val now = 1_800_000_000_000L
        val five = (1..20).map { candle(it.toLong(), MarketTimeframe.FIVE_MINUTES) }
        val one = (1..20).map { candle(it.toLong(), MarketTimeframe.ONE_MINUTE) }

        val result = engine.analyze(
            symbol = "ETHUSDT",
            marketType = CryptoMarketType.USDT_PERPETUAL,
            fiveMinuteCandles = five,
            oneMinuteCandles = one,
            marketDataReceivedAtMillis = now,
            nowMillis = now
        )
        assertEquals(ScalpingSignalAction.NO_TRADE, result.action)
    }

    @Test
    fun mismatchedTimeframeReturnsNoTrade() {
        val now = 1_800_000_000_000L
        val five = (1..60).map { candle(it.toLong(), MarketTimeframe.ONE_MINUTE) }
        val one = (1..60).map { candle(it.toLong(), MarketTimeframe.ONE_MINUTE) }

        val result = engine.analyze(
            symbol = "BTCUSDT",
            marketType = CryptoMarketType.SPOT,
            fiveMinuteCandles = five,
            oneMinuteCandles = one,
            marketDataReceivedAtMillis = now,
            nowMillis = now
        )
        assertEquals(ScalpingSignalAction.NO_TRADE, result.action)
    }

    @Test
    fun futureDatedClosedCandlesReturnNoTrade() {
        val now = 1_800_000_000_000L
        val five = (1..100).map { index ->
            candle(now + index * 300_000L, MarketTimeframe.FIVE_MINUTES)
        }
        val one = (1..60).map { index ->
            candle(now + index * 60_000L, MarketTimeframe.ONE_MINUTE)
        }
        val result = engine.analyze(
            symbol = "BTCUSDT",
            marketType = CryptoMarketType.USDT_PERPETUAL,
            fiveMinuteCandles = five,
            oneMinuteCandles = one,
            marketDataReceivedAtMillis = now,
            nowMillis = now
        )
        assertEquals(ScalpingSignalAction.NO_TRADE, result.action)
        assertEquals("A candle timestamp is in the future; market data is invalid.", result.reasons.first())
    }

    private fun candle(timestamp: Long, timeframe: MarketTimeframe) = CryptoCandle(
        timestampMillis = timestamp,
        open = BigDecimal("100"),
        high = BigDecimal("101"),
        low = BigDecimal("99"),
        close = BigDecimal("100.5"),
        volume = BigDecimal("10"),
        turnover = BigDecimal("1000"),
        timeframe = timeframe,
        isClosed = true
    )
}
