package com.visiontrade.domain.scalping

import com.google.gson.JsonParser
import java.math.BigDecimal
import kotlin.math.sin
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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

    @Test
    fun sharedAndroidBackendDecisionFixturesProduceExpectedActionAndRegime() {
        val stream = this::class.java.classLoader?.getResourceAsStream("scalping/decision-cases.json")
            ?: error("Shared strategy decision fixtures were not found on the test classpath.")
        val fixtures = stream.bufferedReader().use { JsonParser.parseReader(it).asJsonArray }
        val now = 1_800_000_000_000L

        fixtures.forEach { element ->
            val item = element.asJsonObject
            val id = item.get("id").asString
            val marketType = CryptoMarketType.valueOf(item.get("marketType").asString)
            val receivedOffset = item.get("receivedAtOffsetMillis").asLong
            val fiveCount = item.get("fiveCount").asInt
            val oneCount = item.get("oneCount").asInt
            val fiveTimeframe = item.get("fiveTimeframeMinutes").asInt
            val oneTimeframe = item.get("oneTimeframeMinutes").asInt
            val fivePattern = item.get("fivePattern").asString
            val future = item.get("futureCandles").asBoolean

            fun rows(count: Int, timeframeMinutes: Int, pattern: String): List<CryptoCandle> =
                (0 until count).map { index ->
                    val close = if (pattern == "TREND") {
                        100.0 + index * 0.8
                    } else {
                        100.0 + sin(index / if (timeframeMinutes == 5) 3.0 else 2.0) *
                            if (timeframeMinutes == 5) 2.0 else 0.8
                    }
                    val open = close - 0.15
                    val high = maxOf(open, close) + 0.5
                    val low = minOf(open, close) - 0.5
                    val timestamp = if (future) {
                        now + (index + 1) * timeframeMinutes * 60_000L
                    } else {
                        now - (count - index) * timeframeMinutes * 60_000L
                    }
                    val volume = BigDecimal((100 + index % 5).toString())
                    CryptoCandle(
                        timestampMillis = timestamp,
                        open = BigDecimal(open.toString()),
                        high = BigDecimal(high.toString()),
                        low = BigDecimal(low.toString()),
                        close = BigDecimal(close.toString()),
                        volume = volume,
                        turnover = BigDecimal(close.toString()) * volume,
                        timeframe = if (timeframeMinutes == 5) MarketTimeframe.FIVE_MINUTES else MarketTimeframe.ONE_MINUTE,
                        isClosed = true
                    )
                }

            val result = engine.analyze(
                symbol = "BTCUSDT",
                marketType = marketType,
                fiveMinuteCandles = rows(fiveCount, fiveTimeframe, fivePattern),
                oneMinuteCandles = rows(oneCount, oneTimeframe, "RANGE"),
                marketDataReceivedAtMillis = now + receivedOffset,
                nowMillis = now
            )

            assertEquals(item.get("expectedAction").asString, result.action.name, id)
            assertEquals(item.get("expectedRegime").asString, result.regime.name, id)
            assertTrue(
                result.reasons.joinToString(" ").contains(item.get("expectedReasonContains").asString, ignoreCase = true),
                id
            )
        }
    }
}
