package com.visiontrade.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.visiontrade.data.repository.AnalysisRepository
import com.visiontrade.domain.indicators.IndicatorEngine
import com.visiontrade.domain.models.*
import com.visiontrade.domain.strategy.StrategyEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDateTime

class AnalysisViewModel(
    private val repository: AnalysisRepository
) : ViewModel() {

    // ============= STATE FLOWS =============
    private val _analysisState = MutableStateFlow<ChartAnalysis?>(null)
    val analysisState: StateFlow<ChartAnalysis?> = _analysisState.asStateFlow()

    private val _signalState = MutableStateFlow<TradingSignal?>(null)
    val signalState: StateFlow<TradingSignal?> = _signalState.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _historyState = MutableStateFlow<List<AnalysisHistoryEntry>>(emptyList())
    val historyState: StateFlow<List<AnalysisHistoryEntry>> = _historyState.asStateFlow()

    private val indicatorEngine = IndicatorEngine()
    private val strategyEngine = StrategyEngine()

    // ============= ANALYSIS WORKFLOW =============
    fun analyzeChart(analysis: ChartAnalysis) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null

            try {
                // Perform strategy analysis
                val signal = strategyEngine.analyze(analysis)
                
                // Update states
                _analysisState.value = analysis
                _signalState.value = signal

                // Save to history
                saveToHistory(analysis, signal)

                _isLoading.value = false
            } catch (e: Exception) {
                _errorMessage.value = "Analysis error: ${e.message}"
                _isLoading.value = false
            }
        }
    }

    // ============= INDICATOR CALCULATIONS =============
    fun calculateIndicators(candles: List<Candle>, timeframe: Timeframe): TimeframeData {
        if (candles.isEmpty()) {
            return TimeframeData(timeframe)
        }

        val ema20 = indicatorEngine.calculateEMA(candles, 20)
        val ema50 = indicatorEngine.calculateEMA(candles, 50)
        val rsi14 = indicatorEngine.calculateRSI(candles, 14)
        val adx14 = indicatorEngine.calculateADX(candles, 14)
        val structure = indicatorEngine.calculateSupportResistance(candles, 50)

        return TimeframeData(
            timeframe = timeframe,
            candles = candles,
            ema20 = ema20,
            ema50 = ema50,
            rsi14 = rsi14,
            adx14 = adx14,
            latestPrice = candles.lastOrNull()?.close,
            structure = structure
        )
    }

    // ============= PATTERN DETECTION =============
    fun detectEngulfingPatterns(candles: List<Candle>): Pair<EngulfingPattern?, EngulfingPattern?> {
        if (candles.size < 2) {
            return Pair(null, null)
        }

        val prevCandle = candles[candles.size - 2]
        val currCandle = candles[candles.size - 1]

        val bullishEngulfing = if (indicatorEngine.detectBullishEngulfing(prevCandle, currCandle)) {
            EngulfingPattern(
                type = EngulfingType.BULLISH,
                previousCandle = prevCandle,
                currentCandle = currCandle,
                valid = true
            )
        } else null

        val bearishEngulfing = if (indicatorEngine.detectBearishEngulfing(prevCandle, currCandle)) {
            EngulfingPattern(
                type = EngulfingType.BEARISH,
                previousCandle = prevCandle,
                currentCandle = currCandle,
                valid = true
            )
        } else null

        return Pair(bullishEngulfing, bearishEngulfing)
    }

    // ============= HISTORY MANAGEMENT =============
    private fun saveToHistory(analysis: ChartAnalysis, signal: TradingSignal) {
        viewModelScope.launch {
            try {
                val entry = AnalysisHistoryEntry(
                    symbol = analysis.symbol ?: "UNKNOWN",
                    timeframe = analysis.timeframe?.label ?: "UNKNOWN",
                    signal = signal.signal.name,
                    confidence = signal.confidence.name,
                    trend = signal.trend.name,
                    reason = signal.reasons.joinToString(" | "),
                    entryZone = signal.entryZone?.let { 
                        "%.4f - %.4f".format(it.low, it.high) 
                    } ?: "N/A",
                    support = signal.support?.let { 
                        "%.4f - %.4f".format(it.low, it.high) 
                    } ?: "N/A",
                    resistance = signal.resistance?.let { 
                        "%.4f - %.4f".format(it.low, it.high) 
                    } ?: "N/A",
                    timestamp = LocalDateTime.now()
                )
                repository.save(entry)
            } catch (e: Exception) {
                _errorMessage.value = "Failed to save history: ${e.message}"
            }
        }
    }

    fun loadHistory() {
        viewModelScope.launch {
            try {
                repository.getAll().collect { entries ->
                    _historyState.value = entries
                }
            } catch (e: Exception) {
                _errorMessage.value = "Failed to load history: ${e.message}"
            }
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            try {
                repository.deleteAll()
                _historyState.value = emptyList()
            } catch (e: Exception) {
                _errorMessage.value = "Failed to clear history: ${e.message}"
            }
        }
    }

    fun deleteHistoryEntry(entry: AnalysisHistoryEntry) {
        viewModelScope.launch {
            try {
                repository.delete(entry)
                loadHistory()
            } catch (e: Exception) {
                _errorMessage.value = "Failed to delete entry: ${e.message}"
            }
        }
    }

    // ============= ERROR HANDLING =============
    fun clearError() {
        _errorMessage.value = null
    }

    fun clearSignal() {
        _signalState.value = null
        _analysisState.value = null
    }
}
