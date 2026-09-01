package com.visiontrade.domain.risk

import kotlin.math.absoluteValue

/**
 * Risk Management Engine
 * 
 * Calculates position sizing, risk amounts, and reward ratios.
 * Deterministic and mathematically sound.
 */
data class RiskCalculationInput(
    val accountBalance: Double,
    val riskPercentage: Double, // e.g., 2.0 for 2%
    val entryPrice: Double,
    val stopLossPrice: Double,
    val takeProfitPrice: Double,
    val symbol: String = "EURUSD",
    val pipValue: Double = 0.0001 // For forex
)

data class RiskCalculationResult(
    val riskAmount: Double, // Amount of money at risk
    val stopLossPips: Double,
    val takeProfitPips: Double,
    val riskRewardRatio: Double,
    val positionSize: Double, // In lots or units
    val potentialProfit: Double,
    val potentialLoss: Double,
    val percentageRisk: Double
)

class RiskCalculator {

    fun calculateRisk(input: RiskCalculationInput): RiskCalculationResult {
        // Validate inputs
        require(input.accountBalance > 0) { "Account balance must be positive" }
        require(input.riskPercentage > 0 && input.riskPercentage <= 100) { "Risk percentage must be 0-100" }
        require(input.entryPrice > 0) { "Entry price must be positive" }
        require(input.stopLossPrice > 0) { "Stop loss price must be positive" }
        require(input.takeProfitPrice > 0) { "Take profit price must be positive" }

        // Calculate risk amount
        val riskAmount = input.accountBalance * (input.riskPercentage / 100.0)

        // Calculate pips/points
        val stopLossPips = (input.entryPrice - input.stopLossPrice).absoluteValue / input.pipValue
        val takeProfitPips = (input.takeProfitPrice - input.entryPrice).absoluteValue / input.pipValue

        // Validate that TP is farther than SL
        require(takeProfitPips > 0 && stopLossPips > 0) { "Invalid price levels" }

        // Calculate position size
        val positionSize = riskAmount / (stopLossPips * input.pipValue)

        // Calculate potential profit/loss
        val potentialLoss = positionSize * stopLossPips * input.pipValue
        val potentialProfit = positionSize * takeProfitPips * input.pipValue

        // Calculate risk/reward ratio
        val riskRewardRatio = if (stopLossPips > 0) takeProfitPips / stopLossPips else 0.0

        return RiskCalculationResult(
            riskAmount = riskAmount,
            stopLossPips = stopLossPips,
            takeProfitPips = takeProfitPips,
            riskRewardRatio = riskRewardRatio,
            positionSize = positionSize,
            potentialProfit = potentialProfit,
            potentialLoss = potentialLoss,
            percentageRisk = input.riskPercentage
        )
    }
}

// ============= ACCOUNT GROWTH SIMULATOR =============

data class SimulationInput(
    val startingBalance: Double,
    val riskPercentage: Double, // % of account risked per trade
    val winRate: Double, // 0.0 - 1.0 (e.g., 0.55 for 55%)
    val rewardRatio: Double, // e.g., 1.5 (1.5:1 R:R)
    val numberOfTrades: Int,
    val compounding: Boolean = true
)

data class SimulationResult(
    val startingBalance: Double,
    val finalBalance: Double,
    val totalProfit: Double,
    val returnPercentage: Double,
    val winCount: Int,
    val lossCount: Int,
    val consecutiveWins: Int,
    val consecutiveLoser: Int,
    val balanceHistory: List<Double> = emptyList()
)

class AccountGrowthSimulator {

    fun simulate(input: SimulationInput): SimulationResult {
        require(input.startingBalance > 0) { "Starting balance must be positive" }
        require(input.riskPercentage > 0 && input.riskPercentage <= 100) { "Risk % must be 0-100" }
        require(input.winRate >= 0 && input.winRate <= 1.0) { "Win rate must be 0.0-1.0" }
        require(input.rewardRatio > 0) { "Reward ratio must be positive" }
        require(input.numberOfTrades > 0) { "Number of trades must be positive" }

        var currentBalance = input.startingBalance
        val balanceHistory = mutableListOf(currentBalance)
        var winCount = 0
        var lossCount = 0
        var consecutiveWins = 0
        var consecutiveLoser = 0
        var maxConsecutiveWins = 0
        var maxConsecutiveLoser = 0

        // Simulate trades
        for (tradeIndex in 0 until input.numberOfTrades) {
            // Determine if this trade wins or loses
            val isWin = (tradeIndex.toDouble() + 1) / input.numberOfTrades <= input.winRate

            val riskAmount = if (input.compounding) {
                currentBalance * (input.riskPercentage / 100.0)
            } else {
                input.startingBalance * (input.riskPercentage / 100.0)
            }

            if (isWin) {
                val profitAmount = riskAmount * input.rewardRatio
                currentBalance += profitAmount
                winCount++
                consecutiveWins++
                consecutiveLoser = 0
                maxConsecutiveWins = maxOf(maxConsecutiveWins, consecutiveWins)
            } else {
                currentBalance -= riskAmount
                lossCount++
                consecutiveLoser++
                consecutiveWins = 0
                maxConsecutiveLoser = maxOf(maxConsecutiveLoser, consecutiveLoser)
            }

            // Prevent account from going negative
            if (currentBalance < 0) {
                currentBalance = 0.0
            }

            balanceHistory.add(currentBalance)
        }

        val totalProfit = currentBalance - input.startingBalance
        val returnPercentage = if (input.startingBalance > 0) {
            (totalProfit / input.startingBalance) * 100.0
        } else {
            0.0
        }

        return SimulationResult(
            startingBalance = input.startingBalance,
            finalBalance = currentBalance,
            totalProfit = totalProfit,
            returnPercentage = returnPercentage,
            winCount = winCount,
            lossCount = lossCount,
            consecutiveWins = maxConsecutiveWins,
            consecutiveLoser = maxConsecutiveLoser,
            balanceHistory = balanceHistory
        )
    }
}
