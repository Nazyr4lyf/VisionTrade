package com.visiontrade.domain.models

import java.time.LocalDateTime

// ============= CANDLE & OHLC =============
data class Candle(
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Long = 0L,
    val timestamp: LocalDateTime = LocalDateTime.now()
) {
    val bodyHeight: Double = Math.abs(close - open)
    val isBullish: Boolean = close > open
    val isBearish: Boolean = close < open
    val isDoji: Boolean = bodyHeight < ((high - low) * 0.1)
}

// ============= INDICATORS =============
data class IndicatorValue(
    val name: String,
    val value: Double?,
    val status: DataStatus = DataStatus.AVAILABLE
)

enum class DataStatus {
    AVAILABLE,
    UNAVAILABLE,
    UNCERTAIN
}

data class EMAValue(
    val period: Int,
    val value: Double? = null,
    val status: DataStatus = DataStatus.UNAVAILABLE
)

data class RSIValue(
    val period: Int = 14,
    val value: Double? = null,
    val status: DataStatus = DataStatus.UNAVAILABLE
) {
    fun isBuyZone(): Boolean = value != null && value < 70
    fun isSellZone(): Boolean = value != null && value > 30
}

data class ADXValue(
    val period: Int = 14,
    val value: Double? = null,
    val status: DataStatus = DataStatus.UNAVAILABLE
) {
    fun isStrongTrend(): Boolean = value != null && value > 20
    fun isWeakTrend(): Boolean = value == null || value <= 20
}

// ============= SUPPORT & RESISTANCE =============
data class PriceZone(
    val low: Double,
    val high: Double,
    val type: ZoneType = ZoneType.DETECTED
) {
    val middle: Double = (low + high) / 2
    val width: Double = high - low
    
    fun contains(price: Double): Boolean = price >= low && price <= high
    
    fun isNear(price: Double, tolerancePercent: Double = 0.2): Boolean {
        val tolerance = price * (tolerancePercent / 100.0)
        return price >= (low - tolerance) && price <= (high + tolerance)
    }
}

enum class ZoneType {
    DETECTED,
    USER_MARKED
}

data class MarketStructure(
    val support: PriceZone? = null,
    val resistance: PriceZone? = null,
    val validStructure: Boolean = false
)

// ============= PATTERN DETECTION =============
data class EngulfingPattern(
    val type: EngulfingType,
    val previousCandle: Candle,
    val currentCandle: Candle,
    val valid: Boolean = false
) {
    fun bodyEngulfs(): Boolean {
        return when (type) {
            EngulfingType.BULLISH -> {
                previousCandle.isBearish &&
                currentCandle.isBullish &&
                currentCandle.open <= previousCandle.close &&
                currentCandle.close >= previousCandle.open
            }
            EngulfingType.BEARISH -> {
                previousCandle.isBullish &&
                currentCandle.isBearish &&
                currentCandle.open >= previousCandle.close &&
                currentCandle.close <= previousCandle.open
            }
        }
    }
}

enum class EngulfingType {
    BULLISH,
    BEARISH
}

// ============= TIMEFRAME DATA =============
data class TimeframeData(
    val timeframe: Timeframe,
    val candles: List<Candle> = emptyList(),
    val ema20: EMAValue = EMAValue(20),
    val ema50: EMAValue = EMAValue(50),
    val rsi14: RSIValue = RSIValue(),
    val adx14: ADXValue = ADXValue(),
    val latestPrice: Double? = null,
    val structure: MarketStructure = MarketStructure()
)

enum class Timeframe(val label: String, val minutes: Int) {
    M15("M15", 15),
    H1("H1", 60),
    H4("H4", 240)
}

// ============= MARKET ANALYSIS RESULT =============
data class ChartAnalysis(
    val symbol: String? = null,
    val timeframe: Timeframe? = null,
    val currentPrice: Double? = null,
    val m15Data: TimeframeData = TimeframeData(Timeframe.M15),
    val h1Data: TimeframeData = TimeframeData(Timeframe.H1),
    val h4Data: TimeframeData = TimeframeData(Timeframe.H4),
    val bullishEngulfing: EngulfingPattern? = null,
    val bearishEngulfing: EngulfingPattern? = null,
    val imageQuality: ImageQuality = ImageQuality.UNKNOWN,
    val dataCompleteness: Double = 0.0, // 0.0 - 1.0
    val timestamp: LocalDateTime = LocalDateTime.now()
)

enum class ImageQuality {
    EXCELLENT,
    GOOD,
    FAIR,
    POOR,
    UNKNOWN
}

// ============= TRADING SIGNAL =============
data class TradingSignal(
    val signal: Signal,
    val confidence: Confidence,
    val trend: Trend,
    val reasons: List<String> = emptyList(),
    val conditionsMet: Int = 0,
    val totalConditions: Int = 8,
    val entryZone: PriceZone? = null,
    val support: PriceZone? = null,
    val resistance: PriceZone? = null,
    val timestamp: LocalDateTime = LocalDateTime.now(),
    val analysisDetails: String = "",
    val tradeSetup: TradeSetup? = null
)

enum class Signal {
    BUY,
    SELL,
    WAIT
}

enum class Confidence {
    HIGH,
    MEDIUM,
    LOW
}

enum class Trend {
    BULLISH,
    BEARISH,
    RANGING,
    UNKNOWN
}

// ============= HISTORY ENTRY =============
data class AnalysisHistoryEntry(
    val id: Long = 0,
    val symbol: String,
    val timeframe: String,
    val signal: String,
    val confidence: String,
    val trend: String,
    val reason: String,
    val entryZone: String,
    val support: String,
    val resistance: String,
    val imageData: ByteArray? = null,
    val timestamp: LocalDateTime = LocalDateTime.now()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as AnalysisHistoryEntry
        return id == other.id && timestamp == other.timestamp
    }

    override fun hashCode(): Int = id.hashCode() * 31 + timestamp.hashCode()
}
