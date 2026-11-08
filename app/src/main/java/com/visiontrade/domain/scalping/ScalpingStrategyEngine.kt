package com.visiontrade.domain.scalping

import java.math.BigDecimal
import kotlin.math.abs

data class ScalpingSignal(
    val symbol: String,
    val marketType: CryptoMarketType,
    val action: ScalpingSignalAction,
    val regime: MarketRegime,
    val referencePrice: BigDecimal?,
    val supportZone: SupportResistanceZone?,
    val resistanceZone: SupportResistanceZone?,
    val confidenceScore: Int,
    val reasons: List<String>,
    val evaluatedAtMillis: Long
)

class ScalpingStrategyEngine(
    private val indicatorEngine: ScalpingIndicatorEngine = ScalpingIndicatorEngine(),
    private val supportResistanceEngine: SupportResistanceEngine = SupportResistanceEngine(),
    private val config: ScalpingStrategyConfig = ScalpingStrategyConfig()
) {

    fun analyze(
        symbol: String,
        marketType: CryptoMarketType,
        fiveMinuteCandles: List<CryptoCandle>,
        oneMinuteCandles: List<CryptoCandle>,
        marketDataReceivedAtMillis: Long,
        nowMillis: Long
    ): ScalpingSignal {
        require(symbol.isNotBlank()) { "Symbol is required." }
        require(nowMillis > 0L && marketDataReceivedAtMillis > 0L) {
            "Market data timestamps must be positive."
        }

        fun noTrade(
            reason: String,
            regime: MarketRegime = MarketRegime.UNKNOWN,
            price: BigDecimal? = oneMinuteCandles.lastOrNull { it.isClosed }?.close,
            support: SupportResistanceZone? = null,
            resistance: SupportResistanceZone? = null
        ) = ScalpingSignal(
            symbol = symbol,
            marketType = marketType,
            action = ScalpingSignalAction.NO_TRADE,
            regime = regime,
            referencePrice = price,
            supportZone = support,
            resistanceZone = resistance,
            confidenceScore = 0,
            reasons = listOf(reason),
            evaluatedAtMillis = nowMillis
        )

        if (marketDataReceivedAtMillis > nowMillis ||
            nowMillis - marketDataReceivedAtMillis > config.maximumMarketDataAgeMillis
        ) {
            return noTrade("Market data is stale or has an invalid receive timestamp.")
        }

        val five = fiveMinuteCandles.filter { it.isClosed }.sortedBy { it.timestampMillis }
        val one = oneMinuteCandles.filter { it.isClosed }.sortedBy { it.timestampMillis }

        if (five.size < config.fiveMinuteLookback || one.size < config.oneMinuteLookback) {
            return noTrade("Insufficient closed-candle history for the 5m/1m strategy.")
        }
        if (five.any { it.timeframe != MarketTimeframe.FIVE_MINUTES } ||
            one.any { it.timeframe != MarketTimeframe.ONE_MINUTE }
        ) {
            return noTrade("Candle timeframe does not match the required 5m/1m strategy.")
        }
        if (five.zipWithNext().any { (a, b) -> a.timestampMillis >= b.timestampMillis } ||
            one.zipWithNext().any { (a, b) -> a.timestampMillis >= b.timestampMillis }
        ) {
            return noTrade("Candle timestamps must be unique and strictly increasing.")
        }

        val fiveIndicators = indicatorEngine.calculate(five)
        val oneIndicators = indicatorEngine.calculate(one)
        val price = one.last().close
        val atr = fiveIndicators.atr14
            ?: return noTrade("5m ATR is unavailable; volatility cannot be measured.")
        val adx = fiveIndicators.adx14
            ?: return noTrade("5m ADX is unavailable; market regime cannot be classified.")
        val rsi = oneIndicators.rsi7
            ?: return noTrade("1m RSI is unavailable.")
        val vwap = oneIndicators.vwap
            ?: return noTrade("1m VWAP is unavailable.")
        val relativeVolume = oneIndicators.relativeVolume
            ?: return noTrade("Relative volume is unavailable.")

        val regime = if (adx > 25.0) MarketRegime.TRENDING else MarketRegime.RANGE
        if (regime != MarketRegime.RANGE) {
            return noTrade(
                "Range-scalping is disabled while 5m ADX indicates a strong trend.",
                regime,
                price
            )
        }

        val atrTolerance = BigDecimal.valueOf(atr * 0.30)
        val zones = supportResistanceEngine.detectZones(
            candles = five,
            pivotLeftBars = config.pivotLeftBars,
            pivotRightBars = config.pivotRightBars,
            tolerance = atrTolerance,
            minimumTouches = 2
        )
        val support = zones.filter { it.type == PriceZoneType.SUPPORT }
            .minByOrNull { it.zone.midpoint.subtract(price).abs() }?.zone
        val resistance = zones.filter { it.type == PriceZoneType.RESISTANCE }
            .minByOrNull { it.zone.midpoint.subtract(price).abs() }?.zone

        if (support == null || resistance == null || support.midpoint >= resistance.midpoint) {
            return noTrade(
                "A valid confirmed support and resistance range was not found.",
                regime,
                price,
                support,
                resistance
            )
        }

        val distanceTolerance = BigDecimal.valueOf(atr * 0.30)
        val nearSupport = distanceToZone(price, support) <= distanceTolerance
        val nearResistance = distanceToZone(price, resistance) <= distanceTolerance
        val bullishEngulfing = isBullishEngulfing(one[one.lastIndex - 1], one.last())
        val bearishEngulfing = isBearishEngulfing(one[one.lastIndex - 1], one.last())
        val volumeConfirmed = relativeVolume >= 1.20
        val vwapValue = BigDecimal.valueOf(vwap)
        val aboveVwap = price >= vwapValue
        val belowVwap = price <= vwapValue

        val longReasons = buildList {
            if (nearSupport) add("Price is testing a confirmed 5m support zone.")
            if (bullishEngulfing) add("A closed 1m bullish engulfing candle confirms rejection.")
            if (aboveVwap) add("1m close is at or above the rolling VWAP.")
            if (rsi in 35.0..65.0) add("1m RSI(7) is in the configured momentum band.")
            if (volumeConfirmed) add("1m relative volume is at least 1.20x its prior 20-candle average.")
        }
        val shortReasons = buildList {
            if (nearResistance) add("Price is testing a confirmed 5m resistance zone.")
            if (bearishEngulfing) add("A closed 1m bearish engulfing candle confirms rejection.")
            if (belowVwap) add("1m close is at or below the rolling VWAP.")
            if (rsi in 35.0..65.0) add("1m RSI(7) is in the configured momentum band.")
            if (volumeConfirmed) add("1m relative volume is at least 1.20x its prior 20-candle average.")
        }

        val longConfirmed = nearSupport && bullishEngulfing && aboveVwap &&
            rsi in 35.0..65.0 && volumeConfirmed
        val shortConfirmed = nearResistance && bearishEngulfing && belowVwap &&
            rsi in 35.0..65.0 && volumeConfirmed

        if (longConfirmed && shortConfirmed) {
            return noTrade("Conflicting long and short confirmations were detected.", regime, price, support, resistance)
        }
        if (longConfirmed) {
            return ScalpingSignal(
                symbol, marketType, ScalpingSignalAction.BUY, regime, price,
                support, resistance, 100, longReasons, nowMillis
            )
        }
        if (shortConfirmed) {
            if (marketType == CryptoMarketType.SPOT) {
                return noTrade(
                    "Bearish setup detected, but Spot mode cannot open a short position.",
                    regime, price, support, resistance
                )
            }
            return ScalpingSignal(
                symbol, marketType, ScalpingSignalAction.SELL, regime, price,
                support, resistance, 100, shortReasons, nowMillis
            )
        }

        val bestReasons = if (longReasons.size >= shortReasons.size) longReasons else shortReasons
        return noTrade(
            if (bestReasons.isEmpty()) "Price is not at a confirmed support/resistance entry zone."
            else "Entry confirmation is incomplete: " + bestReasons.joinToString("; "),
            regime,
            price,
            support,
            resistance
        )
    }

    private fun distanceToZone(price: BigDecimal, zone: SupportResistanceZone): BigDecimal =
        when {
            price < zone.low -> zone.low.subtract(price)
            price > zone.high -> price.subtract(zone.high)
            else -> BigDecimal.ZERO
        }

    private fun isBullishEngulfing(previous: CryptoCandle, current: CryptoCandle): Boolean {
        val previousBearish = previous.close < previous.open
        val currentBullish = current.close > current.open
        return previousBearish && currentBullish &&
            current.open <= previous.close && current.close >= previous.open
    }

    private fun isBearishEngulfing(previous: CryptoCandle, current: CryptoCandle): Boolean {
        val previousBullish = previous.close > previous.open
        val currentBearish = current.close < current.open
        return previousBullish && currentBearish &&
            current.open >= previous.close && current.close <= previous.open
    }
}
