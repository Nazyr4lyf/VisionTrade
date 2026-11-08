package com.visiontrade.domain.scalping

import java.math.BigDecimal
import kotlin.math.abs

/**
 * Closed-candle indicator calculations for the crypto scalping domain.
 *
 * Inputs must be chronological, unique and from one timeframe. Unclosed
 * candles are excluded. Missing history is represented by null, never zero.
 */
data class ScalpingIndicatorSnapshot(
    val ema20: Double?,
    val ema50: Double?,
    val rsi7: Double?,
    val atr14: Double?,
    val adx14: Double?,
    val vwap: Double?,
    val volumeSma20: Double?,
    val relativeVolume: Double?
)

class ScalpingIndicatorEngine {

    fun calculate(candles: List<CryptoCandle>): ScalpingIndicatorSnapshot {
        val data = validatedClosedCandles(candles)
        return ScalpingIndicatorSnapshot(
            ema20 = ema(data.map { it.close.toDouble() }, 20),
            ema50 = ema(data.map { it.close.toDouble() }, 50),
            rsi7 = rsi(data.map { it.close.toDouble() }, 7),
            atr14 = atr(data, 14),
            adx14 = adx(data, 14),
            vwap = vwap(data),
            volumeSma20 = simpleAverage(data.map { it.volume.toDouble() }, 20),
            relativeVolume = relativeVolume(data, 20)
        )
    }

    fun ema(values: List<Double>, period: Int): Double? {
        if (period <= 0 || values.size < period || values.any { !it.isFinite() }) return null
        val multiplier = 2.0 / (period + 1.0)
        var result = values.take(period).average()
        for (index in period until values.size) {
            result = values[index] * multiplier + result * (1.0 - multiplier)
        }
        return result.takeIf { it.isFinite() }
    }

    fun rsi(closes: List<Double>, period: Int): Double? {
        if (period <= 0 || closes.size < period + 1 || closes.any { !it.isFinite() }) return null
        var gain = 0.0
        var loss = 0.0
        for (index in 1..period) {
            val change = closes[index] - closes[index - 1]
            if (change > 0.0) gain += change else loss += abs(change)
        }
        var averageGain = gain / period
        var averageLoss = loss / period
        for (index in period + 1 until closes.size) {
            val change = closes[index] - closes[index - 1]
            averageGain = (averageGain * (period - 1) + maxOf(change, 0.0)) / period
            averageLoss = (averageLoss * (period - 1) + maxOf(-change, 0.0)) / period
        }
        val value = when {
            averageGain == 0.0 && averageLoss == 0.0 -> 50.0
            averageLoss == 0.0 -> 100.0
            else -> 100.0 - 100.0 / (1.0 + averageGain / averageLoss)
        }
        return value.takeIf { it.isFinite() }?.coerceIn(0.0, 100.0)
    }

    fun atr(candles: List<CryptoCandle>, period: Int): Double? {
        if (period <= 0 || candles.size < period + 1) return null
        val ranges = trueRanges(candles)
        if (ranges.size < period || ranges.any { !it.isFinite() }) return null
        var value = ranges.take(period).average()
        for (index in period until ranges.size) {
            value = (value * (period - 1) + ranges[index]) / period
        }
        return value.takeIf { it.isFinite() && it >= 0.0 }
    }

    fun adx(candles: List<CryptoCandle>, period: Int): Double? {
        if (period <= 0 || candles.size < period * 2 + 1) return null
        val tr = mutableListOf<Double>()
        val plusDm = mutableListOf<Double>()
        val minusDm = mutableListOf<Double>()
        for (index in 1 until candles.size) {
            val current = candles[index]
            val previous = candles[index - 1]
            val high = current.high.toDouble()
            val low = current.low.toDouble()
            val previousHigh = previous.high.toDouble()
            val previousLow = previous.low.toDouble()
            val previousClose = previous.close.toDouble()
            tr += maxOf(high - low, abs(high - previousClose), abs(low - previousClose))
            val up = high - previousHigh
            val down = previousLow - low
            plusDm += if (up > down && up > 0.0) up else 0.0
            minusDm += if (down > up && down > 0.0) down else 0.0
        }
        var smoothTr = tr.take(period).sum()
        var smoothPlus = plusDm.take(period).sum()
        var smoothMinus = minusDm.take(period).sum()
        val dx = mutableListOf<Double>()

        fun currentDx(): Double {
            if (smoothTr <= 0.0) return 0.0
            val plusDi = 100.0 * smoothPlus / smoothTr
            val minusDi = 100.0 * smoothMinus / smoothTr
            val sum = plusDi + minusDi
            return if (sum <= 0.0) 0.0 else 100.0 * abs(plusDi - minusDi) / sum
        }

        dx += currentDx()
        for (index in period until tr.size) {
            smoothTr = smoothTr - smoothTr / period + tr[index]
            smoothPlus = smoothPlus - smoothPlus / period + plusDm[index]
            smoothMinus = smoothMinus - smoothMinus / period + minusDm[index]
            dx += currentDx()
        }
        if (dx.size < period) return null
        var result = dx.take(period).average()
        for (index in period until dx.size) {
            result = (result * (period - 1) + dx[index]) / period
        }
        return result.takeIf { it.isFinite() }?.coerceIn(0.0, 100.0)
    }

    fun vwap(candles: List<CryptoCandle>): Double? {
        if (candles.isEmpty()) return null
        var weighted = 0.0
        var totalVolume = 0.0
        for (candle in candles) {
            val typicalPrice = (
                candle.high.toDouble() + candle.low.toDouble() + candle.close.toDouble()
            ) / 3.0
            val volume = candle.volume.toDouble()
            weighted += typicalPrice * volume
            totalVolume += volume
        }
        if (!weighted.isFinite() || !totalVolume.isFinite() || totalVolume <= 0.0) return null
        return (weighted / totalVolume).takeIf { it.isFinite() }
    }

    fun simpleAverage(values: List<Double>, period: Int): Double? {
        if (period <= 0 || values.size < period) return null
        val window = values.takeLast(period)
        if (window.any { !it.isFinite() }) return null
        return window.average().takeIf { it.isFinite() }
    }

    fun relativeVolume(candles: List<CryptoCandle>, period: Int): Double? {
        if (period <= 0 || candles.size < period + 1) return null
        val baseline = simpleAverage(candles.dropLast(1).map { it.volume.toDouble() }, period)
            ?: return null
        val latest = candles.last().volume.toDouble()
        if (baseline <= 0.0 || !latest.isFinite()) return null
        return (latest / baseline).takeIf { it.isFinite() }
    }

    private fun trueRanges(candles: List<CryptoCandle>): List<Double> =
        (1 until candles.size).map { index ->
            val current = candles[index]
            val previousClose = candles[index - 1].close.toDouble()
            maxOf(
                current.high.toDouble() - current.low.toDouble(),
                abs(current.high.toDouble() - previousClose),
                abs(current.low.toDouble() - previousClose)
            )
        }

    private fun validatedClosedCandles(candles: List<CryptoCandle>): List<CryptoCandle> {
        val closed = candles.filter { it.isClosed }
            .sortedBy { it.timestampMillis }
        require(closed.map { it.timeframe }.distinct().size <= 1) {
            "Indicator input must contain one timeframe only."
        }
        require(closed.zipWithNext().none { (a, b) -> a.timestampMillis == b.timestampMillis }) {
            "Duplicate candle timestamps are not allowed."
        }
        return closed
    }
}
