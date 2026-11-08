package com.visiontrade.domain.scalping

import java.math.BigDecimal

/**
 * Domain primitives for the crypto scalping engine.
 *
 * Financial values use BigDecimal so exchange tick/quantity rounding is
 * explicit and does not inherit the legacy Forex Double/pip assumptions.
 */
enum class CryptoMarketType {
    SPOT,
    USDT_PERPETUAL
}

enum class PositionSide {
    LONG,
    SHORT
}

enum class MarketTimeframe(val minutes: Int) {
    ONE_MINUTE(1),
    FIVE_MINUTES(5)
}

enum class MarketRegime {
    RANGE,
    TRENDING,
    VOLATILE,
    UNKNOWN
}

enum class ScalpingSignalAction {
    BUY,
    SELL,
    NO_TRADE
}

data class ExchangeInstrument(
    val symbol: String,
    val marketType: CryptoMarketType,
    val baseCoin: String,
    val quoteCoin: String,
    val priceTick: BigDecimal,
    val quantityStep: BigDecimal,
    val minimumQuantity: BigDecimal,
    val minimumNotional: BigDecimal,
    val contractMultiplier: BigDecimal = BigDecimal.ONE
) {
    init {
        require(symbol.isNotBlank()) { "Symbol is required." }
        require(baseCoin.isNotBlank()) { "Base coin is required." }
        require(quoteCoin.isNotBlank()) { "Quote coin is required." }
        require(priceTick > BigDecimal.ZERO) { "Price tick must be positive." }
        require(quantityStep > BigDecimal.ZERO) { "Quantity step must be positive." }
        require(minimumQuantity > BigDecimal.ZERO) { "Minimum quantity must be positive." }
        require(minimumNotional >= BigDecimal.ZERO) { "Minimum notional cannot be negative." }
        require(contractMultiplier > BigDecimal.ZERO) { "Contract multiplier must be positive." }
    }
}

data class CryptoCandle(
    val timestampMillis: Long,
    val open: BigDecimal,
    val high: BigDecimal,
    val low: BigDecimal,
    val close: BigDecimal,
    val volume: BigDecimal,
    val turnover: BigDecimal,
    val timeframe: MarketTimeframe,
    val isClosed: Boolean
) {
    init {
        require(timestampMillis > 0L) { "Candle timestamp must be positive." }
        require(open > BigDecimal.ZERO && high > BigDecimal.ZERO &&
            low > BigDecimal.ZERO && close > BigDecimal.ZERO) {
            "OHLC prices must be positive."
        }
        require(high >= low) { "Candle high must be greater than or equal to low." }
        require(open >= low && open <= high) { "Candle open must be within its range." }
        require(close >= low && close <= high) { "Candle close must be within its range." }
        require(volume >= BigDecimal.ZERO) { "Volume cannot be negative." }
        require(turnover >= BigDecimal.ZERO) { "Turnover cannot be negative." }
    }
}

data class SupportResistanceZone(
    val low: BigDecimal,
    val high: BigDecimal,
    val touches: Int,
    val lastConfirmedAtMillis: Long
) {
    init {
        require(low > BigDecimal.ZERO) { "Zone low must be positive." }
        require(high >= low) { "Zone high must be greater than or equal to low." }
        require(touches >= 1) { "A confirmed zone needs at least one touch." }
        require(lastConfirmedAtMillis > 0L) { "Zone confirmation time must be positive." }
    }

    val midpoint: BigDecimal
        get() = low.add(high).divide(BigDecimal("2"))

    fun contains(price: BigDecimal): Boolean =
        price >= low && price <= high
}

data class ScalpingRiskPolicy(
    val riskFraction: BigDecimal = BigDecimal("0.01"),
    val minimumNetRiskReward: BigDecimal = BigDecimal("2.0"),
    val preferredMaximumRiskReward: BigDecimal = BigDecimal("3.0")
) {
    init {
        require(riskFraction > BigDecimal.ZERO && riskFraction <= BigDecimal.ONE) {
            "Risk fraction must be greater than 0 and no more than 1."
        }
        require(minimumNetRiskReward >= BigDecimal("2.0")) {
            "Minimum net risk/reward cannot be below 2.0R."
        }
        require(preferredMaximumRiskReward >= minimumNetRiskReward) {
            "Preferred maximum R:R must be greater than or equal to minimum R:R."
        }
    }
}

data class ScalpingStrategyConfig(
    val riskPolicy: ScalpingRiskPolicy = ScalpingRiskPolicy(),
    val fiveMinuteLookback: Int = 100,
    val oneMinuteLookback: Int = 60,
    val pivotLeftBars: Int = 3,
    val pivotRightBars: Int = 3,
    val adxPeriod: Int = 14,
    val rsiPeriod: Int = 7,
    val atrPeriod: Int = 14,
    val volumeSmaPeriod: Int = 20,
    val maximumMarketDataAgeMillis: Long = 5_000L
) {
    init {
        require(fiveMinuteLookback >= 60) { "5m lookback must provide enough history for structure and indicators." }
        require(oneMinuteLookback >= 30) { "1m lookback must provide enough history for entry confirmation." }
        require(pivotLeftBars >= 1 && pivotRightBars >= 1) { "Pivot windows must be positive." }
        require(adxPeriod >= 2 && rsiPeriod >= 2 && atrPeriod >= 2 && volumeSmaPeriod >= 2) {
            "Indicator periods must be at least 2."
        }
        require(maximumMarketDataAgeMillis > 0L) { "Market-data age limit must be positive." }
    }
}
