package com.visiontrade.domain

import com.visiontrade.domain.models.*
import com.visiontrade.domain.strategy.StrategyEngine
import com.visiontrade.domain.trade.TradeRiskEngine
import com.visiontrade.domain.trade.TradeRiskInput
import com.visiontrade.domain.trade.TradeSetupEngine
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TradeRiskIntegrationTests {

    private val strategyEngine = StrategyEngine()
    private val setupEngine = TradeSetupEngine()
    private val riskEngine = TradeRiskEngine()

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
    fun testBuySignalProducesExecutableTradeSetup() {
        val signal = strategyEngine.analyze(bullishAnalysis())

        assertEquals(Signal.BUY, signal.signal)

        val setup = setupEngine.build(signal)

        assertNotNull(setup)
        assertEquals(TradeDirection.BUY, setup.direction)
        assertTrue(setup.setupValid)
        assertTrue(setup.stopLoss < setup.entryZone.middle)
        assertTrue(setup.takeProfit > setup.entryZone.middle)
        assertTrue(setup.riskRewardRatio > 0.0)
    }

    @Test
    fun testSellSignalProducesExecutableTradeSetup() {
        val signal = strategyEngine.analyze(bearishAnalysis())

        assertEquals(Signal.SELL, signal.signal)

        val setup = setupEngine.build(signal)

        assertNotNull(setup)
        assertEquals(TradeDirection.SELL, setup.direction)
        assertTrue(setup.setupValid)
        assertTrue(setup.stopLoss > setup.entryZone.middle)
        assertTrue(setup.takeProfit < setup.entryZone.middle)
        assertTrue(setup.riskRewardRatio > 0.0)
    }

    @Test
    fun testWaitSignalDoesNotProduceTradeSetup() {
        val signal = strategyEngine.analyze(
            bullishAnalysis().copy(
                imageQuality = ImageQuality.POOR
            )
        )

        assertEquals(Signal.WAIT, signal.signal)
        assertEquals(null, setupEngine.build(signal))
    }

    @Test
    fun testBuySetupIntegratesWithRiskEngine() {
        val signal = strategyEngine.analyze(bullishAnalysis())
        val setup = setupEngine.build(signal)

        assertNotNull(setup)

        val result = riskEngine.calculate(
            setup,
            TradeRiskInput(
                accountBalance = 10_000.0,
                riskPercentage = 2.0,
                pipValue = 0.0001,
                symbol = "EURUSD"
            )
        )

        assertTrue(result.validation.valid)
        assertEquals(200.0, result.risk.riskAmount)
        assertTrue(result.risk.positionSize > 0.0)
        assertTrue(result.risk.potentialLoss > 0.0)
        assertTrue(result.risk.potentialProfit > 0.0)
        assertNotNull(result.setup.riskCalculation)
    }

    @Test
    fun testSellSetupIntegratesWithRiskEngine() {
        val signal = strategyEngine.analyze(bearishAnalysis())
        val setup = setupEngine.build(signal)

        assertNotNull(setup)

        val result = riskEngine.calculate(
            setup,
            TradeRiskInput(
                accountBalance = 10_000.0,
                riskPercentage = 2.0,
                pipValue = 0.0001,
                symbol = "EURUSD"
            )
        )

        assertTrue(result.validation.valid)
        assertEquals(200.0, result.risk.riskAmount)
        assertTrue(result.risk.positionSize > 0.0)
        assertTrue(result.risk.potentialLoss > 0.0)
        assertTrue(result.risk.potentialProfit > 0.0)
        assertNotNull(result.setup.riskCalculation)
    }

    @Test
    fun testRiskRewardIsConsistentAcrossSetupAndRiskResult() {
        val signal = strategyEngine.analyze(bullishAnalysis())
        val setup = setupEngine.build(signal)

        assertNotNull(setup)

        val result = riskEngine.calculate(
            setup,
            TradeRiskInput(
                accountBalance = 10_000.0,
                riskPercentage = 2.0,
                pipValue = 0.0001
            )
        )

        assertEquals(
            setup.riskRewardRatio,
            result.risk.riskRewardRatio,
            0.000001
        )

        assertEquals(
            setup.riskRewardRatio,
            result.setup.riskCalculation!!.riskRewardRatio,
            0.000001
        )
    }

    @Test
    fun testRiskEngineRejectsInvalidAccountBalance() {
        val signal = strategyEngine.analyze(bullishAnalysis())
        val setup = setupEngine.build(signal)

        assertNotNull(setup)

        val thrown = runCatching {
            riskEngine.calculate(
                setup,
                TradeRiskInput(
                    accountBalance = 0.0,
                    riskPercentage = 2.0
                )
            )
        }.exceptionOrNull()

        assertNotNull(thrown)
    }

    @Test
    fun testRiskEngineRejectsInvalidRiskPercentage() {
        val signal = strategyEngine.analyze(bullishAnalysis())
        val setup = setupEngine.build(signal)

        assertNotNull(setup)

        val thrown = runCatching {
            riskEngine.calculate(
                setup,
                TradeRiskInput(
                    accountBalance = 10_000.0,
                    riskPercentage = 0.0
                )
            )
        }.exceptionOrNull()

        assertNotNull(thrown)
    }
}
