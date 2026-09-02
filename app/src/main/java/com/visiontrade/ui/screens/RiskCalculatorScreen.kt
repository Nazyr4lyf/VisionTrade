package com.visiontrade.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.visiontrade.domain.risk.RiskCalculationInput
import com.visiontrade.domain.risk.RiskCalculationResult
import com.visiontrade.domain.risk.RiskCalculator
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
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isCalculating by remember { mutableStateOf(false) }

    val riskCalculator = remember { RiskCalculator() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VisionTradeColors.backgroundDark)
    ) {
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
            SectionCard(title = "Position Details") {
                TextInputField(
                    label = "Account Balance ($)",
                    value = accountBalance,
                    onValueChange = {
                        accountBalance = it
                        errorMessage = null
                    },
                    keyboardType = KeyboardType.Number
                )

                Spacer(modifier = Modifier.height(8.dp))

                TextInputField(
                    label = "Risk % per Trade",
                    value = riskPercent,
                    onValueChange = {
                        riskPercent = it
                        errorMessage = null
                    },
                    keyboardType = KeyboardType.Number
                )

                Spacer(modifier = Modifier.height(8.dp))

                TextInputField(
                    label = "Symbol",
                    value = symbol,
                    onValueChange = {
                        symbol = it
                        errorMessage = null
                    }
                )
            }

            SectionCard(title = "Price Levels") {
                TextInputField(
                    label = "Entry Price",
                    value = entryPrice,
                    onValueChange = {
                        entryPrice = it
                        errorMessage = null
                    },
                    keyboardType = KeyboardType.Number
                )

                Spacer(modifier = Modifier.height(8.dp))

                TextInputField(
                    label = "Stop Loss Price",
                    value = stopLoss,
                    onValueChange = {
                        stopLoss = it
                        errorMessage = null
                    },
                    keyboardType = KeyboardType.Number
                )

                Spacer(modifier = Modifier.height(8.dp))

                TextInputField(
                    label = "Take Profit Price",
                    value = takeProfit,
                    onValueChange = {
                        takeProfit = it
                        errorMessage = null
                    },
                    keyboardType = KeyboardType.Number
                )
            }

            errorMessage?.let { message ->
                SectionCard(title = "Calculation Error") {
                    Text(
                        text = message,
                        color = VisionTradeColors.signalSell
                    )
                }
            }

            PrimaryButton(
                text = "Calculate",
                onClick = {
                    errorMessage = null
                    result = null

                    if (!validateInputs(
                            accountBalance,
                            riskPercent,
                            entryPrice,
                            stopLoss,
                            takeProfit
                        )
                    ) {
                        errorMessage = "Please enter valid positive values for all fields."
                        return@PrimaryButton
                    }

                    isCalculating = true

                    try {
                        val input = RiskCalculationInput(
                            accountBalance = accountBalance.toDouble(),
                            riskPercentage = riskPercent.toDouble(),
                            entryPrice = entryPrice.toDouble(),
                            stopLossPrice = stopLoss.toDouble(),
                            takeProfitPrice = takeProfit.toDouble(),
                            symbol = symbol.ifBlank { "EURUSD" }
                        )

                        result = riskCalculator.calculateRisk(input)
                    } catch (e: IllegalArgumentException) {
                        errorMessage = e.message ?: "Invalid risk calculation input."
                    } catch (e: Exception) {
                        errorMessage = "Unable to calculate risk. Please check your values."
                    } finally {
                        isCalculating = false
                    }
                },
                isLoading = isCalculating
            )

            result?.let { calculation ->
                SectionCard(title = "Risk Analysis") {
                    DataRow(
                        label = "Risk Amount",
                        value = "$%.2f".format(calculation.riskAmount)
                    )

                    DataRow(
                        label = "Stop Loss",
                        value = "%.2f pips".format(calculation.stopLossPips)
                    )

                    DataRow(
                        label = "Take Profit",
                        value = "%.2f pips".format(calculation.takeProfitPips)
                    )

                    DataRow(
                        label = "Position Size",
                        value = "%.4f".format(calculation.positionSize)
                    )

                    DataRow(
                        label = "Risk/Reward Ratio",
                        value = "1:%.2f".format(calculation.riskRewardRatio),
                        color = if (calculation.riskRewardRatio >= 1.5) {
                            VisionTradeColors.signalBuy
                        } else {
                            VisionTradeColors.signalWait
                        }
                    )

                    DataRow(
                        label = "Potential Profit",
                        value = "$%.2f".format(calculation.potentialProfit),
                        color = VisionTradeColors.signalBuy
                    )

                    DataRow(
                        label = "Potential Loss",
                        value = "$%.2f".format(calculation.potentialLoss),
                        color = VisionTradeColors.signalSell
                    )

                    DataRow(
                        label = "Risk Percentage",
                        value = "%.2f%%".format(calculation.percentageRisk)
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
    return try {
        val balance = accountBalance.toDouble()
        val risk = riskPercent.toDouble()
        val entry = entryPrice.toDouble()
        val sl = stopLoss.toDouble()
        val tp = takeProfit.toDouble()

        balance > 0.0 &&
                risk > 0.0 &&
                risk <= 100.0 &&
                entry > 0.0 &&
                sl > 0.0 &&
                tp > 0.0
    } catch (_: NumberFormatException) {
        false
    }
}
