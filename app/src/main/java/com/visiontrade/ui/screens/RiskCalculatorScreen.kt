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
import com.visiontrade.domain.risk.RiskCalculationInput
import com.visiontrade.domain.risk.RiskCalculationResult
import com.visiontrade.ui.components.*
import com.visiontrade.ui.theme.VisionTradeColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RiskCalculatorScreen(
    onBackClick: () -> Unit
) {
    var accountBalance by remember { mutableStateOf("") }
    var riskPercent by remember { mutableStateOf("") }
    var entryPrice by remember { mutableStateOf("") }
    var stopLoss by remember { mutableStateOf("") }
    var takeProfit by remember { mutableStateOf("") }
    var symbol by remember { mutableStateOf("EURUSD") }
    var result by remember { mutableStateOf<RiskCalculationResult?>(null) }
    var isCalculating by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VisionTradeColors.backgroundDark)
    ) {
        // Header
        TopAppBar(
            title = { Text("Risk Calculator") },
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
            // Inputs Section
            SectionCard(title = "Position Details") {
                TextInputField(
                    label = "Account Balance ($)",
                    value = accountBalance,
                    onValueChange = { accountBalance = it },
                    keyboardType = KeyboardType.Number
                )
                Spacer(modifier = Modifier.height(8.dp))
                TextInputField(
                    label = "Risk % per Trade",
                    value = riskPercent,
                    onValueChange = { riskPercent = it },
                    keyboardType = KeyboardType.Number
                )
                Spacer(modifier = Modifier.height(8.dp))
                TextInputField(
                    label = "Symbol",
                    value = symbol,
                    onValueChange = { symbol = it }
                )
            }

            SectionCard(title = "Price Levels") {
                TextInputField(
                    label = "Entry Price",
                    value = entryPrice,
                    onValueChange = { entryPrice = it },
                    keyboardType = KeyboardType.Number
                )
                Spacer(modifier = Modifier.height(8.dp))
                TextInputField(
                    label = "Stop Loss Price",
                    value = stopLoss,
                    onValueChange = { stopLoss = it },
                    keyboardType = KeyboardType.Number
                )
                Spacer(modifier = Modifier.height(8.dp))
                TextInputField(
                    label = "Take Profit Price",
                    value = takeProfit,
                    onValueChange = { takeProfit = it },
                    keyboardType = KeyboardType.Number
                )
            }

            // Calculate Button
            PrimaryButton(
                text = "Calculate",
                onClick = {
                    if (validateInputs(accountBalance, riskPercent, entryPrice, stopLoss, takeProfit)) {
                        isCalculating = true
                        // Simulate calculation
                        try {
                            val input = RiskCalculationInput(
                                accountBalance = accountBalance.toDouble(),
                                riskPercentage = riskPercent.toDouble(),
                                entryPrice = entryPrice.toDouble(),
                                stopLossPrice = stopLoss.toDouble(),
                                takeProfitPrice = takeProfit.toDouble(),
                                symbol = symbol
                            )
                            result = null // In real app, use RiskCalculator
                        } catch (e: Exception) {
                            // Handle error
                        }
                        isCalculating = false
                    }
                },
                isLoading = isCalculating
            )

            // Results
            if (result != null) {
                SectionCard(title = "Risk Analysis") {
                    DataRow(
                        label = "Risk Amount",
                        value = "$%.2f".format(result!!.riskAmount)
                    )
                    DataRow(
                        label = "Position Size",
                        value = "%.4f".format(result!!.positionSize)
                    )
                    DataRow(
                        label = "Risk/Reward Ratio",
                        value = "1:%.2f".format(result!!.riskRewardRatio),
                        color = if (result!!.riskRewardRatio >= 1.5) VisionTradeColors.signalBuy else VisionTradeColors.signalWait
                    )
                    DataRow(
                        label = "Potential Profit",
                        value = "$%.2f".format(result!!.potentialProfit),
                        color = VisionTradeColors.signalBuy
                    )
                    DataRow(
                        label = "Potential Loss",
                        value = "$%.2f".format(result!!.potentialLoss),
                        color = VisionTradeColors.signalSell
                    )
                }
            }
        }
    }
}

private fun validateInputs(
    accountBalance: String,
    riskPercent: String,
    entryPrice: String,
    stopLoss: String,
    takeProfit: String
): Boolean {
    return accountBalance.isNotEmpty() &&
            riskPercent.isNotEmpty() &&
            entryPrice.isNotEmpty() &&
            stopLoss.isNotEmpty() &&
            takeProfit.isNotEmpty() &&
            try {
                accountBalance.toDouble() > 0 &&
                riskPercent.toDouble() > 0 &&
                entryPrice.toDouble() > 0 &&
                stopLoss.toDouble() > 0 &&
                takeProfit.toDouble() > 0
            } catch (e: Exception) {
                false
            }
}
