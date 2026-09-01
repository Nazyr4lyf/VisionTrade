package com.visiontrade.domain.trade

import com.visiontrade.domain.models.TradeDirection
import com.visiontrade.domain.models.TradeSetup
import kotlin.math.abs

data class TradeSetupValidationResult(
    val valid: Boolean,
    val reasons: List<String> = emptyList()
)

class TradeSetupValidator {

    companion object {
        private const val RR_TOLERANCE = 0.000001
    }

    fun validate(setup: TradeSetup): TradeSetupValidationResult {
        val reasons = mutableListOf<String>()

        if (!setup.stopLoss.isFinite() || setup.stopLoss <= 0.0) {
            reasons.add("Stop loss is invalid.")
        }

        if (!setup.takeProfit.isFinite() || setup.takeProfit <= 0.0) {
            reasons.add("Take profit is invalid.")
        }

        if (
            !setup.entryZone.low.isFinite() ||
            !setup.entryZone.high.isFinite() ||
            setup.entryZone.low <= 0.0 ||
            setup.entryZone.high < setup.entryZone.low
        ) {
            reasons.add("Entry zone is invalid.")
        }

        if (!setup.invalidationLevel.isFinite() || setup.invalidationLevel <= 0.0) {
            reasons.add("Invalidation level is invalid.")
        }

        if (!setup.riskRewardRatio.isFinite() || setup.riskRewardRatio <= 0.0) {
            reasons.add("Risk/reward ratio is invalid.")
        }

        val entry = setup.entryZone.middle

        when (setup.direction) {
            TradeDirection.BUY -> {
                if (setup.stopLoss >= entry) {
                    reasons.add("BUY stop loss must be below entry.")
                }

                if (setup.takeProfit <= entry) {
                    reasons.add("BUY take profit must be above entry.")
                }

                if (abs(setup.invalidationLevel - setup.stopLoss) > RR_TOLERANCE) {
                    reasons.add("BUY invalidation level must match stop loss.")
                }
            }

            TradeDirection.SELL -> {
                if (setup.stopLoss <= entry) {
                    reasons.add("SELL stop loss must be above entry.")
                }

                if (setup.takeProfit >= entry) {
                    reasons.add("SELL take profit must be below entry.")
                }

                if (abs(setup.invalidationLevel - setup.stopLoss) > RR_TOLERANCE) {
                    reasons.add("SELL invalidation level must match stop loss.")
                }
            }
        }

        val risk = abs(entry - setup.stopLoss)
        val reward = abs(setup.takeProfit - entry)

        if (risk <= 0.0) {
            reasons.add("Risk distance must be greater than zero.")
        }

        if (reward <= 0.0) {
            reasons.add("Reward distance must be greater than zero.")
        }

        if (risk > 0.0 && reward > 0.0) {
            val expectedRR = reward / risk

            if (abs(setup.riskRewardRatio - expectedRR) > RR_TOLERANCE) {
                reasons.add("Risk/reward ratio does not match entry, stop loss and take profit.")
            }
        }

        return TradeSetupValidationResult(
            valid = reasons.isEmpty() && setup.setupValid,
            reasons = reasons
        )
    }
}
