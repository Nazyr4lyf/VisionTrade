package com.visiontrade.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import com.visiontrade.R

// ============= VISIONTRADE COLORS =============
val BrandGreen = Color(0xFF00FF41)
val BrandGreenBright = Color(0xFF39FF14)
val BrandBlue = Color(0xFF0066FF)
val BrandBlueBright = Color(0xFF1E90FF)
val BrandCyan = Color(0xFF00D9FF)
val BrandWhite = Color(0xFFFFFFFF)

// Trading Signals
val SignalBuy = Color(0xFF00FF41)
val SignalSell = Color(0xFFFF0040)
val SignalWait = Color(0xFFFFB800)

// UI Colors
val BackgroundDark = Color(0xFF0A0E27)
val BackgroundDarker = Color(0xFF050810)
val Surface = Color(0xFF1A1F3A)
val SurfaceVariant = Color(0xFF2A3050)
val TextPrimary = Color(0xFFFFFFFF)
val TextSecondary = Color(0xFFB0B0B0)
val TextTertiary = Color(0xFF808080)
val Border = Color(0xFF2A3050)

// ============= COLOR SCHEME =============
private val DarkColorScheme = darkColorScheme(
    primary = BrandBlue,
    onPrimary = BackgroundDark,
    secondary = BrandGreen,
    onSecondary = BackgroundDark,
    tertiary = BrandCyan,
    onTertiary = BackgroundDark,
    background = BackgroundDark,
    onBackground = TextPrimary,
    surface = Surface,
    onSurface = TextPrimary,
    error = SignalSell,
    onError = BackgroundDark
)

// ============= THEME =============
@Composable
fun VisionTradeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = DarkColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = VisionTradeTypography,
        content = content
    )
}

// ============= COLOR PALETTE OBJECT =============
object VisionTradeColors {
    val brandGreen = BrandGreen
    val brandBlue = BrandBlue
    val brandBlueBright = BrandBlueBright
    val brandCyan = BrandCyan
    val brandWhite = BrandWhite
    
    val signalBuy = SignalBuy
    val signalSell = SignalSell
    val signalWait = SignalWait
    
    val backgroundDark = BackgroundDark
    val backgroundDarker = BackgroundDarker
    val surface = Surface
    val surfaceVariant = SurfaceVariant
    
    val textPrimary = TextPrimary
    val textSecondary = TextSecondary
    val textTertiary = TextTertiary
    val border = Border
    
    val candleBullish = SignalBuy
    val candleBearish = SignalSell
    val ema20 = BrandGreenBright
    val ema50 = BrandBlue
}
