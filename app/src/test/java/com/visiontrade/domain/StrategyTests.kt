package com.visiontrade.domain

import com.visiontrade.domain.models.*
import com.visiontrade.domain.strategy.StrategyEngine
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StrategyTests {

    private val strategyEngine = StrategyEngine()

    private fun bullishAnalysis(): ChartAnalysis {
        val candles = listOf(
            Candle(
                open = 106.0,
                high = 107.0,
                low = 99.0,
                close = 100.0
            ),
            Candle(
                open = 99.0,
                high = 116.0,
                low = 98.0,
                close = 114.0
            )
        )

        val data = TimeframeData(
            timeframe = Timeframe.M15,
            candles = candles,
            ema20 = EMAValue(20, 115.0, DataStatus.AVAILABLE),
            ema50 = EMAValue(50, 110.0, DataStatus.AVAILABLE),
            rsi14 = RSIValue(14, 45.0, DataStatus.AVAILABLE),
            adx14 = ADXValue(14, 30.0, DataStatus.AVAILABLE),
            latestPrice = 100.0,
            structure = MarketStructure(
                support = PriceZone(99.5, 100.5),
                resistance = PriceZone(120.0, 125.0),
                validStructure = true
            )
        )

        return ChartAnalysis(
            symbol = "EURUSD",
            currentPrice = 100.0,
            m15Data = data,
            h1Data = data.copy(timeframe = Timeframe.H1),
            h4Data = data.copy(timeframe = Timeframe.H4),
            bullishEngulfing = EngulfingPattern(
                type = EngulfingType.BULLISH,
                previousCandle = candles[0],
                currentCandle = candles[1],
                valid = true
            ),
            imageQuality = ImageQuality.GOOD,
            dataCompleteness = 1.0
        )
    }

    private fun bearishAnalysis(): ChartAnalysis {
        val candles = listOf(
            Candle(
                open = 144.0,
                high = 151.0,
                low = 143.0,
                close = 150.0
            ),
            Candle(
                open = 151.0,
                high = 152.0,
                low = 134.0,
                close = 136.0
            )
        )

        val data = TimeframeData(
            timeframe = Timeframe.M15,
            candles = candles,
            ema20 = EMAValue(20, 115.0, DataStatus.AVAILABLE),
            ema50 = EMAValue(50, 120.0, DataStatus.AVAILABLE),
            rsi14 = RSIValue(14, 55.0, DataStatus.AVAILABLE),
            adx14 = ADXValue(14, 30.0, DataStatus.AVAILABLE),
            latestPrice = 124.5,
            structure = MarketStructure(
                support = PriceZone(100.0, 105.0),
                resistance = PriceZone(124.0, 125.0),
                validStructure = true
            )
        )

        return ChartAnalysis(
            symbol = "EURUSD",
            currentPrice = 124.5,
            m15Data = data,
            h1Data = data.copy(timeframe = Timeframe.H1),
            h4Data = data.copy(timeframe = Timeframe.H4),
            bearishEngulfing = EngulfingPattern(
                type = EngulfingType.BEARISH,
                previousCandle = candles[0],
                currentCandle = candles[1],
                valid = true
            ),
            imageQuality = ImageQuality.GOOD,
            dataCompleteness = 1.0
        )
    }

    @Test
    fun testWaitSignalForPoorImageQuality() {
        val analysis = ChartAnalysis(
            imageQuality = ImageQuality.POOR
        )

        val signal = strategyEngine.analyze(analysis)

        assertEquals(Signal.WAIT, signal.signal)
    }

    @Test
    fun testWaitSignalForUnknownImageQuality() {
        val analysis = ChartAnalysis(
            imageQuality = ImageQuality.UNKNOWN,
            dataCompleteness = 1.0
        )

        val signal = strategyEngine.analyze(analysis)

        assertEquals(Signal.WAIT, signal.signal)
    }

    @Test
    fun testWaitSignalForInsufficientData() {
        val analysis = ChartAnalysis(
            imageQuality = ImageQuality.GOOD,
            dataCompleteness = 0.3
        )

        val signal = strategyEngine.analyze(analysis)

        assertEquals(Signal.WAIT, signal.signal)
    }

    @Test
    fun testBuySignalAllConditionsMet() {
        val signal = strategyEngine.analyze(bullishAnalysis())

        assertEquals(Signal.BUY, signal.signal)
        assertEquals(8, signal.conditionsMet)
        assertEquals(8, signal.totalConditions)
        assertEquals(Confidence.HIGH, signal.confidence)
    }

    @Test
    fun testPartialBuyConditions() {
        val analysis = bullishAnalysis().copy(
            m15Data = bullishAnalysis().m15Data.copy(
                adx14 = ADXValue(14, 15.0, DataStatus.AVAILABLE)
            )
        )

        val signal = strategyEngine.analyze(analysis)

        assertEquals(Signal.WAIT, signal.signal)
        assertTrue(signal.conditionsMet < 8)
    }

    @Test
    fun testSellSignalAllConditionsMet() {
        val signal = strategyEngine.analyze(bearishAnalysis())

        assertEquals(Signal.SELL, signal.signal)
        assertEquals(8, signal.conditionsMet)
        assertEquals(8, signal.totalConditions)
        assertEquals(Confidence.HIGH, signal.confidence)
    }

    @Test
    fun testPartialSellConditions() {
        val analysis = bearishAnalysis().copy(
            m15Data = bearishAnalysis().m15Data.copy(
                adx14 = ADXValue(14, 15.0, DataStatus.AVAILABLE)
            )
        )

        val signal = strategyEngine.analyze(analysis)

        assertEquals(Signal.WAIT, signal.signal)
        assertTrue(signal.conditionsMet < 8)
    }

    @Test
    fun testBuyFailsWithoutBullishEngulfing() {
        val analysis = bullishAnalysis().copy(
            bullishEngulfing = null
        )

        val signal = strategyEngine.analyze(analysis)

        assertEquals(Signal.WAIT, signal.signal)
        assertTrue(signal.conditionsMet < 8)
    }

    @Test
    fun testSellFailsWithoutBearishEngulfing() {
        val analysis = bearishAnalysis().copy(
            bearishEngulfing = null
        )

        val signal = strategyEngine.analyze(analysis)

        assertEquals(Signal.WAIT, signal.signal)
        assertTrue(signal.conditionsMet < 8)
    }

    @Test
    fun testBuyFailsWhenRsiTooHigh() {
        val analysis = bullishAnalysis().copy(
            m15Data = bullishAnalysis().m15Data.copy(
                rsi14 = RSIValue(14, 75.0, DataStatus.AVAILABLE)
            )
        )

        val signal = strategyEngine.analyze(analysis)

        assertEquals(Signal.WAIT, signal.signal)
        assertTrue(signal.conditionsMet < 8)
    }

    @Test
    fun testSellFailsWhenRsiTooLow() {
        val analysis = bearishAnalysis().copy(
            m15Data = bearishAnalysis().m15Data.copy(
                rsi14 = RSIValue(14, 25.0, DataStatus.AVAILABLE)
            )
        )

        val signal = strategyEngine.analyze(analysis)

        assertEquals(Signal.WAIT, signal.signal)
        assertTrue(signal.conditionsMet < 8)
    }

    @Test
    fun testBuyFailsWhenTrendIsNotAligned() {
        val base = bullishAnalysis()
        val analysis = base.copy(
            h4Data = base.h4Data.copy(
                ema20 = EMAValue(20, 105.0, DataStatus.AVAILABLE),
                ema50 = EMAValue(50, 110.0, DataStatus.AVAILABLE)
            )
        )

        val signal = strategyEngine.analyze(analysis)

        assertEquals(Signal.WAIT, signal.signal)
    }

    @Test
    fun testSellFailsWhenTrendIsNotAligned() {
        val base = bearishAnalysis()
        val analysis = base.copy(
            h4Data = base.h4Data.copy(
                ema20 = EMAValue(20, 125.0, DataStatus.AVAILABLE),
                ema50 = EMAValue(50, 120.0, DataStatus.AVAILABLE)
            )
        )

        val signal = strategyEngine.analyze(analysis)

        assertEquals(Signal.WAIT, signal.signal)
    }

    @Test
    fun testBuyFailsWhenPriceIsFarFromSupport() {
        val base = bullishAnalysis()
        val analysis = base.copy(
            currentPrice = 115.0,
            m15Data = base.m15Data.copy(
                latestPrice = 115.0
            )
        )

        val signal = strategyEngine.analyze(analysis)

        assertEquals(Signal.WAIT, signal.signal)
    }

    @Test
    fun testSellFailsWhenPriceIsFarFromResistance() {
        val base = bearishAnalysis()
        val analysis = base.copy(
            currentPrice = 110.0,
            m15Data = base.m15Data.copy(
                latestPrice = 110.0
            )
        )

        val signal = strategyEngine.analyze(analysis)

        assertEquals(Signal.WAIT, signal.signal)
    }

    @Test
    fun testBuyFailsWhenAdxTooLow() {
        val base = bullishAnalysis()
        val analysis = base.copy(
            m15Data = base.m15Data.copy(
                adx14 = ADXValue(14, 20.0, DataStatus.AVAILABLE)
            )
        )

        val signal = strategyEngine.analyze(analysis)

        assertEquals(Signal.WAIT, signal.signal)
    }

    @Test
    fun testSellFailsWhenAdxTooLow() {
        val base = bearishAnalysis()
        val analysis = base.copy(
            m15Data = base.m15Data.copy(
                adx14 = ADXValue(14, 20.0, DataStatus.AVAILABLE)
            )
        )

        val signal = strategyEngine.analyze(analysis)

        assertEquals(Signal.WAIT, signal.signal)
    }

    @Test
    fun testBuyRequiresAllEightConditions() {
        val base = bullishAnalysis()
        val analysis = base.copy(
            m15Data = base.m15Data.copy(
                rsi14 = RSIValue(14, 70.0, DataStatus.AVAILABLE)
            )
        )

        val signal = strategyEngine.analyze(analysis)

        assertEquals(Signal.WAIT, signal.signal)
        assertTrue(signal.conditionsMet < 8)
    }

    @Test
    fun testSellRequiresAllEightConditions() {
        val base = bearishAnalysis()
        val analysis = base.copy(
            m15Data = base.m15Data.copy(
                rsi14 = RSIValue(14, 30.0, DataStatus.AVAILABLE)
            )
        )

        val signal = strategyEngine.analyze(analysis)

        assertEquals(Signal.WAIT, signal.signal)
        assertTrue(signal.conditionsMet < 8)
    }

    @Test
    fun testBuyHasHighConfidenceWhenAllConditionsMet() {
        val signal = strategyEngine.analyze(bullishAnalysis())

        assertEquals(Signal.BUY, signal.signal)
        assertEquals(Confidence.HIGH, signal.confidence)
    }

    @Test
    fun testSellHasHighConfidenceWhenAllConditionsMet() {
        val signal = strategyEngine.analyze(bearishAnalysis())

        assertEquals(Signal.SELL, signal.signal)
        assertEquals(Confidence.HIGH, signal.confidence)
    }
}
