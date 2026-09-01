package com.visiontrade.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.visiontrade.ui.components.VisionTrade3DMarket
import com.visiontrade.ui.theme.VisionTradeColors
import kotlinx.coroutines.delay

@Composable
fun WelcomeScreen(
    onGetStarted: () -> Unit
) {
    var visible by remember { mutableStateOf(false) }
    var entering by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(180)
        visible = true
    }

    val transition = rememberInfiniteTransition()

    val glow by transition.animateFloat(
        initialValue = 0.12f,
        targetValue = 0.32f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 2400,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        )
    )

    val buttonScale by animateFloatAsState(
        targetValue = if (entering) 0.96f else 1f,
        animationSpec = tween(120),
        label = "buttonScale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF02050C),
                        VisionTradeColors.backgroundDarker,
                        Color(0xFF071329),
                        VisionTradeColors.backgroundDarker
                    )
                )
            )
    ) {

        // Ambient blue intelligence glow
        Box(
            modifier = Modifier
                .fillMaxSize()
                .alpha(glow)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            VisionTradeColors.brandBlue.copy(alpha = 0.45f),
                            Color.Transparent
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    horizontal = 20.dp,
                    vertical = 26.dp
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {

            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(900)) +
                    slideInVertically(
                        initialOffsetY = { -45 },
                        animationSpec = tween(
                            900,
                            easing = FastOutSlowInEasing
                        )
                    )
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "VISION",
                        color = VisionTradeColors.textPrimary,
                        fontSize = 36.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 5.sp
                    )

                    Text(
                        text = "TRADE",
                        color = VisionTradeColors.brandBlueBright,
                        fontSize = 36.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 5.sp
                    )

                    Spacer(
                        modifier = Modifier.height(7.dp)
                    )

                    Text(
                        text = "MARKET INTELLIGENCE",
                        color = VisionTradeColors.brandCyan,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 3.sp
                    )
                }
            }

            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(
                    tween(
                        durationMillis = 1100,
                        delayMillis = 200
                    )
                ) + scaleIn(
                    initialScale = 0.92f,
                    animationSpec = tween(
                        durationMillis = 1100,
                        delayMillis = 200,
                        easing = FastOutSlowInEasing
                    )
                )
            ) {
                VisionTrade3DMarket(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(
                            RoundedCornerShape(30.dp)
                        )
                )
            }

            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(
                    tween(
                        durationMillis = 900,
                        delayMillis = 600
                    )
                ) + slideInVertically(
                    initialOffsetY = { 55 },
                    animationSpec = tween(
                        durationMillis = 900,
                        delayMillis = 600,
                        easing = FastOutSlowInEasing
                    )
                )
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {

                    Text(
                        text = "SEE THE MARKET.",
                        color = VisionTradeColors.textPrimary,
                        fontSize = 23.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )

                    Text(
                        text = "FOLLOW THE STRATEGY.",
                        color = VisionTradeColors.brandGreen,
                        fontSize = 23.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )

                    Spacer(
                        modifier = Modifier.height(9.dp)
                    )

                    Text(
                        text = "Analyze market structure, technical signals, trade setups and risk in one intelligent workspace.",
                        color = VisionTradeColors.textSecondary,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        textAlign = TextAlign.Center
                    )

                    Spacer(
                        modifier = Modifier.height(19.dp)
                    )

                    Button(
                        onClick = {
                            if (!entering) {
                                entering = true
                                onGetStarted()
                            }
                        },
                        enabled = !entering,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .alpha(
                                if (entering) 0.75f else 1f
                            ),
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor =
                                VisionTradeColors.brandBlue,
                            contentColor = Color.White
                        )
                    ) {
                        Text(
                            text = "ENTER VISIONTRADE",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp
                        )
                    }

                    Spacer(
                        modifier = Modifier.height(8.dp)
                    )

                    Text(
                        text = "INTELLIGENCE • DISCIPLINE • RISK",
                        color = VisionTradeColors.textTertiary,
                        fontSize = 8.sp,
                        letterSpacing = 1.5.sp
                    )
                }
            }
        }
    }
}
