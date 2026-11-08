package com.visiontrade.domain.scalping

import java.math.BigDecimal
import java.math.RoundingMode

data class TradingCostModel(
    val entryFeeRate: BigDecimal,
    val exitFeeRate: BigDecimal,
    val slippageRatePerSide: BigDecimal,
    val fundingRateAllowance: BigDecimal = BigDecimal.ZERO
) {
    init {
        require(entryFeeRate >= BigDecimal.ZERO) { "Entry fee cannot be negative." }
        require(exitFeeRate >= BigDecimal.ZERO) { "Exit fee cannot be negative." }
        require(slippageRatePerSide >= BigDecimal.ZERO) { "Slippage cannot be negative." }
        require(fundingRateAllowance >= BigDecimal.ZERO) { "Funding allowance cannot be negative." }
    }
}

data class RiskCalculationRequest(
    val equity: BigDecimal,
    val entryPrice: BigDecimal,
    val stopLossPrice: BigDecimal,
    val takeProfitPrice: BigDecimal,
    val side: PositionSide,
    val instrument: ExchangeInstrument,
    val costs: TradingCostModel
)

data class RiskCalculationResult(
    val accepted: Boolean,
    val rejectionReason: String?,
    val riskBudget: BigDecimal,
    val quantity: BigDecimal,
    val notional: BigDecimal,
    val estimatedLossAtStop: BigDecimal,
    val grossRiskReward: BigDecimal?,
    val netRiskReward: BigDecimal?
)

class CryptoRiskEngine(
    private val policy: ScalpingRiskPolicy = ScalpingRiskPolicy()
) {

    fun calculate(request: RiskCalculationRequest): RiskCalculationResult {
        val zero = BigDecimal.ZERO
        val budget = request.equity.multiply(policy.riskFraction)

        fun reject(reason: String, grossRr: BigDecimal? = null, netRr: BigDecimal? = null) =
            RiskCalculationResult(false, reason, budget, zero, zero, zero, grossRr, netRr)

        if (request.equity <= zero || request.entryPrice <= zero ||
            request.stopLossPrice <= zero || request.takeProfitPrice <= zero
        ) return reject("Equity, entry, stop-loss and take-profit must be positive.")

        if (request.instrument.marketType == CryptoMarketType.SPOT &&
            request.side == PositionSide.SHORT
        ) return reject("Spot risk calculation cannot open a naked short position.")

        val isLong = request.side == PositionSide.LONG
        val stopValid = if (isLong) request.stopLossPrice < request.entryPrice
            else request.stopLossPrice > request.entryPrice
        val targetValid = if (isLong) request.takeProfitPrice > request.entryPrice
            else request.takeProfitPrice < request.entryPrice
        if (!stopValid || !targetValid) {
            return reject("Stop-loss and take-profit must be on the correct side of entry.")
        }

        val multiplier = request.instrument.contractMultiplier
        val entry = request.entryPrice
        val stop = request.stopLossPrice
        val target = request.takeProfitPrice
        val costs = request.costs

        val entryCostsPerUnit = entry.multiply(
            costs.entryFeeRate.add(costs.slippageRatePerSide)
        )
        val stopExitCostsPerUnit = stop.multiply(
            costs.exitFeeRate.add(costs.slippageRatePerSide)
        ).add(entry.multiply(costs.fundingRateAllowance))
        val targetExitCostsPerUnit = target.multiply(
            costs.exitFeeRate.add(costs.slippageRatePerSide)
        ).add(entry.multiply(costs.fundingRateAllowance))

        val grossLossPerUnit = entry.subtract(stop).abs().multiply(multiplier)
        val grossRewardPerUnit = target.subtract(entry).abs().multiply(multiplier)
        val netLossPerUnit = grossLossPerUnit.add(
            entryCostsPerUnit.add(stopExitCostsPerUnit).multiply(multiplier)
        )
        val netRewardPerUnit = grossRewardPerUnit.subtract(
            entryCostsPerUnit.add(targetExitCostsPerUnit).multiply(multiplier)
        )

        if (netLossPerUnit <= zero) return reject("Calculated loss per unit is invalid.")
        if (netRewardPerUnit <= zero) return reject("Estimated costs consume the entire target reward.")

        val grossRr = grossRewardPerUnit.divide(grossLossPerUnit, 8, RoundingMode.HALF_UP)
        val netRr = netRewardPerUnit.divide(netLossPerUnit, 8, RoundingMode.HALF_UP)
        if (netRr < policy.minimumNetRiskReward) {
            return reject(
                "Net risk/reward is below the required 2.0R after fees and slippage.",
                grossRr,
                netRr
            )
        }

        val rawQuantity = budget.divide(netLossPerUnit, 16, RoundingMode.DOWN)
        val quantitySteps = rawQuantity.divide(
            request.instrument.quantityStep, 0, RoundingMode.DOWN
        )
        val quantity = quantitySteps.multiply(request.instrument.quantityStep)
        if (quantity < request.instrument.minimumQuantity || quantity <= zero) {
            return reject("Risk-sized quantity is below the exchange minimum quantity.", grossRr, netRr)
        }

        val notional = quantity.multiply(entry).multiply(multiplier)
        if (notional < request.instrument.minimumNotional) {
            return reject("Risk-sized position is below the exchange minimum notional.", grossRr, netRr)
        }

        val estimatedLoss = quantity.multiply(netLossPerUnit)
        if (estimatedLoss > budget) {
            return reject("Rounded position would exceed the configured 1% risk budget.", grossRr, netRr)
        }

        return RiskCalculationResult(
            accepted = true,
            rejectionReason = null,
            riskBudget = budget,
            quantity = quantity,
            notional = notional,
            estimatedLossAtStop = estimatedLoss,
            grossRiskReward = grossRr,
            netRiskReward = netRr
        )
    }
}
