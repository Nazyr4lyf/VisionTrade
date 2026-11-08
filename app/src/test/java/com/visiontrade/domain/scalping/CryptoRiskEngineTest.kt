package com.visiontrade.domain.scalping

import java.math.BigDecimal
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CryptoRiskEngineTest {

    private val engine = CryptoRiskEngine()
    private val costs = TradingCostModel(
        entryFeeRate = BigDecimal("0.0005"),
        exitFeeRate = BigDecimal("0.0005"),
        slippageRatePerSide = BigDecimal("0.0002"),
        fundingRateAllowance = BigDecimal("0.0001")
    )

    @Test
    fun sizesSpotPositionWithinOnePercentRiskAndTwoRNetReward() {
        val result = engine.calculate(
            RiskCalculationRequest(
                equity = BigDecimal("1000"),
                entryPrice = BigDecimal("100"),
                stopLossPrice = BigDecimal("99"),
                takeProfitPrice = BigDecimal("103"),
                side = PositionSide.LONG,
                instrument = spotInstrument(),
                costs = costs
            )
        )

        assertTrue(result.accepted, result.rejectionReason ?: "Expected accepted risk calculation")
        assertEquals(BigDecimal("10.00"), result.riskBudget)
        assertTrue(result.estimatedLossAtStop <= result.riskBudget)
        assertTrue(result.netRiskReward!! >= BigDecimal("2.0"))
        assertEquals(
            0,
            result.quantity.remainder(BigDecimal("0.01")).compareTo(BigDecimal.ZERO)
        )
    }

    @Test
    fun rejectsSetupWhenNetRewardRiskFallsBelowTwoAfterCosts() {
        val result = engine.calculate(
            RiskCalculationRequest(
                equity = BigDecimal("1000"),
                entryPrice = BigDecimal("100"),
                stopLossPrice = BigDecimal("99"),
                takeProfitPrice = BigDecimal("102"),
                side = PositionSide.LONG,
                instrument = spotInstrument(),
                costs = costs
            )
        )

        assertFalse(result.accepted)
        assertNotNull(result.netRiskReward)
        assertTrue(result.netRiskReward!! < BigDecimal("2.0"))
    }

    @Test
    fun spotCannotOpenNakedShort() {
        val result = engine.calculate(
            RiskCalculationRequest(
                equity = BigDecimal("1000"),
                entryPrice = BigDecimal("100"),
                stopLossPrice = BigDecimal("101"),
                takeProfitPrice = BigDecimal("97"),
                side = PositionSide.SHORT,
                instrument = spotInstrument(),
                costs = costs
            )
        )
        assertFalse(result.accepted)
        assertTrue(result.rejectionReason!!.contains("Spot"))
    }

    @Test
    fun rejectsInvalidLongStopAndTargetOrientation() {
        val result = engine.calculate(
            RiskCalculationRequest(
                equity = BigDecimal("1000"),
                entryPrice = BigDecimal("100"),
                stopLossPrice = BigDecimal("101"),
                takeProfitPrice = BigDecimal("103"),
                side = PositionSide.LONG,
                instrument = spotInstrument(),
                costs = costs
            )
        )
        assertFalse(result.accepted)
    }

    @Test
    fun rejectsPricesThatDoNotAlignWithExchangeTickSize() {
        val result = engine.calculate(
            RiskCalculationRequest(
                equity = BigDecimal("1000"),
                entryPrice = BigDecimal("100.005"),
                stopLossPrice = BigDecimal("99.00"),
                takeProfitPrice = BigDecimal("103.00"),
                side = PositionSide.LONG,
                instrument = spotInstrument(),
                costs = costs
            )
        )
        assertFalse(result.accepted)
        assertTrue(result.rejectionReason!!.contains("tick size"))
    }

    private fun spotInstrument() = ExchangeInstrument(
        symbol = "BTCUSDT",
        marketType = CryptoMarketType.SPOT,
        baseCoin = "BTC",
        quoteCoin = "USDT",
        priceTick = BigDecimal("0.01"),
        quantityStep = BigDecimal("0.01"),
        minimumQuantity = BigDecimal("0.01"),
        minimumNotional = BigDecimal("5")
    )
}
