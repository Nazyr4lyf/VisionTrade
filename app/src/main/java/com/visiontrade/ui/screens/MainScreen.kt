package com.visiontrade.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.visiontrade.ui.components.PrimaryButton
import com.visiontrade.ui.components.VisionTrade3DMarket
import com.visiontrade.ui.theme.VisionTradeColors
import kotlinx.coroutines.delay

@Composable
fun MainScreen(
    onAnalyzeScreenshot: () -> Unit,
    onScanCamera: () -> Unit,
    onManualData: () -> Unit,
    onRiskCalculator: () -> Unit,
    onFlippingSimulator: () -> Unit,
    onHistory: () -> Unit,
    onSettings: () -> Unit
) {
    var showContent by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(120)
        showContent = true
    }

    val transition = rememberInfiniteTransition(label = "mainGlow")

    val glowAlpha by transition.animateFloat(
        initialValue = 0.12f,
        targetValue = 0.28f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 2200,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        VisionTradeColors.backgroundDarker,
                        VisionTradeColors.backgroundDark,
                        VisionTradeColors.backgroundDarker
                    )
                )
            )
    ) {
        Box(
            modifier = Modifier
                .size(360.dp)
                .align(Alignment.TopEnd)
                .alpha(glowAlpha)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            VisionTradeColors.brandBlue,
                            Color.Transparent
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 18.dp)
        ) {

            AnimatedVisibility(
                visible = showContent,
                enter = fadeIn(tween(700)) +
                    slideInVertically(
                        initialOffsetY = { -35 },
                        animationSpec = tween(
                            700,
                            easing = FastOutSlowInEasing
                        )
                    )
            ) {
                Column {

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "VISIONTRADE",
                                color = VisionTradeColors.textPrimary,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 2.5.sp
                            )

                            Text(
                                text = "MARKET INTELLIGENCE",
                                color = VisionTradeColors.brandCyan,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 2.sp
                            )
                        }

                        StatusPill()
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Text(
                        text = "See the Market.",
                        color = VisionTradeColors.textPrimary,
                        fontSize = 27.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = "Follow the Strategy.",
                        color = VisionTradeColors.brandGreen,
                        fontSize = 27.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(7.dp))

                    Text(
                        text = "Your intelligent workspace for market analysis, signals and risk.",
                        color = VisionTradeColors.textSecondary,
                        fontSize = 13.sp,
                        lineHeight = 19.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            AnimatedVisibility(
                visible = showContent,
                enter = fadeIn(
                    animationSpec = tween(
                        durationMillis = 900,
                        delayMillis = 180
                    )
                ) +
                    slideInVertically(
                        initialOffsetY = { 70 },
                        animationSpec = tween(
                            durationMillis = 900,
                            delayMillis = 180,
                            easing = FastOutSlowInEasing
                        )
                    )
            ) {
                VisionTrade3DMarket(
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            AnimatedVisibility(
                visible = showContent,
                enter = fadeIn(
                    animationSpec = tween(
                        durationMillis = 800,
                        delayMillis = 400
                    )
                )
            ) {
                Column {

                    SectionLabel(
                        title = "ANALYSIS WORKSPACE",
                        subtitle = "Choose how you want to read the market."
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    PrimaryButton(
                        text = "Analyze Screenshot",
                        onClick = onAnalyzeScreenshot,
                        modifier = Modifier.padding(vertical = 5.dp)
                    )

                    PrimaryButton(
                        text = "Scan Camera",
                        onClick = onScanCamera,
                        modifier = Modifier.padding(vertical = 5.dp)
                    )

                    PrimaryButton(
                        text = "Manual Data Entry",
                        onClick = onManualData,
                        modifier = Modifier.padding(vertical = 5.dp)
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    SectionLabel(
                        title = "TOOLS",
                        subtitle = "Risk and account planning utilities."
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    UtilityCard(
                        title = "Risk Calculator",
                        subtitle = "Calculate position risk and exposure.",
                        accent = VisionTradeColors.signalWait,
                        onClick = onRiskCalculator
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    UtilityCard(
                        title = "Account Growth Simulator",
                        subtitle = "Explore hypothetical account growth scenarios.",
                        accent = VisionTradeColors.brandGreen,
                        onClick = onFlippingSimulator
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = onHistory,
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor =
                                    VisionTradeColors.brandGreen.copy(alpha = 0.14f),
                                contentColor =
                                    VisionTradeColors.brandGreen
                            ),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text("History")
                        }

                        Button(
                            onClick = onSettings,
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor =
                                    VisionTradeColors.brandBlue.copy(alpha = 0.14f),
                                contentColor =
                                    VisionTradeColors.brandBlue
                            ),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Settings,
                                contentDescription = "Settings"
                            )

                            Spacer(modifier = Modifier.width(6.dp))

                            Text("Settings")
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = VisionTradeColors.surface
                        ),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp)
                        ) {
                            Text(
                                text = "⚠ Disclaimer",
                                color = VisionTradeColors.signalWait,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = "This application is for educational purposes only. It does not guarantee trading results and does not automatically execute trades.",
                                color = VisionTradeColors.textSecondary,
                                fontSize = 11.sp,
                                lineHeight = 17.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }
    }
}

@Composable
private fun StatusPill() {
    Row(
        modifier = Modifier
            .background(
                VisionTradeColors.brandGreen.copy(alpha = 0.10f),
                RoundedCornerShape(50.dp)
            )
            .padding(
                horizontal = 11.dp,
                vertical = 7.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .background(
                    VisionTradeColors.brandGreen,
                    RoundedCornerShape(50.dp)
                )
        )

        Spacer(modifier = Modifier.width(7.dp))

        Text(
            text = "SYSTEM READY",
            color = VisionTradeColors.brandGreen,
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
    }
}

@Composable
private fun SectionLabel(
    title: String,
    subtitle: String
) {
    Column {
        Text(
            text = title,
            color = VisionTradeColors.textPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp
        )

        Spacer(modifier = Modifier.height(3.dp))

        Text(
            text = subtitle,
            color = VisionTradeColors.textTertiary,
            fontSize = 10.sp
        )
    }
}

@Composable
private fun UtilityCard(
    title: String,
    subtitle: String,
    accent: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = VisionTradeColors.surface
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(15.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(5.dp, 42.dp)
                    .background(
                        accent,
                        RoundedCornerShape(10.dp)
                    )
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column {
                Text(
                    text = title,
                    color = VisionTradeColors.textPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(3.dp))

                Text(
                    text = subtitle,
                    color = VisionTradeColors.textSecondary,
                    fontSize = 10.sp,
                    lineHeight = 15.sp
                )
            }
        }
    }
}
