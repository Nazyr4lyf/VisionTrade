package com.visiontrade.domain

import com.visiontrade.domain.models.*
import com.visiontrade.domain.trade.TradeRiskEngine
import com.visiontrade.domain.trade.TradeRiskInput
import com.visiontrade.domain.trade.TradeSetupEngine
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class TradeRiskEngineTests {

    private val setupEngine = TradeSetupEngine()
    private val riskEngine = TradeRiskEngine()

    private fun buySetup(): TradeSetup {
        val signal = TradingSignal(
            signal = Signal.BUY,
            confidence = Confidence.HIGH,
            trend = Trend.BULLISH,
            conditionsMet = 8,
            totalConditions = 8,
            entryZone = PriceZone(100.0, 101.0),
            support = PriceZone(99.0, 100.0),
            resistance = PriceZone(110.0, 112.0)
        )

        return setupEngine.build(signal)!!
    }

    @Test
    fun testTradeRiskEngineCalculatesRiskForValidSetup() {
        val setup = buySetup()

        val result = riskEngine.calculate(
            setup = setup,
            input = TradeRiskInput(
                accountBalance = 10_000.0,
                riskPercentage = 2.0,
                pipValue = 0.0001,
                symbol = "EURUSD"
            )
        )

        assertNotNull(result.risk)
        assertEquals(200.0, result.risk.riskAmount)
        assertTrue(result.risk.positionSize > 0.0)
        assertTrue(result.risk.potentialLoss > 0.0)
        assertTrue(result.risk.potentialProfit > 0.0)
        assertTrue(result.validation.valid)
    }

    @Test
    fun testRiskCalculationIsAttachedToSetup() {
        val setup = buySetup()

        val result = riskEngine.calculate(
            setup = setup,
            input = TradeRiskInput(
                accountBalance = 5_000.0,
                riskPercentage = 2.0,
                pipValue = 0.0001,
                symbol = "EURUSD"
            )
        )

        assertNotNull(result.setup.riskCalculation)
        assertEquals(
            result.risk.riskAmount,
            result.setup.riskCalculation!!.riskAmount
        )
    }

    @Test
    fun testInvalidAccountBalanceIsRejected() {
        val setup = buySetup()

        assertFailsWith<IllegalArgumentException> {
            riskEngine.calculate(
                setup = setup,
                input = TradeRiskInput(
                    accountBalance = 0.0,
                    riskPercentage = 2.0
                )
            )
        }
    }

    @Test
    fun testInvalidRiskPercentageIsRejected() {
        val setup = buySetup()

        assertFailsWith<IllegalArgumentException> {
            riskEngine.calculate(
                setup = setup,
                input = TradeRiskInput(
                    accountBalance = 10_000.0,
                    riskPercentage = 101.0
                )
            )
        }
    }

    @Test
    fun testInvalidPipValueIsRejected() {
        val setup = buySetup()

        assertFailsWith<IllegalArgumentException> {
            riskEngine.calculate(
                setup = setup,
                input = TradeRiskInput(
                    accountBalance = 10_000.0,
                    riskPercentage = 2.0,
                    pipValue = 0.0
                )
            )
        }
    }

    @Test
    fun testRiskRewardMatchesTradeSetup() {
        val setup = buySetup()

        val result = riskEngine.calculate(
            setup = setup,
            input = TradeRiskInput(
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
    }
}
