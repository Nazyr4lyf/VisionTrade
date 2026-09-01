package com.visiontrade.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.visiontrade.ui.components.DataRow
import com.visiontrade.ui.components.PrimaryButton
import com.visiontrade.ui.components.SectionCard
import com.visiontrade.ui.theme.VisionTradeColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBackClick: () -> Unit,
    onClearHistory: () -> Unit
) {
    var tolerancePercent by remember { mutableStateOf("0.2") }
    var lookbackCandles by remember { mutableStateOf("50") }
    var showAbout by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VisionTradeColors.backgroundDark)
    ) {
        // Header
        TopAppBar(
            title = { Text("Settings") },
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
            // Strategy Settings
            SectionCard(title = "Strategy Configuration") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Support/Resistance Tolerance (%)",
                                style = MaterialTheme.typography.bodySmall,
                                color = VisionTradeColors.textSecondary
                            )
                            Text(
                                tolerancePercent,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = VisionTradeColors.brandGreen
                            )
                        }
                        Slider(
                            value = tolerancePercent.toFloatOrNull() ?: 0.2f,
                            onValueChange = { tolerancePercent = "%.2f".format(it) },
                            valueRange = 0.1f..1.0f,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            "Price is considered near support/resistance within this percentage",
                            style = MaterialTheme.typography.labelSmall,
                            color = VisionTradeColors.textTertiary,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }

                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Lookback Period (Candles)",
                                style = MaterialTheme.typography.bodySmall,
                                color = VisionTradeColors.textSecondary
                            )
                            Text(
                                lookbackCandles,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = VisionTradeColors.brandGreen
                            )
                        }
                        Slider(
                            value = lookbackCandles.toFloatOrNull() ?: 50f,
                            onValueChange = { lookbackCandles = it.toInt().toString() },
                            valueRange = 20f..100f,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            "Number of recent candles to analyze for support/resistance",
                            style = MaterialTheme.typography.labelSmall,
                            color = VisionTradeColors.textTertiary,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }

            // Data Management
            SectionCard(title = "Data Management") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "⚠ Warning",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = VisionTradeColors.signalWait
                    )
                    Text(
                        "Clearing history cannot be undone.",
                        style = MaterialTheme.typography.bodySmall,
                        color = VisionTradeColors.textSecondary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Button(
                        onClick = { showClearConfirm = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = VisionTradeColors.signalSell.copy(alpha = 0.2f),
                            contentColor = VisionTradeColors.signalSell
                        ),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            "Clear All History",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }

            // About Section
            SectionCard(title = "About") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    DataRow(
                        label = "App Name",
                        value = "VisionTrade AI Assistant"
                    )
                    DataRow(
                        label = "Version",
                        value = "1.0.0"
                    )
                    DataRow(
                        label = "Build",
                        value = "1"
                    )

                    Divider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = VisionTradeColors.border
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "See the Market. Follow the Strategy.",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = VisionTradeColors.brandGreen
                        )

                        Text(
                            "VisionTrade is a technical analysis tool for personal Forex trading education and experimentation.",
                            style = MaterialTheme.typography.bodySmall,
                            color = VisionTradeColors.textSecondary
                        )

                        Divider(
                            modifier = Modifier.padding(vertical = 8.dp),
                            color = VisionTradeColors.border
                        )

                        Text(
                            "Disclaimer",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = VisionTradeColors.signalWait
                        )

                        Text(
                            "This application is for educational purposes only. It does not guarantee trading results and does not automatically execute trades. Trading is risky. Only trade with money you can afford to lose.",
                            style = MaterialTheme.typography.bodySmall,
                            color = VisionTradeColors.textSecondary
                        )

                        Text(
                            "All analysis is performed locally on your device. No data is transmitted externally.",
                            style = MaterialTheme.typography.bodySmall,
                            color = VisionTradeColors.textSecondary,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    // Clear History Confirmation Dialog
    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = {
                Text(
                    "Clear History?",
                    color = VisionTradeColors.textPrimary
                )
            },
            text = {
                Text(
                    "Are you sure? This action cannot be undone.",
                    color = VisionTradeColors.textSecondary
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onClearHistory()
                        showClearConfirm = false
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = VisionTradeColors.signalSell
                    )
                ) {
                    Text("Clear")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showClearConfirm = false },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = VisionTradeColors.brandBlue
                    )
                ) {
                    Text("Cancel")
                }
            },
            containerColor = VisionTradeColors.surface,
            tonalElevation = 0.dp
        )
    }
}
