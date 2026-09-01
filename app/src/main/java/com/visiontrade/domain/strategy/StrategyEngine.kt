package com.visiontrade.domain.strategy

import com.visiontrade.domain.models.*
import com.visiontrade.domain.trade.TradeSetupEngine

/**
 * VisionTrade Deterministic Strategy Engine
 *
 * Important:
 * - No guessing.
 * - Missing/invalid data => WAIT.
 * - BUY/SELL requires full multi-timeframe confirmation.
 * - M15 is the execution timeframe.
 * - H1 and H4 are confirmation timeframes.
 *
 * BUY requirements:
 * 1. M15 EMA20 > EMA50
 * 2. H1 EMA20 > EMA50
 * 3. H4 EMA20 > EMA50
 * 4. M15 RSI14 is valid and < 70
 * 5. M15 ADX14 is valid and > 20
 * 6. Valid bullish engulfing pattern on M15
 * 7. Price is near valid M15 support
 * 8. M15/H1/H4 trend alignment is bullish
 *
 * SELL requirements are the exact inverse.
 */
class StrategyEngine {

    companion object {
        private const val TOTAL_CONDITIONS = 8
        private const val MIN_DATA_COMPLETENESS = 0.5
        private const val RSI_BUY_MAX = 70.0
        private const val RSI_SELL_MIN = 30.0
        private const val ADX_MIN = 20.0
        private const val ZONE_TOLERANCE_PERCENT = 0.2
    }

    fun analyze(analysis: ChartAnalysis): TradingSignal {

        // ------------------------------------------------------------
        // QUALITY GATES
        // ------------------------------------------------------------

        if (
            analysis.imageQuality == ImageQuality.POOR ||
            analysis.imageQuality == ImageQuality.UNKNOWN
        ) {
            return waitSignal(
                trend = Trend.UNKNOWN,
                reason = "Image quality is insufficient for reliable analysis."
            )
        }

        if (
            !analysis.dataCompleteness.isFinite() ||
            analysis.dataCompleteness < MIN_DATA_COMPLETENESS
        ) {
            return waitSignal(
                trend = Trend.UNKNOWN,
                reason = "Data completeness is insufficient for a reliable signal."
            )
        }

        // ------------------------------------------------------------
        // BASIC DATA VALIDATION
        // ------------------------------------------------------------

        if (!hasValidTimeframeIndicators(analysis.m15Data)) {
            return waitSignal(
                trend = Trend.UNKNOWN,
                reason = "M15 indicator data is incomplete."
            )
        }

        if (!hasValidTimeframeIndicators(analysis.h1Data)) {
            return waitSignal(
                trend = Trend.UNKNOWN,
                reason = "H1 indicator data is incomplete."
            )
        }

        if (!hasValidTimeframeIndicators(analysis.h4Data)) {
            return waitSignal(
                trend = Trend.UNKNOWN,
                reason = "H4 indicator data is incomplete."
            )
        }

        val currentPrice = analysis.m15Data.latestPrice ?: analysis.currentPrice

        if (currentPrice == null || !currentPrice.isFinite() || currentPrice <= 0.0) {
            return waitSignal(
                trend = Trend.UNKNOWN,
                reason = "Current market price is unavailable or invalid."
            )
        }

        // ------------------------------------------------------------
        // DETERMINE MARKET TREND
        // ------------------------------------------------------------

        val overallTrend = determineTrend(analysis)

        // ------------------------------------------------------------
        // CHECK BUY
        // ------------------------------------------------------------

        val buyConditions = checkBuyConditions(analysis, currentPrice)

        if (buyConditions.allMet) {
            return attachTradeSetup(TradingSignal(
                signal = Signal.BUY,
                confidence = Confidence.HIGH,
                trend = Trend.BULLISH,
                reasons = buyConditions.reasons,
                conditionsMet = buyConditions.metCount,
                totalConditions = TOTAL_CONDITIONS,
                entryZone = analysis.m15Data.structure.support,
                support = analysis.m15Data.structure.support,
                resistance = analysis.m15Data.structure.resistance,
                analysisDetails =
                    "BUY confirmed: M15 execution + H1/H4 trend confirmation + momentum + pattern + support."
            ))
        }

        // ------------------------------------------------------------
        // CHECK SELL
        // ------------------------------------------------------------

        val sellConditions = checkSellConditions(analysis, currentPrice)

        if (sellConditions.allMet) {
            return attachTradeSetup(TradingSignal(
                signal = Signal.SELL,
                confidence = Confidence.HIGH,
                trend = Trend.BEARISH,
                reasons = sellConditions.reasons,
                conditionsMet = sellConditions.metCount,
                totalConditions = TOTAL_CONDITIONS,
                entryZone = analysis.m15Data.structure.resistance,
                support = analysis.m15Data.structure.support,
                resistance = analysis.m15Data.structure.resistance,
                analysisDetails =
                    "SELL confirmed: M15 execution + H1/H4 trend confirmation + momentum + pattern + resistance."
            ))
        }

        // ------------------------------------------------------------
        // CONFLICT DETECTION
        // ------------------------------------------------------------

        val buyStrong = buyConditions.metCount >= 6
        val sellStrong = sellConditions.metCount >= 6

        if (buyStrong && sellStrong) {
            return attachTradeSetup(TradingSignal(
                signal = Signal.WAIT,
                confidence = Confidence.LOW,
                trend = Trend.UNKNOWN,
                reasons = listOf(
                    "Conflicting market conditions detected.",
                    "BUY and SELL conditions are both strongly represented."
                ),
                conditionsMet = maxOf(
                    buyConditions.metCount,
                    sellConditions.metCount
                ),
                totalConditions = TOTAL_CONDITIONS,
                support = analysis.m15Data.structure.support,
                resistance = analysis.m15Data.structure.resistance,
                analysisDetails = "WAIT: conflicting directional conditions."
            ))
        }

        // ------------------------------------------------------------
        // DEFAULT WAIT
        // ------------------------------------------------------------

        val reasons = (
            buyConditions.unmetReasons +
                sellConditions.unmetReasons
            )
            .distinct()
            .take(8)

        return attachTradeSetup(TradingSignal(
            signal = Signal.WAIT,
            confidence = Confidence.LOW,
            trend = overallTrend,
            reasons = if (reasons.isNotEmpty()) {
                reasons
            } else {
                listOf("Required conditions for BUY or SELL were not satisfied.")
            },
            conditionsMet = maxOf(
                buyConditions.metCount,
                sellConditions.metCount
            ),
            totalConditions = TOTAL_CONDITIONS,
            entryZone = null,
            support = analysis.m15Data.structure.support,
            resistance = analysis.m15Data.structure.resistance,
            analysisDetails = "WAIT: no complete deterministic setup."
        ))
    }

    // ================================================================
    // BUY STRATEGY
    // ================================================================

    private fun checkBuyConditions(
        analysis: ChartAnalysis,
        currentPrice: Double
    ): ConditionCheck {

        val reasons = mutableListOf<String>()
        val unmetReasons = mutableListOf<String>()
        var metCount = 0

        // 1. M15 EMA
        if (isBullishEma(analysis.m15Data)) {
            reasons.add("M15 EMA20 is above EMA50.")
            metCount++
        } else {
            unmetReasons.add("M15 EMA20 is not above EMA50.")
        }

        // 2. H1 EMA
        if (isBullishEma(analysis.h1Data)) {
            reasons.add("H1 EMA20 is above EMA50.")
            metCount++
        } else {
            unmetReasons.add("H1 EMA20 is not above EMA50.")
        }

        // 3. H4 EMA
        if (isBullishEma(analysis.h4Data)) {
            reasons.add("H4 EMA20 is above EMA50.")
            metCount++
        } else {
            unmetReasons.add("H4 EMA20 is not above EMA50.")
        }

        // 4. RSI
        val rsi = analysis.m15Data.rsi14.value

        if (
            rsi != null &&
            rsi.isFinite() &&
            rsi >= 0.0 &&
            rsi <= 100.0 &&
            rsi < RSI_BUY_MAX
        ) {
            reasons.add("M15 RSI14 is below 70.")
            metCount++
        } else {
            unmetReasons.add("M15 RSI14 is not in the BUY range.")
        }

        // 5. ADX
        val adx = analysis.m15Data.adx14.value

        if (
            adx != null &&
            adx.isFinite() &&
            adx >= 0.0 &&
            adx > ADX_MIN
        ) {
            reasons.add("M15 ADX14 confirms trend strength.")
            metCount++
        } else {
            unmetReasons.add("M15 ADX14 does not confirm sufficient trend strength.")
        }

        // 6. Bullish engulfing
        if (
            analysis.bullishEngulfing != null &&
            analysis.bullishEngulfing.valid &&
            analysis.bullishEngulfing.bodyEngulfs()
        ) {
            reasons.add("Valid bullish engulfing pattern detected on M15.")
            metCount++
        } else {
            unmetReasons.add("No valid bullish engulfing pattern on M15.")
        }

        // 7. Support proximity
        val support = analysis.m15Data.structure.support

        if (
            support != null &&
            support.low.isFinite() &&
            support.high.isFinite() &&
            support.low > 0.0 &&
            support.high >= support.low &&
            support.isNear(currentPrice, ZONE_TOLERANCE_PERCENT)
        ) {
            reasons.add("Price is near the M15 support zone.")
            metCount++
        } else {
            unmetReasons.add("Price is not near a valid M15 support zone.")
        }

        // 8. Full MTF trend alignment
        if (determineTrend(analysis) == Trend.BULLISH) {
            reasons.add("M15, H1 and H4 trends are bullish.")
            metCount++
        } else {
            unmetReasons.add("M15, H1 and H4 are not fully bullish.")
        }

        return ConditionCheck(
            allMet = metCount == TOTAL_CONDITIONS,
            metCount = metCount,
            reasons = reasons,
            unmetReasons = unmetReasons
        )
    }

    // ================================================================
    // SELL STRATEGY
    // ================================================================

    private fun checkSellConditions(
        analysis: ChartAnalysis,
        currentPrice: Double
    ): ConditionCheck {

        val reasons = mutableListOf<String>()
        val unmetReasons = mutableListOf<String>()
        var metCount = 0

        // 1. M15 EMA
        if (isBearishEma(analysis.m15Data)) {
            reasons.add("M15 EMA20 is below EMA50.")
            metCount++
        } else {
            unmetReasons.add("M15 EMA20 is not below EMA50.")
        }

        // 2. H1 EMA
        if (isBearishEma(analysis.h1Data)) {
            reasons.add("H1 EMA20 is below EMA50.")
            metCount++
        } else {
            unmetReasons.add("H1 EMA20 is not below EMA50.")
        }

        // 3. H4 EMA
        if (isBearishEma(analysis.h4Data)) {
            reasons.add("H4 EMA20 is below EMA50.")
            metCount++
        } else {
            unmetReasons.add("H4 EMA20 is not below EMA50.")
        }

        // 4. RSI
        val rsi = analysis.m15Data.rsi14.value

        if (
            rsi != null &&
            rsi.isFinite() &&
            rsi >= 0.0 &&
            rsi <= 100.0 &&
            rsi > RSI_SELL_MIN
        ) {
            reasons.add("M15 RSI14 is above 30.")
            metCount++
        } else {
            unmetReasons.add("M15 RSI14 is not in the SELL range.")
        }

        // 5. ADX
        val adx = analysis.m15Data.adx14.value

        if (
            adx != null &&
            adx.isFinite() &&
            adx >= 0.0 &&
            adx > ADX_MIN
        ) {
            reasons.add("M15 ADX14 confirms trend strength.")
            metCount++
        } else {
            unmetReasons.add("M15 ADX14 does not confirm sufficient trend strength.")
        }

        // 6. Bearish engulfing
        if (
            analysis.bearishEngulfing != null &&
            analysis.bearishEngulfing.valid &&
            analysis.bearishEngulfing.bodyEngulfs()
        ) {
            reasons.add("Valid bearish engulfing pattern detected on M15.")
            metCount++
        } else {
            unmetReasons.add("No valid bearish engulfing pattern on M15.")
        }

        // 7. Resistance proximity
        val resistance = analysis.m15Data.structure.resistance

        if (
            resistance != null &&
            resistance.low.isFinite() &&
            resistance.high.isFinite() &&
            resistance.low > 0.0 &&
            resistance.high >= resistance.low &&
            resistance.isNear(currentPrice, ZONE_TOLERANCE_PERCENT)
        ) {
            reasons.add("Price is near the M15 resistance zone.")
            metCount++
        } else {
            unmetReasons.add("Price is not near a valid M15 resistance zone.")
        }

        // 8. Full MTF trend alignment
        if (determineTrend(analysis) == Trend.BEARISH) {
            reasons.add("M15, H1 and H4 trends are bearish.")
            metCount++
        } else {
            unmetReasons.add("M15, H1 and H4 are not fully bearish.")
        }

        return ConditionCheck(
            allMet = metCount == TOTAL_CONDITIONS,
            metCount = metCount,
            reasons = reasons,
            unmetReasons = unmetReasons
        )
    }

    // ================================================================
    // VALIDATION
    // ================================================================

    private fun hasValidTimeframeIndicators(
        data: TimeframeData
    ): Boolean {

        val ema20 = data.ema20.value
        val ema50 = data.ema50.value
        val rsi = data.rsi14.value
        val adx = data.adx14.value

        return (
            ema20 != null &&
                ema20.isFinite() &&
                ema20 > 0.0 &&
                ema50 != null &&
                ema50.isFinite() &&
                ema50 > 0.0 &&
                rsi != null &&
                rsi.isFinite() &&
                rsi in 0.0..100.0 &&
                adx != null &&
                adx.isFinite() &&
                adx >= 0.0
            )
    }

    private fun isBullishEma(data: TimeframeData): Boolean {
        val ema20 = data.ema20.value
        val ema50 = data.ema50.value

        return (
            ema20 != null &&
                ema50 != null &&
                ema20.isFinite() &&
                ema50.isFinite() &&
                ema20 > 0.0 &&
                ema50 > 0.0 &&
                ema20 > ema50
            )
    }

    private fun isBearishEma(data: TimeframeData): Boolean {
        val ema20 = data.ema20.value
        val ema50 = data.ema50.value

        return (
            ema20 != null &&
                ema50 != null &&
                ema20.isFinite() &&
                ema50.isFinite() &&
                ema20 > 0.0 &&
                ema50 > 0.0 &&
                ema20 < ema50
            )
    }

    // ================================================================
    // TREND
    // ================================================================

    private fun determineTrend(
        analysis: ChartAnalysis
    ): Trend {

        val m15Trend = determineTimeframeTrend(analysis.m15Data)
        val h1Trend = determineTimeframeTrend(analysis.h1Data)
        val h4Trend = determineTimeframeTrend(analysis.h4Data)

        return when {
            m15Trend == Trend.BULLISH &&
                h1Trend == Trend.BULLISH &&
                h4Trend == Trend.BULLISH -> Trend.BULLISH

            m15Trend == Trend.BEARISH &&
                h1Trend == Trend.BEARISH &&
                h4Trend == Trend.BEARISH -> Trend.BEARISH

            m15Trend == Trend.RANGING &&
                h1Trend == Trend.RANGING &&
                h4Trend == Trend.RANGING -> Trend.RANGING

            else -> Trend.UNKNOWN
        }
    }

    private fun determineTimeframeTrend(
        data: TimeframeData
    ): Trend {

        val ema20 = data.ema20.value
        val ema50 = data.ema50.value

        if (
            ema20 == null ||
            ema50 == null ||
            !ema20.isFinite() ||
            !ema50.isFinite() ||
            ema20 <= 0.0 ||
            ema50 <= 0.0
        ) {
            return Trend.UNKNOWN
        }

        return when {
            ema20 > ema50 -> Trend.BULLISH
            ema20 < ema50 -> Trend.BEARISH
            else -> Trend.RANGING
        }
    }


    // ================================================================
    // TRADE SETUP INTEGRATION
    // ================================================================

    private fun attachTradeSetup(signal: TradingSignal): TradingSignal {
        val setup = TradeSetupEngine().build(signal)

        return signal.copy(
            tradeSetup = setup
        )
    }

    // ================================================================
    // WAIT RESULT
    // ================================================================

    private fun waitSignal(
        trend: Trend,
        reason: String
    ): TradingSignal {

        return attachTradeSetup(TradingSignal(
            signal = Signal.WAIT,
            confidence = Confidence.LOW,
            trend = trend,
            reasons = listOf(reason),
            conditionsMet = 0,
            totalConditions = TOTAL_CONDITIONS,
            entryZone = null,
            support = null,
            resistance = null,
            analysisDetails = "WAIT: quality/data gate failed."
        ))
    }

    // ================================================================
    // INTERNAL CONDITION RESULT
    // ================================================================

    private data class ConditionCheck(
        val allMet: Boolean,
        val metCount: Int,
        val reasons: List<String>,
        val unmetReasons: List<String>
    )
}
