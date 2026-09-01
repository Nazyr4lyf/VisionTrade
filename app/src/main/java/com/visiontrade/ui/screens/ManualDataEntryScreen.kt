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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.visiontrade.domain.models.*
import com.visiontrade.ui.components.*
import com.visiontrade.ui.theme.VisionTradeColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualDataEntryScreen(
    onAnalysisComplete: (ChartAnalysis) -> Unit,
    onBackClick: () -> Unit
) {
    var symbol by remember { mutableStateOf("EURUSD") }
    var timeframe by remember { mutableStateOf("M15") }
    
    // Most recent candle
    var open1 by remember { mutableStateOf("") }
    var high1 by remember { mutableStateOf("") }
    var low1 by remember { mutableStateOf("") }
    var close1 by remember { mutableStateOf("") }
    
    // Previous candles
    var open2 by remember { mutableStateOf("") }
    var high2 by remember { mutableStateOf("") }
    var low2 by remember { mutableStateOf("") }
    var close2 by remember { mutableStateOf("") }
    
    var open3 by remember { mutableStateOf("") }
    var high3 by remember { mutableStateOf("") }
    var low3 by remember { mutableStateOf("") }
    var close3 by remember { mutableStateOf("") }
    
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isAnalyzing by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VisionTradeColors.backgroundDark)
    ) {
        // Header
        TopAppBar(
            title = { Text("Manual Data Entry") },
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SectionCard(title = "Chart Information") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextInputField(
                        label = "Symbol",
                        value = symbol,
                        onValueChange = { symbol = it },
                        modifier = Modifier.weight(1f)
                    )
                    TextInputField(
                        label = "Timeframe",
                        value = timeframe,
                        onValueChange = { timeframe = it },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            SectionCard(title = "Candle 1 (Most Recent) - Open/High/Low/Close") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TextInputField(
                            label = "O",
                            value = open1,
                            onValueChange = { open1 = it },
                            keyboardType = KeyboardType.Number,
                            modifier = Modifier.weight(1f)
                        )
                        TextInputField(
                            label = "H",
                            value = high1,
                            onValueChange = { high1 = it },
                            keyboardType = KeyboardType.Number,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TextInputField(
                            label = "L",
                            value = low1,
                            onValueChange = { low1 = it },
                            keyboardType = KeyboardType.Number,
                            modifier = Modifier.weight(1f)
                        )
                        TextInputField(
                            label = "C",
                            value = close1,
                            onValueChange = { close1 = it },
                            keyboardType = KeyboardType.Number,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            SectionCard(title = "Candle 2 - Open/High/Low/Close") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TextInputField(
                            label = "O",
                            value = open2,
                            onValueChange = { open2 = it },
                            keyboardType = KeyboardType.Number,
                            modifier = Modifier.weight(1f)
                        )
                        TextInputField(
                            label = "H",
                            value = high2,
                            onValueChange = { high2 = it },
                            keyboardType = KeyboardType.Number,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TextInputField(
                            label = "L",
                            value = low2,
                            onValueChange = { low2 = it },
                            keyboardType = KeyboardType.Number,
                            modifier = Modifier.weight(1f)
                        )
                        TextInputField(
                            label = "C",
                            value = close2,
                            onValueChange = { close2 = it },
                            keyboardType = KeyboardType.Number,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            SectionCard(title = "Candle 3 - Open/High/Low/Close") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TextInputField(
                            label = "O",
                            value = open3,
                            onValueChange = { open3 = it },
                            keyboardType = KeyboardType.Number,
                            modifier = Modifier.weight(1f)
                        )
                        TextInputField(
                            label = "H",
                            value = high3,
                            onValueChange = { high3 = it },
                            keyboardType = KeyboardType.Number,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TextInputField(
                            label = "L",
                            value = low3,
                            onValueChange = { low3 = it },
                            keyboardType = KeyboardType.Number,
                            modifier = Modifier.weight(1f)
                        )
                        TextInputField(
                            label = "C",
                            value = close3,
                            onValueChange = { close3 = it },
                            keyboardType = KeyboardType.Number,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            if (errorMessage != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = VisionTradeColors.signalSell.copy(alpha = 0.2f)
                    ),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Text(
                            "Error",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = VisionTradeColors.signalSell
                            )
                        )
                        Text(
                            errorMessage!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = VisionTradeColors.textSecondary,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }

            PrimaryButton(
                text = "Analyze Chart",
                onClick = {
                    errorMessage = null
                    try {
                        // Parse and validate inputs
                        val o1 = open1.toDoubleOrNull() ?: throw IllegalArgumentException("Invalid Open 1")
                        val h1 = high1.toDoubleOrNull() ?: throw IllegalArgumentException("Invalid High 1")
                        val l1 = low1.toDoubleOrNull() ?: throw IllegalArgumentException("Invalid Low 1")
                        val c1 = close1.toDoubleOrNull() ?: throw IllegalArgumentException("Invalid Close 1")
                        
                        val o2 = open2.toDoubleOrNull() ?: throw IllegalArgumentException("Invalid Open 2")
                        val h2 = high2.toDoubleOrNull() ?: throw IllegalArgumentException("Invalid High 2")
                        val l2 = low2.toDoubleOrNull() ?: throw IllegalArgumentException("Invalid Low 2")
                        val c2 = close2.toDoubleOrNull() ?: throw IllegalArgumentException("Invalid Close 2")
                        
                        val o3 = open3.toDoubleOrNull() ?: throw IllegalArgumentException("Invalid Open 3")
                        val h3 = high3.toDoubleOrNull() ?: throw IllegalArgumentException("Invalid High 3")
                        val l3 = low3.toDoubleOrNull() ?: throw IllegalArgumentException("Invalid Low 3")
                        val c3 = close3.toDoubleOrNull() ?: throw IllegalArgumentException("Invalid Close 3")

                        // Create candles
                        val candles = listOf(
                            Candle(open = o3, high = h3, low = l3, close = c3),
                            Candle(open = o2, high = h2, low = l2, close = c2),
                            Candle(open = o1, high = h1, low = l1, close = c1)
                        )

                        // Manual mode is deliberately conservative: three candles are
                        // useful for pattern inspection, but they are NOT enough to
                        // calculate EMA50/RSI14/ADX14 or 50-candle structure.
                        // Therefore the strategy will safely return WAIT until a
                        // complete dataset is supplied by the vision pipeline.
                        val selectedTimeframe = runCatching {
                            Timeframe.valueOf(timeframe.trim().uppercase())
                        }.getOrElse { Timeframe.M15 }

                        val timeframeData = TimeframeData(
                            timeframe = selectedTimeframe,
                            candles = candles,
                            latestPrice = candles.lastOrNull()?.close
                        )

                        val analysis = ChartAnalysis(
                            symbol = symbol.ifEmpty { "UNKNOWN" },
                            timeframe = selectedTimeframe,
                            currentPrice = candles.lastOrNull()?.close,
                            m15Data = if (selectedTimeframe == Timeframe.M15) timeframeData else TimeframeData(Timeframe.M15),
                            h1Data = if (selectedTimeframe == Timeframe.H1) timeframeData else TimeframeData(Timeframe.H1),
                            h4Data = if (selectedTimeframe == Timeframe.H4) timeframeData else TimeframeData(Timeframe.H4),
                            imageQuality = ImageQuality.GOOD,
                            dataCompleteness = 0.2
                        )
                        isAnalyzing = true
                        onAnalysisComplete(analysis)
                    } catch (e: Exception) {
                        errorMessage = "Error: ${e.message}\n\nPlease check all fields are filled with valid numbers."
                    }
                },
                isLoading = isAnalyzing
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
