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
import androidx.compose.ui.unit.sp
import com.visiontrade.domain.risk.SimulationInput
import com.visiontrade.domain.risk.SimulationResult
import com.visiontrade.ui.components.*
import com.visiontrade.ui.theme.VisionTradeColors
import com.visiontrade.ui.viewmodel.RiskViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimulatorScreen(
    riskViewModel: RiskViewModel,
    onBackClick: () -> Unit
) {
    var startBalance by remember { mutableStateOf("10000") }
    var riskPercent by remember { mutableStateOf("2") }
    var winRate by remember { mutableStateOf("55") }
    var rewardRatio by remember { mutableStateOf("1.5") }
    var numTrades by remember { mutableStateOf("100") }
    var compounding by remember { mutableStateOf(true) }
    val result by riskViewModel.simulationResult.collectAsState()
    val simulationError by riskViewModel.simulationError.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VisionTradeColors.backgroundDark)
    ) {
        // Header
        TopAppBar(
            title = { Text("Account Growth Simulator") },
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
            // Warning Banner
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = VisionTradeColors.signalWait.copy(alpha = 0.2f)
                ),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp)
                ) {
                    Text(
                        "⚠ Hypothetical Simulation",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = VisionTradeColors.signalWait
                        )
                    )
                    Text(
                        "Results are hypothetical and do not guarantee actual trading performance.",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = VisionTradeColors.textSecondary
                        ),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            SectionCard(title = "Simulation Parameters") {
                TextInputField(
                    label = "Starting Balance ($)",
                    value = startBalance,
                    onValueChange = { startBalance = it },
                    keyboardType = KeyboardType.Number
                )
                Spacer(modifier = Modifier.height(8.dp))
                TextInputField(
                    label = "Risk per Trade (%)",
                    value = riskPercent,
                    onValueChange = { riskPercent = it },
                    keyboardType = KeyboardType.Number
                )
                Spacer(modifier = Modifier.height(8.dp))
                TextInputField(
                    label = "Win Rate (%)",
                    value = winRate,
                    onValueChange = { winRate = it },
                    keyboardType = KeyboardType.Number
                )
                Spacer(modifier = Modifier.height(8.dp))
                TextInputField(
                    label = "Reward Ratio (R:R)",
                    value = rewardRatio,
                    onValueChange = { rewardRatio = it },
                    keyboardType = KeyboardType.Number
                )
                Spacer(modifier = Modifier.height(8.dp))
                TextInputField(
                    label = "Number of Trades",
                    value = numTrades,
                    onValueChange = { numTrades = it },
                    keyboardType = KeyboardType.Number
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "Enable Compounding",
                        style = MaterialTheme.typography.bodyMedium,
                        color = VisionTradeColors.textPrimary
                    )
                    Checkbox(
                        checked = compounding,
                        onCheckedChange = { compounding = it },
                        colors = CheckboxDefaults.colors(
                            checkedColor = VisionTradeColors.brandGreen
                        )
                    )
                }
            }

            // Simulate Button
            PrimaryButton(
                text = "Run Simulation",
                onClick = {
                    if (validateSimInputs(startBalance, riskPercent, winRate, rewardRatio, numTrades)) {
                        riskViewModel.runSimulation(
                            SimulationInput(
                                startingBalance = startBalance.toDouble(),
                                riskPercentage = riskPercent.toDouble(),
                                winRate = winRate.toDouble() / 100.0,
                                rewardRatio = rewardRatio.toDouble(),
                                numberOfTrades = numTrades.toInt(),
                                compounding = compounding
                            )
                        )
                    }
                },
                isLoading = false
            )

            simulationError?.let { error ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = VisionTradeColors.signalSell.copy(alpha = 0.15f)
                    ),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = error,
                        color = VisionTradeColors.signalSell,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            // Results
            if (result != null) {
                SectionCard(title = "Simulation Results") {
                    DataRow(
                        label = "Starting Balance",
                        value = "$%.2f".format(result!!.startingBalance)
                    )
                    DataRow(
                        label = "Final Balance",
                        value = "$%.2f".format(result!!.finalBalance),
                        color = if (result!!.finalBalance > result!!.startingBalance) 
                            VisionTradeColors.signalBuy else VisionTradeColors.signalSell
                    )
                    DataRow(
                        label = "Total Profit/Loss",
                        value = "$%.2f".format(result!!.totalProfit),
                        color = if (result!!.totalProfit > 0) 
                            VisionTradeColors.signalBuy else VisionTradeColors.signalSell
                    )
                    DataRow(
                        label = "Return %",
                        value = "%.2f%%".format(result!!.returnPercentage),
                        color = if (result!!.returnPercentage > 0) 
                            VisionTradeColors.signalBuy else VisionTradeColors.signalSell
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    DataRow(
                        label = "Winning Trades",
                        value = result!!.winCount.toString()
                    )
                    DataRow(
                        label = "Losing Trades",
                        value = result!!.lossCount.toString()
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

private fun validateSimInputs(
    startBalance: String,
    riskPercent: String,
    winRate: String,
    rewardRatio: String,
    numTrades: String
): Boolean {
    return startBalance.isNotEmpty() &&
            riskPercent.isNotEmpty() &&
            winRate.isNotEmpty() &&
            rewardRatio.isNotEmpty() &&
            numTrades.isNotEmpty() &&
            try {
                val balance = startBalance.toDouble()
                val risk = riskPercent.toDouble()
                val wr = winRate.toDouble()
                val rr = rewardRatio.toDouble()
                val trades = numTrades.toInt()
                balance > 0 && risk > 0 && risk <= 100 &&
                wr >= 0 && wr <= 100 && rr > 0 && trades > 0
            } catch (e: Exception) {
                false
            }
}
