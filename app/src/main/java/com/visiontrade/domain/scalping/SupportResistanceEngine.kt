package com.visiontrade.domain.scalping

import java.math.BigDecimal

enum class PriceZoneType {
    SUPPORT,
    RESISTANCE
}

data class DetectedPriceZone(
    val type: PriceZoneType,
    val zone: SupportResistanceZone
)

class SupportResistanceEngine {

    private data class Pivot(
        val price: BigDecimal,
        val timestampMillis: Long
    )

    /**
     * Finds confirmed swing pivots and clusters nearby prices into zones.
     * A pivot is emitted only after pivotRightBars later closed candles exist,
     * preventing future-candle look-ahead and repainting.
     */
    fun detectZones(
        candles: List<CryptoCandle>,
        pivotLeftBars: Int = 3,
        pivotRightBars: Int = 3,
        tolerance: BigDecimal,
        minimumTouches: Int = 2
    ): List<DetectedPriceZone> {
        require(pivotLeftBars >= 1 && pivotRightBars >= 1) {
            "Pivot windows must be positive."
        }
        require(tolerance >= BigDecimal.ZERO) { "Zone tolerance cannot be negative." }
        require(minimumTouches >= 1) { "Minimum touches must be positive." }

        val closed = candles.filter { it.isClosed }.sortedBy { it.timestampMillis }
        require(closed.map { it.timeframe }.distinct().size <= 1) {
            "Support/resistance input must contain one timeframe only."
        }
        require(closed.zipWithNext().none { (a, b) -> a.timestampMillis == b.timestampMillis }) {
            "Duplicate candle timestamps are not allowed."
        }

        val lastConfirmedIndex = closed.size - pivotRightBars - 1
        if (lastConfirmedIndex < pivotLeftBars) return emptyList()

        val highs = mutableListOf<Pivot>()
        val lows = mutableListOf<Pivot>()

        for (index in pivotLeftBars..lastConfirmedIndex) {
            val candidate = closed[index]
            val left = closed.subList(index - pivotLeftBars, index)
            val right = closed.subList(index + 1, index + pivotRightBars + 1)

            val isSwingHigh = left.all { candidate.high > it.high } &&
                right.all { candidate.high > it.high }
            val isSwingLow = left.all { candidate.low < it.low } &&
                right.all { candidate.low < it.low }

            if (isSwingHigh) highs += Pivot(candidate.high, candidate.timestampMillis)
            if (isSwingLow) lows += Pivot(candidate.low, candidate.timestampMillis)
        }

        return buildList {
            addAll(cluster(highs, PriceZoneType.RESISTANCE, tolerance, minimumTouches))
            addAll(cluster(lows, PriceZoneType.SUPPORT, tolerance, minimumTouches))
        }.sortedWith(
            compareBy<DetectedPriceZone> { it.zone.midpoint }
                .thenBy { it.type.name }
        )
    }

    private fun cluster(
        pivots: List<Pivot>,
        type: PriceZoneType,
        tolerance: BigDecimal,
        minimumTouches: Int
    ): List<DetectedPriceZone> {
        if (pivots.isEmpty()) return emptyList()
        val sorted = pivots.sortedBy { it.price }
        val groups = mutableListOf<MutableList<Pivot>>()

        for (pivot in sorted) {
            val current = groups.lastOrNull()
            if (current == null) {
                groups += mutableListOf(pivot)
            } else {
                val center = current.map { it.price }.averageBigDecimal()
                if (pivot.price.subtract(center).abs() <= tolerance) {
                    current += pivot
                } else {
                    groups += mutableListOf(pivot)
                }
            }
        }

        return groups.filter { it.size >= minimumTouches }.map { group ->
            val prices = group.map { it.price }
            DetectedPriceZone(
                type = type,
                zone = SupportResistanceZone(
                    low = prices.minOrNull()!!,
                    high = prices.maxOrNull()!!,
                    touches = group.size,
                    lastConfirmedAtMillis = group.maxOf { it.timestampMillis }
                )
            )
        }
    }

    private fun List<BigDecimal>.averageBigDecimal(): BigDecimal {
        if (isEmpty()) return BigDecimal.ZERO
        return fold(BigDecimal.ZERO, BigDecimal::add)
            .divide(BigDecimal(size), java.math.MathContext.DECIMAL128)
    }
}
