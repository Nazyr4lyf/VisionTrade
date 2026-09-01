package com.visiontrade.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.visiontrade.domain.risk.AccountGrowthSimulator
import com.visiontrade.domain.risk.RiskCalculationInput
import com.visiontrade.domain.risk.RiskCalculationResult
import com.visiontrade.domain.risk.RiskCalculator
import com.visiontrade.domain.risk.SimulationInput
import com.visiontrade.domain.risk.SimulationResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class RiskViewModel : ViewModel() {

    private val riskCalculator = RiskCalculator()
    private val simulator = AccountGrowthSimulator()

    // ============= RISK CALCULATOR STATE =============
    private val _riskResult = MutableStateFlow<RiskCalculationResult?>(null)
    val riskResult: StateFlow<RiskCalculationResult?> = _riskResult.asStateFlow()

    private val _riskError = MutableStateFlow<String?>(null)
    val riskError: StateFlow<String?> = _riskError.asStateFlow()

    // ============= SIMULATOR STATE =============
    private val _simulationResult = MutableStateFlow<SimulationResult?>(null)
    val simulationResult: StateFlow<SimulationResult?> = _simulationResult.asStateFlow()

    private val _simulationError = MutableStateFlow<String?>(null)
    val simulationError: StateFlow<String?> = _simulationError.asStateFlow()

    // ============= RISK CALCULATION =============
    fun calculateRisk(input: RiskCalculationInput) {
        try {
            _riskError.value = null
            val result = riskCalculator.calculateRisk(input)
            _riskResult.value = result
        } catch (e: Exception) {
            _riskError.value = e.message ?: "Calculation error"
            _riskResult.value = null
        }
    }

    fun clearRiskResult() {
        _riskResult.value = null
        _riskError.value = null
    }

    // ============= SIMULATION =============
    fun runSimulation(input: SimulationInput) {
        try {
            _simulationError.value = null
            val result = simulator.simulate(input)
            _simulationResult.value = result
        } catch (e: Exception) {
            _simulationError.value = e.message ?: "Simulation error"
            _simulationResult.value = null
        }
    }

    fun clearSimulationResult() {
        _simulationResult.value = null
        _simulationError.value = null
    }
}
