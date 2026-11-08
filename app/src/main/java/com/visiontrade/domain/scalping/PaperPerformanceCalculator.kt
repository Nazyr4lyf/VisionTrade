package com.visiontrade.domain.scalping

import java.math.BigDecimal
import java.math.RoundingMode

data class PaperPerformanceMetrics(
    val tradeCount: Int,
    val wins: Int,
    val losses: Int,
    val breakeven: Int,
    val winRate: BigDecimal,
    val netPnl: BigDecimal,
    val averageRealizedR: BigDecimal,
    val expectancyR: BigDecimal,
    val profitFactor: BigDecimal?,
    val maximumDrawdown: BigDecimal
)

class PaperPerformanceCalculator {

    fun calculate(
        initialEquity: BigDecimal,
        closedTrades: List<PaperExitFill>
    ): PaperPerformanceMetrics {
        require(initialEquity > BigDecimal.ZERO) { "Initial equity must be positive." }

        if (closedTrades.isEmpty()) {
            return PaperPerformanceMetrics(
                tradeCount = 0,
                wins = 0,
                losses = 0,
                breakeven = 0,
                winRate = BigDecimal.ZERO,
                netPnl = BigDecimal.ZERO,
                averageRealizedR = BigDecimal.ZERO,
                expectancyR = BigDecimal.ZERO,
                profitFactor = null,
                maximumDrawdown = BigDecimal.ZERO
            )
        }

        val ordered = closedTrades.sortedBy { it.filledAtMillis }
        val wins = ordered.count { it.netPnl > BigDecimal.ZERO }
        val losses = ordered.count { it.netPnl < BigDecimal.ZERO }
        val breakeven = ordered.size - wins - losses
        val totalNet = ordered.fold(BigDecimal.ZERO) { acc, trade -> acc.add(trade.netPnl) }
        val totalR = ordered.fold(BigDecimal.ZERO) { acc, trade -> acc.add(trade.realizedR) }
        val averageR = totalR.divide(BigDecimal(ordered.size), 8, RoundingMode.HALF_UP)
        val winRate = BigDecimal(wins).multiply(BigDecimal("100"))
            .divide(BigDecimal(ordered.size), 4, RoundingMode.HALF_UP)
        val grossProfit = ordered.filter { it.netPnl > BigDecimal.ZERO }
            .fold(BigDecimal.ZERO) { acc, trade -> acc.add(trade.netPnl) }
        val grossLoss = ordered.filter { it.netPnl < BigDecimal.ZERO }
            .fold(BigDecimal.ZERO) { acc, trade -> acc.add(trade.netPnl.abs()) }
        val profitFactor = if (grossLoss == BigDecimal.ZERO) null
            else grossProfit.divide(grossLoss, 8, RoundingMode.HALF_UP)

        var equity = initialEquity
        var peak = initialEquity
        var maxDrawdown = BigDecimal.ZERO
        for (trade in ordered) {
            equity = equity.add(trade.netPnl)
            if (equity > peak) peak = equity
            if (peak > BigDecimal.ZERO) {
                val drawdown = peak.subtract(equity)
                    .divide(peak, 8, RoundingMode.HALF_UP)
                if (drawdown > maxDrawdown) maxDrawdown = drawdown
            }
        }

        return PaperPerformanceMetrics(
            tradeCount = ordered.size,
            wins = wins,
            losses = losses,
            breakeven = breakeven,
            winRate = winRate,
            netPnl = totalNet,
            averageRealizedR = averageR,
            expectancyR = averageR,
            profitFactor = profitFactor,
            maximumDrawdown = maxDrawdown
        )
    }
}
