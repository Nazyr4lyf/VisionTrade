package com.visiontrade.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.visiontrade.domain.models.TradingSignal
import com.visiontrade.ui.components.*
import com.visiontrade.ui.theme.VisionTradeColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalysisResultScreen(
    signal: TradingSignal,
    onBackClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VisionTradeColors.backgroundDark)
    ) {
        // Header
        TopAppBar(
            title = { Text("Analysis Result") },
            navigationIcon = {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = VisionTradeColors.textPrimary
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = VisionTradeColors.backgroundDarker,
                titleContentColor = VisionTradeColors.textPrimary
            )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Signal Badge
            SignalBadge(
                signal = signal.signal.name,
                confidence = signal.confidence.name,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )

            // Trend
            SectionCard(title = "Market Trend") {
                DataRow(
                    label = "Trend",
                    value = signal.trend.name,
                    color = when (signal.trend.name) {
                        "BULLISH" -> VisionTradeColors.signalBuy
                        "BEARISH" -> VisionTradeColors.signalSell
                        else -> VisionTradeColors.signalWait
                    }
                )
                DataRow(
                    label = "Confidence",
                    value = "${signal.conditionsMet}/${signal.totalConditions} conditions met"
                )
            }

            // Price Zones
            if (signal.entryZone != null || signal.support != null || signal.resistance != null) {
                SectionCard(title = "Price Levels") {
                    signal.entryZone?.let {
                        DataRow(
                            label = "Entry Zone",
                            value = "%.4f - %.4f".format(it.low, it.high)
                        )
                    }
                    signal.support?.let {
                        DataRow(
                            label = "Support",
                            value = "%.4f - %.4f".format(it.low, it.high),
                            color = VisionTradeColors.signalBuy
                        )
                    }
                    signal.resistance?.let {
                        DataRow(
                            label = "Resistance",
                            value = "%.4f - %.4f".format(it.low, it.high),
                            color = VisionTradeColors.signalSell
                        )
                    }
                }
            }

            // Reasons
            if (signal.reasons.isNotEmpty()) {
                SectionCard(title = "Why ${signal.signal.name}?") {
                    ReasonsList(signal.reasons)
                }
            }

            // Details
            if (signal.analysisDetails.isNotEmpty()) {
                SectionCard(title = "Details") {
                    Text(
                        text = signal.analysisDetails,
                        style = MaterialTheme.typography.bodySmall,
                        color = VisionTradeColors.textSecondary
                    )
                }
            }

            // Trade Setup
signal.tradeSetup?.let { setup ->
    SectionCard(title = "Trade Setup") {

        DataRow(
            label = "Direction",
            value = setup.direction.name,
            color = when (setup.direction.name) {
                "BUY" -> VisionTradeColors.signalBuy
                "SELL" -> VisionTradeColors.signalSell
                else -> VisionTradeColors.signalWait
            }
        )

        DataRow(
            label = "Entry Zone",
            value = "%.4f - %.4f".format(
                setup.entryZone.low,
                setup.entryZone.high
            )
        )

        DataRow(
            label = "Stop Loss",
            value = "%.4f".format(setup.stopLoss),
            color = VisionTradeColors.signalSell
        )

        DataRow(
            label = "Take Profit",
            value = "%.4f".format(setup.takeProfit),
            color = VisionTradeColors.signalBuy
        )

        DataRow(
            label = "Risk / Reward",
            value = "1 : %.2f".format(setup.riskRewardRatio)
        )

        DataRow(
            label = "Invalidation",
            value = "%.4f".format(setup.invalidationLevel)
        )

        DataRow(
            label = "Setup Status",
            value = if (setup.setupValid) "VALID" else "INVALID",
            color = if (setup.setupValid) {
                VisionTradeColors.signalBuy
            } else {
                VisionTradeColors.signalSell
            }
        )

        if (setup.reason.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = setup.reason,
                style = MaterialTheme.typography.bodySmall,
                color = VisionTradeColors.textSecondary
            )
        }
    }
}

Spacer(modifier = Modifier.height(8.dp))
        }
    }
}
