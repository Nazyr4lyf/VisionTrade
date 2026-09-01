package com.visiontrade.domain.trade

import com.visiontrade.domain.models.TradeSetup
import com.visiontrade.domain.risk.RiskCalculationInput
import com.visiontrade.domain.risk.RiskCalculationResult
import com.visiontrade.domain.risk.RiskCalculator

data class TradeRiskInput(
    val accountBalance: Double,
    val riskPercentage: Double,
    val pipValue: Double = 0.0001,
    val symbol: String = "EURUSD"
)

data class TradeRiskResult(
    val setup: TradeSetup,
    val risk: RiskCalculationResult,
    val validation: TradeSetupValidationResult
)

class TradeRiskEngine(
    private val validator: TradeSetupValidator = TradeSetupValidator(),
    private val calculator: RiskCalculator = RiskCalculator()
) {

    fun calculate(
        setup: TradeSetup,
        input: TradeRiskInput
    ): TradeRiskResult {

        val validation = validator.validate(setup)

        require(validation.valid) {
            "Invalid trade setup: ${validation.reasons.joinToString("; ")}"
        }

        require(input.accountBalance > 0.0) {
            "Account balance must be positive."
        }

        require(input.riskPercentage > 0.0 && input.riskPercentage <= 100.0) {
            "Risk percentage must be between 0 and 100."
        }

        require(input.pipValue > 0.0 && input.pipValue.isFinite()) {
            "Pip value must be positive."
        }

        val entry = setup.entryZone.middle

        val risk = calculator.calculateRisk(
            RiskCalculationInput(
                accountBalance = input.accountBalance,
                riskPercentage = input.riskPercentage,
                entryPrice = entry,
                stopLossPrice = setup.stopLoss,
                takeProfitPrice = setup.takeProfit,
                symbol = input.symbol,
                pipValue = input.pipValue
            )
        )

        return TradeRiskResult(
            setup = setup.copy(riskCalculation = risk),
            risk = risk,
            validation = validation
        )
    }
}
