package com.visiontrade.domain.models

import com.visiontrade.domain.risk.RiskCalculationResult
/**
 * Deterministic trade execution setup.
 *
 * Contains only levels that have been explicitly derived from
 * the confirmed TradingSignal. No guessed prices.
 */
data class TradeSetup(
    val direction: TradeDirection,
    val entryZone: PriceZone,
    val stopLoss: Double,
    val takeProfit: Double,
    val riskRewardRatio: Double,
    val invalidationLevel: Double,
    val setupValid: Boolean,
    val reason: String,
    val riskCalculation: RiskCalculationResult? = null
)

enum class TradeDirection {
    BUY,
    SELL
}
