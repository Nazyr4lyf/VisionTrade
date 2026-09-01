package com.visiontrade.domain.indicators

import com.visiontrade.domain.models.*
import kotlin.math.abs

/**
 * Deterministic technical indicator engine.
 *
 * Rules:
 * - Never fabricates indicator values when data is insufficient.
 * - Rejects invalid candle data.
 * - Uses deterministic calculations.
 * - Safe for empty/degenerate datasets.
 */
class IndicatorEngine {

    // =========================================================
    // VALIDATION
    // =========================================================

    private fun isValidCandle(candle: Candle): Boolean {
        return candle.open.isFinite() &&
                candle.high.isFinite() &&
                candle.low.isFinite() &&
                candle.close.isFinite() &&
                candle.high >= candle.low &&
                candle.open in candle.low..candle.high &&
                candle.close in candle.low..candle.high
    }

    private fun validCandles(candles: List<Candle>): List<Candle> {
        return candles.filter(::isValidCandle)
    }

    // =========================================================
    // EMA
    // =========================================================

    fun calculateEMA(
        candles: List<Candle>,
        period: Int
    ): EMAValue {

        if (period <= 0) {
            return EMAValue(
                period = period,
                status = DataStatus.UNAVAILABLE
            )
        }

        val valid = validCandles(candles)

        if (valid.size < period) {
            return EMAValue(
                period = period,
                status = DataStatus.UNAVAILABLE
            )
        }

        val closes = valid.map { it.close }

        val multiplier = 2.0 / (period + 1)

        // Standard initialization with SMA.
        var ema = closes
            .take(period)
            .average()

        for (i in period until closes.size) {
            ema =
                (closes[i] * multiplier) +
                        (ema * (1.0 - multiplier))
        }

        return EMAValue(
            period = period,
            value = ema,
            status = DataStatus.AVAILABLE
        )
    }

    fun calculateEMAMultiple(
        candles: List<Candle>,
        periods: List<Int>
    ): List<EMAValue> {
        return periods.map { period ->
            calculateEMA(candles, period)
        }
    }

    // =========================================================
    // RSI — WILDER'S METHOD
    // =========================================================

    fun calculateRSI(
        candles: List<Candle>,
        period: Int = 14
    ): RSIValue {

        if (period <= 0) {
            return RSIValue(
                period = period,
                status = DataStatus.UNAVAILABLE
            )
        }

        val valid = validCandles(candles)

        if (valid.size < period + 1) {
            return RSIValue(
                period = period,
                status = DataStatus.UNAVAILABLE
            )
        }

        val changes = mutableListOf<Double>()

        for (i in 1 until valid.size) {
            changes += valid[i].close - valid[i - 1].close
        }

        val gains = changes.map { change ->
            if (change > 0.0) change else 0.0
        }

        val losses = changes.map { change ->
            if (change < 0.0) abs(change) else 0.0
        }

        var averageGain =
            gains.take(period).average()

        var averageLoss =
            losses.take(period).average()

        for (i in period until changes.size) {

            averageGain =
                ((averageGain * (period - 1)) + gains[i]) /
                        period

            averageLoss =
                ((averageLoss * (period - 1)) + losses[i]) /
                        period
        }

        val rsi = when {
            averageLoss == 0.0 && averageGain == 0.0 -> 50.0

            averageLoss == 0.0 -> 100.0

            else -> {
                val rs = averageGain / averageLoss
                100.0 - (100.0 / (1.0 + rs))
            }
        }

        return RSIValue(
            period = period,
            value = rsi.coerceIn(0.0, 100.0),
            status = DataStatus.AVAILABLE
        )
    }

    // =========================================================
    // ADX — WILDER'S METHOD
    // =========================================================

    fun calculateADX(
        candles: List<Candle>,
        period: Int = 14
    ): ADXValue {

        if (period <= 0) {
            return ADXValue(
                period = period,
                status = DataStatus.UNAVAILABLE
            )
        }

        val valid = validCandles(candles)

        /*
         * A robust ADX calculation needs enough candles to create
         * initial TR/+DM/-DM, initial DX values and then smooth ADX.
         */
        if (valid.size < (period * 2) + 1) {
            return ADXValue(
                period = period,
                status = DataStatus.UNAVAILABLE
            )
        }

        val trueRanges = mutableListOf<Double>()
        val plusDM = mutableListOf<Double>()
        val minusDM = mutableListOf<Double>()

        for (i in 1 until valid.size) {

            val current = valid[i]
            val previous = valid[i - 1]

            val tr = maxOf(
                current.high - current.low,
                abs(current.high - previous.close),
                abs(current.low - previous.close)
            )

            val upMove =
                current.high - previous.high

            val downMove =
                previous.low - current.low

            val positiveDM =
                if (upMove > downMove && upMove > 0.0) {
                    upMove
                } else {
                    0.0
                }

            val negativeDM =
                if (downMove > upMove && downMove > 0.0) {
                    downMove
                } else {
                    0.0
                }

            trueRanges += tr
            plusDM += positiveDM
            minusDM += negativeDM
        }

        if (trueRanges.size < period) {
            return ADXValue(
                period = period,
                status = DataStatus.UNAVAILABLE
            )
        }

        /*
         * Wilder smoothing.
         *
         * We keep smoothed TR/+DM/-DM as sums initially.
         */
        var smoothedTR =
            trueRanges.take(period).sum()

        var smoothedPlusDM =
            plusDM.take(period).sum()

        var smoothedMinusDM =
            minusDM.take(period).sum()

        val dxValues = mutableListOf<Double>()

        fun calculateDX(
            tr: Double,
            positiveDM: Double,
            negativeDM: Double
        ): Double {

            if (tr <= 0.0) {
                return 0.0
            }

            val plusDI =
                (positiveDM / tr) * 100.0

            val minusDI =
                (negativeDM / tr) * 100.0

            val denominator =
                plusDI + minusDI

            if (denominator == 0.0) {
                return 0.0
            }

            return (
                    abs(plusDI - minusDI) /
                            denominator
                    ) * 100.0
        }

        // First DX.
        dxValues += calculateDX(
            smoothedTR,
            smoothedPlusDM,
            smoothedMinusDM
        )

        /*
         * Wilder recursive smoothing.
         */
        for (i in period until trueRanges.size) {

            smoothedTR =
                smoothedTR -
                        (smoothedTR / period) +
                        trueRanges[i]

            smoothedPlusDM =
                smoothedPlusDM -
                        (smoothedPlusDM / period) +
                        plusDM[i]

            smoothedMinusDM =
                smoothedMinusDM -
                        (smoothedMinusDM / period) +
                        minusDM[i]

            dxValues += calculateDX(
                smoothedTR,
                smoothedPlusDM,
                smoothedMinusDM
            )
        }

        if (dxValues.size < period) {
            return ADXValue(
                period = period,
                status = DataStatus.UNAVAILABLE
            )
        }

        /*
         * Initial ADX = SMA of first period DX values.
         */
        var adx =
            dxValues.take(period).average()

        /*
         * Wilder smoothing of ADX.
         */
        for (i in period until dxValues.size) {
            adx =
                (
                        (adx * (period - 1)) +
                                dxValues[i]
                        ) / period
        }

        return ADXValue(
            period = period,
            value = adx.coerceIn(0.0, 100.0),
            status = DataStatus.AVAILABLE
        )
    }

    // =========================================================
    // TREND
    // =========================================================

    fun determineTrend(
        ema20: Double?,
        ema50: Double?
    ): Trend {

        if (
            ema20 == null ||
            ema50 == null ||
            !ema20.isFinite() ||
            !ema50.isFinite()
        ) {
            return Trend.UNKNOWN
        }

        return when {
            ema20 > ema50 -> Trend.BULLISH
            ema20 < ema50 -> Trend.BEARISH
            else -> Trend.RANGING
        }
    }

    fun determineTrendMultiTimeframe(
        m15Ema20: Double?,
        m15Ema50: Double?,
        h1Ema20: Double?,
        h1Ema50: Double?,
        h4Ema20: Double?,
        h4Ema50: Double?
    ): Trend {

        val m15Trend =
            determineTrend(m15Ema20, m15Ema50)

        val h1Trend =
            determineTrend(h1Ema20, h1Ema50)

        val h4Trend =
            determineTrend(h4Ema20, h4Ema50)

        return when {

            m15Trend == h1Trend &&
                    h1Trend == h4Trend ->
                m15Trend

            m15Trend == h1Trend &&
                    m15Trend == Trend.BULLISH ->
                Trend.BULLISH

            m15Trend == h1Trend &&
                    m15Trend == Trend.BEARISH ->
                Trend.BEARISH

            h1Trend == h4Trend &&
                    h1Trend == Trend.BULLISH ->
                Trend.BULLISH

            h1Trend == h4Trend &&
                    h1Trend == Trend.BEARISH ->
                Trend.BEARISH

            else ->
                Trend.UNKNOWN
        }
    }

    // =========================================================
    // ENGULFING PATTERNS
    // =========================================================

    fun detectBullishEngulfing(
        previousCandle: Candle,
        currentCandle: Candle
    ): Boolean {

        if (
            !isValidCandle(previousCandle) ||
            !isValidCandle(currentCandle)
        ) {
            return false
        }

        return previousCandle.isBearish &&
                currentCandle.isBullish &&
                currentCandle.open <= previousCandle.close &&
                currentCandle.close >= previousCandle.open
    }

    fun detectBearishEngulfing(
        previousCandle: Candle,
        currentCandle: Candle
    ): Boolean {

        if (
            !isValidCandle(previousCandle) ||
            !isValidCandle(currentCandle)
        ) {
            return false
        }

        return previousCandle.isBullish &&
                currentCandle.isBearish &&
                currentCandle.open >= previousCandle.close &&
                currentCandle.close <= previousCandle.open
    }

    // =========================================================
    // SUPPORT / RESISTANCE
    // =========================================================

    fun calculateSupportResistance(
        candles: List<Candle>,
        lookbackPeriod: Int = 50
    ): MarketStructure {

        if (lookbackPeriod < 3) {
            return MarketStructure(
                validStructure = false
            )
        }

        val valid =
            validCandles(candles)

        if (valid.size < lookbackPeriod) {
            return MarketStructure(
                validStructure = false
            )
        }

        val recentCandles =
            valid.takeLast(lookbackPeriod)

        val resistance =
            findResistanceZone(recentCandles)

        val support =
            findSupportZone(recentCandles)

        return MarketStructure(
            support = support,
            resistance = resistance,
            validStructure =
                support != null ||
                        resistance != null
        )
    }

    private fun findResistanceZone(
        candles: List<Candle>
    ): PriceZone? {

        if (candles.size < 3) {
            return null
        }

        val highs =
            candles.map { it.high }

        val maxHigh =
            highs.maxOrNull()
                ?: return null

        val minHigh =
            highs.minOrNull()
                ?: return null

        val range =
            maxHigh - minHigh

        val swingHighs =
            mutableListOf<Double>()

        for (i in 1 until candles.lastIndex) {

            val current =
                candles[i].high

            val previous =
                candles[i - 1].high

            val next =
                candles[i + 1].high

            if (
                current >= previous &&
                current >= next &&
                (
                        range == 0.0 ||
                                current >=
                                minHigh + range * 0.70
                        )
            ) {
                swingHighs += current
            }
        }

        if (swingHighs.isEmpty()) {
            return PriceZone(
                low = maxHigh * 0.98,
                high = maxHigh,
                type = ZoneType.DETECTED
            )
        }

        val average =
            swingHighs.average()

        return PriceZone(
            low = average * 0.99,
            high = average * 1.01,
            type = ZoneType.DETECTED
        )
    }

    private fun findSupportZone(
        candles: List<Candle>
    ): PriceZone? {

        if (candles.size < 3) {
            return null
        }

        val lows =
            candles.map { it.low }

        val maxLow =
            lows.maxOrNull()
                ?: return null

        val minLow =
            lows.minOrNull()
                ?: return null

        val range =
            maxLow - minLow

        val swingLows =
            mutableListOf<Double>()

        for (i in 1 until candles.lastIndex) {

            val current =
                candles[i].low

            val previous =
                candles[i - 1].low

            val next =
                candles[i + 1].low

            if (
                current <= previous &&
                current <= next &&
                (
                        range == 0.0 ||
                                current <=
                                maxLow - range * 0.70
                        )
            ) {
                swingLows += current
            }
        }

        if (swingLows.isEmpty()) {
            return PriceZone(
                low = minLow,
                high = minLow * 1.02,
                type = ZoneType.DETECTED
            )
        }

        val average =
            swingLows.average()

        return PriceZone(
            low = average * 0.99,
            high = average * 1.01,
            type = ZoneType.DETECTED
        )
    }
}
