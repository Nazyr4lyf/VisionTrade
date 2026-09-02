package com.visiontrade.domain

import com.visiontrade.domain.risk.AccountGrowthSimulator
import com.visiontrade.domain.risk.RiskCalculationInput
import com.visiontrade.domain.risk.RiskCalculator
import com.visiontrade.domain.risk.SimulationInput
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RiskTests {

    private val riskCalculator = RiskCalculator()
    private val simulator = AccountGrowthSimulator()

    @Test
    fun testBasicRiskCalculation() {
        val input = RiskCalculationInput(
            accountBalance = 10000.0,
            riskPercentage = 2.0,
            entryPrice = 1.1200,
            stopLossPrice = 1.1180,
            takeProfitPrice = 1.1240
        )

        val result = riskCalculator.calculateRisk(input)
        
        assertEquals(200.0, result.riskAmount)
        assertTrue(result.riskRewardRatio > 1.0)
    }

    @Test
    fun testRiskRewardRatioCalculation() {
        val input = RiskCalculationInput(
            accountBalance = 10000.0,
            riskPercentage = 2.0,
            entryPrice = 100.0,
            stopLossPrice = 99.0,
            takeProfitPrice = 102.0
        )

        val result = riskCalculator.calculateRisk(input)
        
        // Risk = 1 pip, Reward = 2 pips, Ratio = 2:1
        assertEquals(2.0, result.riskRewardRatio)
    }

    @Test
    fun testAccountGrowthSimulation() {
        val input = SimulationInput(
            startingBalance = 10000.0,
            riskPercentage = 2.0,
            winRate = 0.55, // 55% win rate
            rewardRatio = 1.5, // 1.5:1 reward ratio
            numberOfTrades = 100,
            compounding = true
        )

        val result = simulator.simulate(input)
        
        assertTrue(result.finalBalance > 0)
        assertEquals(10000.0, result.startingBalance)
        assertTrue(result.winCount > 0)
    }

    @Test
    fun testPhase9DefaultSimulatorExactResult() {
        val input = SimulationInput(
            startingBalance = 10000.0,
            riskPercentage = 2.0,
            winRate = 0.55,
            rewardRatio = 1.5,
            numberOfTrades = 100,
            compounding = true
        )

        val result = simulator.simulate(input)

        // Phase 9: exact default simulator verification
        assertEquals(10000.0, result.startingBalance, 0.01)
        assertEquals(20474.85, result.finalBalance, 0.01)
        assertEquals(10474.85, result.totalProfit, 0.01)
        assertEquals(104.75, result.returnPercentage, 0.01)
        assertEquals(55, result.winCount)
        assertEquals(45, result.lossCount)
        assertTrue(result.finalBalance > result.startingBalance)
    }

    @Test
    fun testSimulationWithoutCompounding() {
        val input = SimulationInput(
            startingBalance = 10000.0,
            riskPercentage = 2.0,
            winRate = 0.55,
            rewardRatio = 1.5,
            numberOfTrades = 100,
            compounding = false
        )

        val result = simulator.simulate(input)
        
        assertTrue(result.finalBalance > 0)
    }

    @Test
    fun testSimulationWithPoorWinRate() {
        val input = SimulationInput(
            startingBalance = 10000.0,
            riskPercentage = 2.0,
            winRate = 0.40, // 40% win rate
            rewardRatio = 1.5,
            numberOfTrades = 100,
            compounding = true
        )

        val result = simulator.simulate(input)
        
        // Should still produce a result (balance may be lower)
        assertTrue(result.finalBalance >= 0)
        assert(result.lossCount > result.winCount)
    }

    @Test
    fun testSmallAccountRisk() {
        val input = RiskCalculationInput(
            accountBalance = 500.0, // Small account
            riskPercentage = 5.0,
            entryPrice = 1.1200,
            stopLossPrice = 1.1180,
            takeProfitPrice = 1.1240
        )

        val result = riskCalculator.calculateRisk(input)
        
        assertEquals(25.0, result.riskAmount)
        assertTrue(result.positionSize > 0)
    }

    @Test
    fun testPositiveSizeAlwaysPositive() {
        val input = RiskCalculationInput(
            accountBalance = 10000.0,
            riskPercentage = 10.0,
            entryPrice = 1.5000,
            stopLossPrice = 1.4900,
            takeProfitPrice = 1.5200
        )

        val result = riskCalculator.calculateRisk(input)
        
        assertTrue(result.positionSize > 0)
        assertTrue(result.riskAmount > 0)
        assertTrue(result.potentialLoss > 0)
    }
}
