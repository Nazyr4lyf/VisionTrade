package com.visiontrade.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.visiontrade.ui.theme.VisionTradeColors
import kotlin.math.sin

@Composable
fun VisionTrade3DMarket(
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition()

    val scanProgress by transition.animateFloat(
        initialValue = -0.15f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 3600,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Restart
        )
    )

    val pulse by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800),
            repeatMode = RepeatMode.Reverse
        )
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(330.dp)
            .clip(RoundedCornerShape(30.dp))
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF102B55),
                        Color(0xFF09152C),
                        VisionTradeColors.backgroundDarker
                    )
                )
            )
    ) {

        Canvas(
            modifier = Modifier.matchParentSize()
        ) {
            val w = size.width
            val h = size.height

            val horizon = h * 0.43f

            // Ambient glow
            drawCircle(
                color = VisionTradeColors.brandBlue.copy(alpha = 0.10f),
                radius = h * 0.42f,
                center = Offset(w * 0.50f, horizon)
            )

            drawCircle(
                color = VisionTradeColors.brandCyan.copy(alpha = 0.06f),
                radius = h * 0.26f,
                center = Offset(w * 0.72f, h * 0.35f)
            )

            // Perspective horizontal grid
            for (i in 0..10) {
                val p = i / 10f
                val y = horizon + (p * p) * (h - horizon)

                drawLine(
                    color = VisionTradeColors.brandBlue.copy(
                        alpha = 0.10f + p * 0.08f
                    ),
                    start = Offset(0f, y),
                    end = Offset(w, y),
                    strokeWidth = 1f
                )
            }

            // Perspective vertical grid
            for (i in 0..16) {
                val x = i / 16f * w
                val center = w / 2f

                val path = Path().apply {
                    moveTo(
                        center + (x - center) * 0.05f,
                        horizon
                    )
                    lineTo(x, h)
                }

                drawPath(
                    path = path,
                    color = VisionTradeColors.brandCyan.copy(alpha = 0.10f),
                    style = Stroke(width = 1f)
                )
            }

            // Market data curve
            val curve = Path()

            for (i in 0..120) {
                val p = i / 120f
                val x = p * w

                val wave =
                    sin(i * 0.17f) * h * 0.035f +
                    sin(i * 0.055f) * h * 0.075f

                val trend = (1f - p) * h * 0.035f

                val y = h * 0.53f - wave - trend

                if (i == 0) {
                    curve.moveTo(x, y)
                } else {
                    curve.lineTo(x, y)
                }
            }

            // Glow behind market curve
            drawPath(
                path = curve,
                color = VisionTradeColors.brandCyan.copy(alpha = 0.12f),
                style = Stroke(width = 12f)
            )

            drawPath(
                path = curve,
                color = VisionTradeColors.brandCyan.copy(alpha = pulse),
                style = Stroke(width = 3.5f)
            )

            // Candlestick cluster
            val candleWidth = w * 0.035f
            val spacing = w * 0.060f

            for (i in 0 until 13) {
                val x = w * 0.10f + i * spacing

                val centerY =
                    h * 0.49f -
                    sin(i * 0.9f) * h * 0.045f -
                    i * h * 0.006f

                val bodyHeight =
                    h * (0.045f + (i % 4) * 0.012f)

                val bullish = i % 4 != 1

                val candleColor =
                    if (bullish) {
                        VisionTradeColors.signalBuy
                    } else {
                        VisionTradeColors.signalSell
                    }

                // Wick
                drawLine(
                    color = candleColor.copy(alpha = 0.85f),
                    start = Offset(
                        x + candleWidth / 2f,
                        centerY - bodyHeight * 2.1f
                    ),
                    end = Offset(
                        x + candleWidth / 2f,
                        centerY + bodyHeight * 2.1f
                    ),
                    strokeWidth = 2f
                )

                // Body
                drawRect(
                    color = candleColor.copy(alpha = 0.92f),
                    topLeft = Offset(
                        x,
                        centerY - bodyHeight
                    ),
                    size = androidx.compose.ui.geometry.Size(
                        candleWidth,
                        bodyHeight * 2f
                    )
                )
            }

            // Current price guide
            val priceY = h * 0.34f

            drawLine(
                color = VisionTradeColors.brandGreen.copy(alpha = 0.28f),
                start = Offset(w * 0.08f, priceY),
                end = Offset(w * 0.92f, priceY),
                strokeWidth = 1.5f
            )

            // Moving scanner
            val scanX = scanProgress * w

            drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color.Transparent,
                        VisionTradeColors.brandCyan.copy(alpha = 0.16f),
                        VisionTradeColors.brandCyan.copy(alpha = 0.45f),
                        Color.Transparent
                    ),
                    startX = scanX - 55f,
                    endX = scanX + 55f
                ),
                topLeft = Offset(scanX - 55f, 0f),
                size = androidx.compose.ui.geometry.Size(110f, h)
            )

            drawLine(
                color = VisionTradeColors.brandCyan.copy(alpha = 0.75f),
                start = Offset(scanX, 0f),
                end = Offset(scanX, h),
                strokeWidth = 2f
            )

            // Intelligence node
            val nodeX = w * 0.78f
            val nodeY = h * 0.31f

            drawCircle(
                color = VisionTradeColors.brandGreen.copy(alpha = 0.10f),
                radius = 22f + pulse * 8f,
                center = Offset(nodeX, nodeY)
            )

            drawCircle(
                color = VisionTradeColors.brandGreen.copy(alpha = 0.85f),
                radius = 5f,
                center = Offset(nodeX, nodeY)
            )

            drawCircle(
                color = VisionTradeColors.brandGreen.copy(alpha = 0.25f),
                radius = 11f,
                center = Offset(nodeX, nodeY),
                style = Stroke(width = 1.5f)
            )

            // Signal zone
            drawRoundRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        VisionTradeColors.brandGreen.copy(alpha = 0.04f),
                        VisionTradeColors.brandGreen.copy(alpha = 0.12f),
                        Color.Transparent
                    )
                ),
                topLeft = Offset(w * 0.58f, h * 0.57f),
                size = androidx.compose.ui.geometry.Size(
                    w * 0.29f,
                    h * 0.14f
                ),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(16f, 16f)
            )
        }

        Text(
            text = "LIVE MARKET VISION",
            color = VisionTradeColors.textPrimary.copy(alpha = 0.88f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(20.dp)
        )

        Text(
            text = "AI • STRUCTURE • SIGNAL",
            color = VisionTradeColors.brandCyan.copy(alpha = 0.80f),
            fontSize = 8.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.5.sp,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(20.dp)
        )

        Text(
            text = "SCANNING",
            color = VisionTradeColors.brandGreen.copy(alpha = 0.90f),
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
        )
    }
}
