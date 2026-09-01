package com.visiontrade.domain.vision

import android.graphics.Bitmap
import android.graphics.Color
import com.visiontrade.domain.indicators.IndicatorEngine
import com.visiontrade.domain.models.*
import java.time.LocalDateTime

/**
 * VisionTrade local chart analyzer.
 *
 * IMPORTANT:
 * This analyzer extracts visually detectable candle candidates.
 * It does NOT claim to recover exact broker OHLC prices from pixels.
 *
 * The generated prices are normalized relative-price values intended
 * for deterministic indicator/pattern processing only.
 *
 * images[0] = M15
 * images[1] = H1
 * images[2] = H4
 */
class VisionChartAnalyzer(
    private val indicatorEngine: IndicatorEngine = IndicatorEngine()
) {

    data class Extraction(
        val candles: List<Candle>,
        val confidence: Double,
        val quality: ImageQuality,
        val reason: String
    )

    fun analyzeThreeTimeframes(images: List<Bitmap>): ChartAnalysis {

        if (images.size != 3) {
            return ChartAnalysis(
                imageQuality = ImageQuality.POOR,
                dataCompleteness = 0.0
            )
        }

        val extractions = images.map { extractCandles(it) }

        val averageConfidence =
            extractions
                .map { it.confidence }
                .average()
                .coerceIn(0.0, 1.0)

        val usableCount =
            extractions.count {
                it.candles.size >= MIN_REQUIRED_CANDLES &&
                    it.confidence >= MIN_USABLE_CONFIDENCE
            }

        /*
         * Never allow incomplete multi-timeframe data to reach
         * the deterministic strategy engine as if it were complete.
         */
        if (usableCount < 3) {

            val quality =
                when {
                    usableCount >= 2 -> ImageQuality.FAIR

                    extractions.any {
                        it.candles.isNotEmpty()
                    } -> ImageQuality.POOR

                    else -> ImageQuality.UNKNOWN
                }

            return ChartAnalysis(
                timeframe = Timeframe.M15,
                currentPrice = null,
                m15Data = TimeframeData(Timeframe.M15),
                h1Data = TimeframeData(Timeframe.H1),
                h4Data = TimeframeData(Timeframe.H4),
                bullishEngulfing = null,
                bearishEngulfing = null,
                imageQuality = quality,
                dataCompleteness = averageConfidence
            )
        }

        val m15 =
            buildTimeframe(
                candles = extractions[0].candles,
                timeframe = Timeframe.M15
            )

        val h1 =
            buildTimeframe(
                candles = extractions[1].candles,
                timeframe = Timeframe.H1
            )

        val h4 =
            buildTimeframe(
                candles = extractions[2].candles,
                timeframe = Timeframe.H4
            )

        val patterns =
            detectPatterns(m15.candles)

        val quality =
            when {
                averageConfidence >= 0.85 ->
                    ImageQuality.EXCELLENT

                averageConfidence >= 0.72 ->
                    ImageQuality.GOOD

                averageConfidence >= 0.60 ->
                    ImageQuality.FAIR

                else ->
                    ImageQuality.POOR
            }

        return ChartAnalysis(
            timeframe = Timeframe.M15,
            currentPrice = m15.latestPrice,
            m15Data = m15,
            h1Data = h1,
            h4Data = h4,
            bullishEngulfing = patterns.first,
            bearishEngulfing = patterns.second,
            imageQuality = quality,
            dataCompleteness = averageConfidence
        )
    }

    /**
     * Extract visually detectable candle candidates.
     *
     * The analyzer deliberately does not fabricate real market prices.
     * Pixel Y coordinates are converted into a normalized relative
     * price scale.
     */
    fun extractCandles(bitmap: Bitmap): Extraction {

        if (bitmap.width < MIN_BITMAP_WIDTH ||
            bitmap.height < MIN_BITMAP_HEIGHT
        ) {
            return Extraction(
                candles = emptyList(),
                confidence = 0.0,
                quality = ImageQuality.POOR,
                reason = "Image is too small."
            )
        }

        val left =
            (bitmap.width * CHART_LEFT_RATIO)
                .toInt()

        val right =
            (bitmap.width * CHART_RIGHT_RATIO)
                .toInt()

        val top =
            (bitmap.height * CHART_TOP_RATIO)
                .toInt()

        val bottom =
            (bitmap.height * CHART_BOTTOM_RATIO)
                .toInt()

        val chartWidth = right - left
        val chartHeight = bottom - top

        if (chartWidth <= 0 || chartHeight <= 0) {
            return Extraction(
                candles = emptyList(),
                confidence = 0.0,
                quality = ImageQuality.POOR,
                reason = "Invalid chart area."
            )
        }

        /*
         * Detect columns containing candle-like colored pixels.
         */
        val activeColumns =
            BooleanArray(chartWidth)

        val scanStep =
            if (bitmap.width >= 1800) 2 else 1

        for (x in 0 until chartWidth step scanStep) {

            var candlePixels = 0

            for (y in 0 until chartHeight step scanStep) {

                val color =
                    bitmap.getPixel(
                        left + x,
                        top + y
                    )

                if (
                    isBullish(color) ||
                    isBearish(color)
                ) {
                    candlePixels++
                }
            }

            activeColumns[x] =
                candlePixels >=
                    maxOf(
                        2,
                        chartHeight /
                            (140 * scanStep)
                    )
        }

        /*
         * Convert active columns into contiguous clusters.
         */
        val clusters =
            mutableListOf<IntRange>()

        var clusterStart = -1

        for (x in activeColumns.indices) {

            val active =
                activeColumns[x]

            if (active && clusterStart < 0) {
                clusterStart = x
            }

            val isLast =
                x == activeColumns.lastIndex

            if (
                (!active || isLast) &&
                clusterStart >= 0
            ) {

                val clusterEnd =
                    if (active) x else x - 1

                if (clusterEnd >= clusterStart) {
                    clusters +=
                        clusterStart..clusterEnd
                }

                clusterStart = -1
            }
        }

        /*
         * Merge very small gaps.
         */
        val merged =
            mutableListOf<IntRange>()

        for (range in clusters) {

            if (merged.isEmpty()) {
                merged += range
                continue
            }

            val previous =
                merged.last()

            val gap =
                range.first - previous.last

            if (gap <= MAX_CLUSTER_GAP) {

                merged.removeAt(
                    merged.lastIndex
                )

                merged +=
                    previous.first..range.last

            } else {
                merged += range
            }
        }

        /*
         * Analyze each candidate.
         */
        val candidates =
            mutableListOf<CandleCandidate>()

        for (range in merged) {

            val rangeWidth =
                range.last - range.first + 1

            /*
             * Very large colored regions are more likely to be
             * indicators, labels or UI elements than candles.
             */
            if (
                rangeWidth >
                    maxOf(
                        MAX_CANDLE_WIDTH,
                        chartWidth / 18
                    )
            ) {
                continue
            }

            var bullishPixels = 0
            var bearishPixels = 0

            val pixels =
                mutableListOf<Pair<Int, Int>>()

            for (x in range) {

                for (
                    y in 0 until chartHeight
                        step scanStep
                ) {

                    val color =
                        bitmap.getPixel(
                            left + x,
                            top + y
                        )

                    when {

                        isBullish(color) -> {
                            pixels += x to y
                            bullishPixels++
                        }

                        isBearish(color) -> {
                            pixels += x to y
                            bearishPixels++
                        }
                    }
                }
            }

            if (pixels.size < MIN_PIXEL_COUNT) {
                continue
            }

            val ys =
                pixels.map {
                    it.second
                }

            val minY =
                ys.minOrNull()
                    ?: continue

            val maxY =
                ys.maxOrNull()
                    ?: continue

            val height =
                maxY - minY

            if (height < MIN_CANDLE_HEIGHT) {
                continue
            }

            val bullish =
                bullishPixels >= bearishPixels

            candidates +=
                CandleCandidate(
                    range = range,
                    minY = minY,
                    maxY = maxY,
                    bullish = bullish,
                    pixelCount = pixels.size
                )
        }

        /*
         * Keep only plausible candle widths and retain the
         * most recent visible candidates.
         */
        val filtered =
            candidates
                .filter {
                    it.pixelCount >= MIN_PIXEL_COUNT &&
                        (
                            it.range.last -
                                it.range.first +
                                1
                            ) <= MAX_CANDLE_WIDTH
                }
                .sortedBy {
                    it.range.first
                }
                .takeLast(MAX_CANDLES)

        if (filtered.size < MIN_REQUIRED_CANDLES) {

            val confidence =
                (
                    filtered.size /
                        MIN_REQUIRED_CANDLES.toDouble()
                    )
                    .coerceIn(
                        0.0,
                        0.59
                    )

            return Extraction(
                candles = emptyList(),
                confidence = confidence,
                quality = ImageQuality.POOR,
                reason =
                    "Only ${filtered.size} reliable candle candidates detected."
            )
        }

        /*
         * Convert pixel coordinates to normalized relative prices.
         *
         * 100.0 is NOT a broker price.
         */
        val normalizedScale =
            NORMALIZED_SCALE

        val now =
            LocalDateTime.now()

        val candles =
            filtered.mapIndexed { index, candidate ->

                val high =
                    NORMALIZED_REFERENCE -
                        candidate.minY *
                        normalizedScale

                val low =
                    NORMALIZED_REFERENCE -
                        candidate.maxY *
                        normalizedScale

                val totalHeight =
                    candidate.maxY -
                        candidate.minY

                val bodyTop =
                    candidate.minY +
                        (
                            totalHeight *
                                BODY_RATIO
                            ).toInt()

                val bodyBottom =
                    candidate.maxY -
                        (
                            totalHeight *
                                BODY_RATIO
                            ).toInt()

                val open: Double
                val close: Double

                if (candidate.bullish) {

                    open =
                        NORMALIZED_REFERENCE -
                            bodyBottom *
                            normalizedScale

                    close =
                        NORMALIZED_REFERENCE -
                            bodyTop *
                            normalizedScale

                } else {

                    open =
                        NORMALIZED_REFERENCE -
                            bodyTop *
                            normalizedScale

                    close =
                        NORMALIZED_REFERENCE -
                            bodyBottom *
                            normalizedScale
                }

                val safeHigh =
                    maxOf(
                        high,
                        low
                    )

                val safeLow =
                    minOf(
                        high,
                        low
                    )

                Candle(
                    open =
                        open.coerceIn(
                            safeLow,
                            safeHigh
                        ),

                    high = safeHigh,

                    low = safeLow,

                    close =
                        close.coerceIn(
                            safeLow,
                            safeHigh
                        ),

                    volume = 0L,

                    /*
                     * Extraction itself has no knowledge of the
                     * actual broker candle timestamps.
                     *
                     * We preserve chronological ordering using
                     * M15 as the extraction base interval.
                     * buildTimeframe() then normalizes the interval
                     * for the requested timeframe.
                     */
                    timestamp =
                        now.minusMinutes(
                            (
                                filtered.size -
                                    index -
                                    1
                                ).toLong() *
                                Timeframe.M15.minutes
                        )
                )
            }

        val usable =
            candles.filter {
                it.high > it.low &&
                    it.open.isFinite() &&
                    it.close.isFinite() &&
                    it.open in it.low..it.high &&
                    it.close in it.low..it.high
            }

        val confidence =
            if (filtered.isEmpty()) {
                0.0
            } else {
                (
                    usable.size.toDouble() /
                        filtered.size
                    )
                    .coerceIn(
                        0.0,
                        1.0
                    )
            }

        val quality =
            when {
                confidence >= 0.85 ->
                    ImageQuality.EXCELLENT

                confidence >= 0.72 ->
                    ImageQuality.GOOD

                confidence >= 0.60 ->
                    ImageQuality.FAIR

                else ->
                    ImageQuality.POOR
            }

        return Extraction(
            candles = usable,
            confidence = confidence,
            quality = quality,
            reason =
                "Extracted ${usable.size} usable candle candidates from visual evidence."
        )
    }

    private fun buildTimeframe(
        candles: List<Candle>,
        timeframe: Timeframe
    ): TimeframeData {

        /*
         * Rebase timestamps to the requested timeframe.
         * The OHLC values remain normalized visual values.
         */
        val normalizedCandles =
            rebaseTimestamps(
                candles = candles,
                timeframe = timeframe
            )

        return TimeframeData(
            timeframe = timeframe,
            candles = normalizedCandles,

            ema20 =
                indicatorEngine.calculateEMA(
                    normalizedCandles,
                    20
                ),

            ema50 =
                indicatorEngine.calculateEMA(
                    normalizedCandles,
                    50
                ),

            rsi14 =
                indicatorEngine.calculateRSI(
                    normalizedCandles,
                    14
                ),

            adx14 =
                indicatorEngine.calculateADX(
                    normalizedCandles,
                    14
                ),

            latestPrice =
                normalizedCandles
                    .lastOrNull()
                    ?.close,

            structure =
                indicatorEngine.calculateSupportResistance(
                    normalizedCandles,
                    50
                )
        )
    }

    private fun rebaseTimestamps(
        candles: List<Candle>,
        timeframe: Timeframe
    ): List<Candle> {

        if (candles.isEmpty()) {
            return emptyList()
        }

        val now =
            LocalDateTime.now()

        return candles.mapIndexed { index, candle ->

            candle.copy(
                timestamp =
                    now.minusMinutes(
                        (
                            candles.size -
                                index -
                                1
                            ).toLong() *
                            timeframe.minutes
                    )
            )
        }
    }

    private fun detectPatterns(
        candles: List<Candle>
    ): Pair<EngulfingPattern?, EngulfingPattern?> {

        if (candles.size < 2) {
            return null to null
        }

        val previous =
            candles[candles.lastIndex - 1]

        val current =
            candles.last()

        val bullish =
            if (
                indicatorEngine.detectBullishEngulfing(
                    previous,
                    current
                )
            ) {
                EngulfingPattern(
                    type = EngulfingType.BULLISH,
                    previousCandle = previous,
                    currentCandle = current,
                    valid = true
                )
            } else {
                null
            }

        val bearish =
            if (
                indicatorEngine.detectBearishEngulfing(
                    previous,
                    current
                )
            ) {
                EngulfingPattern(
                    type = EngulfingType.BEARISH,
                    previousCandle = previous,
                    currentCandle = current,
                    valid = true
                )
            } else {
                null
            }

        return bullish to bearish
    }

    /*
     * Green/green-dominant pixels.
     */
    private fun isBullish(
        color: Int
    ): Boolean {

        val red =
            Color.red(color)

        val green =
            Color.green(color)

        val blue =
            Color.blue(color)

        return (
            green > 120 &&
                green > red * 1.15 &&
                green > blue * 0.95 &&
                green - red > 35
            )
    }

    /*
     * Red/red-dominant pixels.
     */
    private fun isBearish(
        color: Int
    ): Boolean {

        val red =
            Color.red(color)

        val green =
            Color.green(color)

        val blue =
            Color.blue(color)

        return (
            red > 150 &&
                red > green * 1.35 &&
                red > blue * 1.15 &&
                red - green > 60
            )
    }

    private data class CandleCandidate(
        val range: IntRange,
        val minY: Int,
        val maxY: Int,
        val bullish: Boolean,
        val pixelCount: Int
    )

    private companion object {

        const val MIN_BITMAP_WIDTH = 300
        const val MIN_BITMAP_HEIGHT = 200

        const val MIN_REQUIRED_CANDLES = 50
        const val MIN_USABLE_CONFIDENCE = 0.60

        const val MIN_PIXEL_COUNT = 8
        const val MIN_CANDLE_HEIGHT = 4
        const val MAX_CANDLE_WIDTH = 24
        const val MAX_CLUSTER_GAP = 3
        const val MAX_CANDLES = 120

        const val CHART_LEFT_RATIO = 0.05f
        const val CHART_RIGHT_RATIO = 0.97f
        const val CHART_TOP_RATIO = 0.08f
        const val CHART_BOTTOM_RATIO = 0.90f

        const val NORMALIZED_REFERENCE = 100.0
        const val NORMALIZED_SCALE = 0.01

        const val BODY_RATIO = 0.25
    }
}
