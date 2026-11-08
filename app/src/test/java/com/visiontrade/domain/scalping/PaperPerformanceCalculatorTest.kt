package com.visiontrade.domain.scalping

import java.math.BigDecimal
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PaperPerformanceCalculatorTest {

    private val calculator = PaperPerformanceCalculator()

    @Test
    fun calculatesWinRateProfitFactorAndDrawdownFromChronologicalEquityCurve() {
        val result = calculator.calculate(
            initialEquity = BigDecimal("1000"),
            closedTrades = listOf(
                trade("100", "1.0", 3),
                trade("-50", "-0.5", 1),
                trade("25", "0.25", 2),
                trade("-25", "-0.25", 4)
            )
        )

        assertEquals(4, result.tradeCount)
        assertEquals(2, result.wins)
        assertEquals(2, result.losses)
        assertEquals(0, result.breakeven)
        assertEquals(BigDecimal("50.0000"), result.winRate)
        assertEquals(BigDecimal("50"), result.netPnl)
        assertEquals(BigDecimal("1.66666667"), result.profitFactor)
        assertEquals(BigDecimal("0.05"), result.maximumDrawdown.stripTrailingZeros())
    }

    @Test
    fun emptyHistoryReturnsZeroMetricsWithoutInventingProfitFactor() {
        val result = calculator.calculate(BigDecimal("500"), emptyList())

        assertEquals(0, result.tradeCount)
        assertEquals(BigDecimal.ZERO, result.netPnl)
        assertEquals(BigDecimal.ZERO, result.maximumDrawdown)
        assertNull(result.profitFactor)
    }

    private fun trade(pnl: String, r: String, timestamp: Long) = PaperExitFill(
        positionId = "paper-$timestamp",
        symbol = "BTCUSDT",
        reason = if (pnl.startsWith("-")) PaperExitReason.STOP_LOSS else PaperExitReason.TAKE_PROFIT,
        filledAtMillis = timestamp,
        referenceTriggerPrice = BigDecimal("100"),
        simulatedFillPrice = BigDecimal("100"),
        grossPnl = BigDecimal(pnl),
        entryFeePaid = BigDecimal.ZERO,
        exitFeePaid = BigDecimal.ZERO,
        fundingCost = BigDecimal.ZERO,
        netPnl = BigDecimal(pnl),
        realizedR = BigDecimal(r)
    )
}
