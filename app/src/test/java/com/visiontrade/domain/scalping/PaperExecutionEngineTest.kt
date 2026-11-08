package com.visiontrade.domain.scalping

import java.math.BigDecimal
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PaperExecutionEngineTest {

    private val engine = PaperExecutionEngine()
    private val costs = TradingCostModel(
        entryFeeRate = BigDecimal("0.001"),
        exitFeeRate = BigDecimal("0.001"),
        slippageRatePerSide = BigDecimal("0.001"),
        fundingRateAllowance = BigDecimal("0.0001")
    )

    @Test
    fun whenStopAndTargetAreBothTouchedStopIsAssumedFirst() {
        val position = longPosition()
        val candle = candle(low = "98", high = "104")

        val fill = engine.evaluateExit(position, candle)

        assertEquals(PaperExitReason.STOP_LOSS, fill!!.reason)
        assertEquals(BigDecimal("99"), fill.referenceTriggerPrice)
        assertTrue(fill.simulatedFillPrice < fill.referenceTriggerPrice)
        assertTrue(fill.netPnl < BigDecimal.ZERO)
    }

    @Test
    fun unclosedCandleCannotTriggerPaperExit() {
        assertNull(
            engine.evaluateExit(
                longPosition(),
                candle(low = "98", high = "104", isClosed = false)
            )
        )
    }

    @Test
    fun longTakeProfitProducesPositiveNetPnlAfterCosts() {
        val fill = engine.evaluateExit(
            longPosition(),
            candle(low = "100", high = "104")
        )

        assertEquals(PaperExitReason.TAKE_PROFIT, fill!!.reason)
        assertTrue(fill.netPnl > BigDecimal.ZERO)
        assertTrue(fill.realizedR > BigDecimal.ZERO)
        assertTrue(fill.exitFeePaid > BigDecimal.ZERO)
    }

    @Test
    fun shortTakeProfitProducesPositiveNetPnlAfterCosts() {
        val position = PaperPosition(
            id = "paper-short-1",
            symbol = "BTCUSDT",
            marketType = CryptoMarketType.USDT_PERPETUAL,
            side = PositionSide.SHORT,
            quantity = BigDecimal("0.1"),
            entryPrice = BigDecimal("100"),
            stopLossPrice = BigDecimal("101"),
            takeProfitPrice = BigDecimal("97"),
            contractMultiplier = BigDecimal.ONE,
            entryFeePaid = BigDecimal("0.01"),
            riskBudget = BigDecimal("1"),
            costs = costs
        )

        // The candle must be a valid OHLC bar: its high cannot be below its open.
        val fill = engine.evaluateExit(position, candle(low = "96", high = "100"))

        assertEquals(PaperExitReason.TAKE_PROFIT, fill!!.reason)
        assertTrue(fill.netPnl > BigDecimal.ZERO)
    }


    @Test
    fun longStopGapThroughUsesWorseCandleOpenBeforeSlippage() {
        val fill = engine.evaluateExit(
            longPosition(),
            candle(low = "96", high = "100", open = "97")
        )

        assertEquals(PaperExitReason.STOP_LOSS, fill!!.reason)
        assertEquals(BigDecimal("99"), fill.referenceTriggerPrice)
        assertEquals(BigDecimal("96.903"), fill.simulatedFillPrice)
    }

    @Test
    fun shortStopGapThroughUsesWorseCandleOpenBeforeSlippage() {
        val position = PaperPosition(
            id = "paper-short-gap",
            symbol = "BTCUSDT",
            marketType = CryptoMarketType.USDT_PERPETUAL,
            side = PositionSide.SHORT,
            quantity = BigDecimal("0.1"),
            entryPrice = BigDecimal("100"),
            stopLossPrice = BigDecimal("101"),
            takeProfitPrice = BigDecimal("97"),
            contractMultiplier = BigDecimal.ONE,
            entryFeePaid = BigDecimal("0.01"),
            riskBudget = BigDecimal("1"),
            costs = costs
        )

        val fill = engine.evaluateExit(
            position,
            candle(low = "100", high = "104", open = "103")
        )

        assertEquals(PaperExitReason.STOP_LOSS, fill!!.reason)
        assertEquals(BigDecimal("101"), fill.referenceTriggerPrice)
        assertEquals(BigDecimal("103.103"), fill.simulatedFillPrice)
    }

    private fun longPosition() = PaperPosition(
        id = "paper-long-1",
        symbol = "BTCUSDT",
        marketType = CryptoMarketType.SPOT,
        side = PositionSide.LONG,
        quantity = BigDecimal("0.1"),
        entryPrice = BigDecimal("100"),
        stopLossPrice = BigDecimal("99"),
        takeProfitPrice = BigDecimal("103"),
        contractMultiplier = BigDecimal.ONE,
        entryFeePaid = BigDecimal("0.01"),
        riskBudget = BigDecimal("1"),
        costs = costs
    )

    private fun candle(
        low: String,
        high: String,
        isClosed: Boolean = true,
        open: String = "100"
    ) = CryptoCandle(
        timestampMillis = 1_800_000_000_000L,
        open = BigDecimal(open),
        high = BigDecimal(high),
        low = BigDecimal(low),
        close = BigDecimal("100"),
        volume = BigDecimal("10"),
        turnover = BigDecimal("1000"),
        timeframe = MarketTimeframe.ONE_MINUTE,
        isClosed = isClosed
    )
}
