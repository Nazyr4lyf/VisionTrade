package com.visiontrade.domain.scalping

import java.math.BigDecimal
import java.math.RoundingMode

data class PaperPosition(
    val id: String,
    val symbol: String,
    val marketType: CryptoMarketType,
    val side: PositionSide,
    val quantity: BigDecimal,
    val entryPrice: BigDecimal,
    val stopLossPrice: BigDecimal,
    val takeProfitPrice: BigDecimal,
    val contractMultiplier: BigDecimal,
    val entryFeePaid: BigDecimal,
    val riskBudget: BigDecimal,
    val costs: TradingCostModel
) {
    init {
        require(id.isNotBlank() && symbol.isNotBlank()) { "Position id and symbol are required." }
        require(quantity > BigDecimal.ZERO) { "Position quantity must be positive." }
        require(entryPrice > BigDecimal.ZERO) { "Entry price must be positive." }
        require(contractMultiplier > BigDecimal.ZERO) { "Contract multiplier must be positive." }
        require(entryFeePaid >= BigDecimal.ZERO && riskBudget > BigDecimal.ZERO) {
            "Entry fee cannot be negative and risk budget must be positive."
        }
        if (marketType == CryptoMarketType.SPOT) {
            require(side == PositionSide.LONG) { "Spot paper positions cannot be naked short." }
        }
        require(
            if (side == PositionSide.LONG) stopLossPrice < entryPrice && takeProfitPrice > entryPrice
            else stopLossPrice > entryPrice && takeProfitPrice < entryPrice
        ) { "Stop-loss and take-profit must be on the correct side of entry." }
    }
}

enum class PaperExitReason {
    STOP_LOSS,
    TAKE_PROFIT
}

data class PaperExitFill(
    val positionId: String,
    val symbol: String,
    val marketType: CryptoMarketType,
    val side: PositionSide,
    val reason: PaperExitReason,
    val filledAtMillis: Long,
    val referenceTriggerPrice: BigDecimal,
    val simulatedFillPrice: BigDecimal,
    val grossPnl: BigDecimal,
    val entryFeePaid: BigDecimal,
    val exitFeePaid: BigDecimal,
    val fundingCost: BigDecimal,
    val netPnl: BigDecimal,
    val realizedR: BigDecimal
)

/**
 * Deterministic paper-only exit evaluator. It never sends exchange orders.
 * If one OHLC candle touches both stop and target, stop-loss wins because
 * intrabar ordering is unknown and optimistic fills would bias backtests.
 */
class PaperExecutionEngine {

    fun evaluateExit(position: PaperPosition, candle: CryptoCandle): PaperExitFill? {
        if (!candle.isClosed) return null
        if (candle.timestampMillis <= 0L) return null

        val stop = position.stopLossPrice
        val target = position.takeProfitPrice
        val stopTouched: Boolean
        val targetTouched: Boolean

        if (position.side == PositionSide.LONG) {
            stopTouched = candle.low <= stop
            targetTouched = candle.high >= target
        } else {
            stopTouched = candle.high >= stop
            targetTouched = candle.low <= target
        }

        if (!stopTouched && !targetTouched) return null

        val reason = if (stopTouched) PaperExitReason.STOP_LOSS else PaperExitReason.TAKE_PROFIT
        val trigger = if (reason == PaperExitReason.STOP_LOSS) stop else target
        // A stop is a trigger, not a guaranteed fill price. If the candle opens
        // beyond the stop, model the exit from that worse opening price.
        // For targets, keep the trigger price to avoid optimistic gap improvement.
        val executionReference = if (reason == PaperExitReason.STOP_LOSS) {
            when (position.side) {
                PositionSide.LONG -> if (candle.open < stop) candle.open else stop
                PositionSide.SHORT -> if (candle.open > stop) candle.open else stop
            }
        } else {
            target
        }
        val slip = position.costs.slippageRatePerSide
        val fill = when (position.side) {
            PositionSide.LONG -> executionReference.multiply(BigDecimal.ONE.subtract(slip))
            PositionSide.SHORT -> executionReference.multiply(BigDecimal.ONE.add(slip))
        }

        val multiplier = position.contractMultiplier
        val signedPriceMove = when (position.side) {
            PositionSide.LONG -> fill.subtract(position.entryPrice)
            PositionSide.SHORT -> position.entryPrice.subtract(fill)
        }
        val grossPnl = signedPriceMove.multiply(position.quantity).multiply(multiplier)
        val exitNotional = fill.multiply(position.quantity).multiply(multiplier)
        val exitFee = exitNotional.multiply(position.costs.exitFeeRate)
        val fundingCost = position.entryPrice.multiply(position.quantity)
            .multiply(multiplier).multiply(position.costs.fundingRateAllowance)
        val netPnl = grossPnl.subtract(position.entryFeePaid)
            .subtract(exitFee).subtract(fundingCost)
        val realizedR = netPnl.divide(position.riskBudget, 8, RoundingMode.HALF_UP)

        return PaperExitFill(
            positionId = position.id,
            symbol = position.symbol,
            marketType = position.marketType,
            side = position.side,
            reason = reason,
            filledAtMillis = candle.timestampMillis,
            referenceTriggerPrice = trigger,
            simulatedFillPrice = fill,
            grossPnl = grossPnl,
            entryFeePaid = position.entryFeePaid,
            exitFeePaid = exitFee,
            fundingCost = fundingCost,
            netPnl = netPnl,
            realizedR = realizedR
        )
    }
}
