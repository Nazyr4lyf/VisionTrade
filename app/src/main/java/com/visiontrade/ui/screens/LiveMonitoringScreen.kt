package com.visiontrade.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

private const val SNAPSHOT_URL =
    "https://jjcpaasizhczyqnozblo.supabase.co/rest/v1/visiontrade_monitor_snapshot?id=eq.latest&select=*"
private const val SUPABASE_PUBLISHABLE_KEY =
    "sb_publishable_CvgzR70r_pErX8Ue0KeAXQ_TJHeX5UJ"

private data class MonitorSignal(
    val symbol: String,
    val marketType: String,
    val action: String,
    val referencePrice: String,
    val stopLoss: String,
    val takeProfit: String,
    val riskAccepted: Boolean,
    val paperApproved: Boolean
)

private data class MonitorSnapshot(
    val workerRunning: Boolean,
    val lastScanAt: String,
    val lastScanPairs: Int,
    val actionableSignals: Int,
    val updatedAt: String,
    val signals: List<MonitorSignal>
)

private suspend fun loadMonitorSnapshot(): MonitorSnapshot? = withContext(Dispatchers.IO) {
    val connection = (URL(SNAPSHOT_URL).openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        connectTimeout = 10_000
        readTimeout = 10_000
        setRequestProperty("apikey", SUPABASE_PUBLISHABLE_KEY)
        setRequestProperty("Authorization", "Bearer $SUPABASE_PUBLISHABLE_KEY")
        setRequestProperty("Accept", "application/json")
    }
    try {
        if (connection.responseCode !in 200..299) {
            throw IllegalStateException("Monitoring service returned HTTP ${connection.responseCode}")
        }
        val body = connection.inputStream.bufferedReader().use { it.readText() }
        val rows = JsonParser.parseString(body).asJsonArray
        if (rows.size() == 0) return@withContext null
        val row = rows[0].asJsonObject
        val signals = row.getAsJsonArray("recent_signals")?.map { element ->
            val item = element.asJsonObject
            MonitorSignal(
                symbol = item.get("symbol")?.asString ?: "UNKNOWN",
                marketType = item.get("marketType")?.asString ?: "MARKET",
                action = item.get("action")?.asString ?: "NO_TRADE",
                referencePrice = item.get("referencePrice")?.toString() ?: "—",
                stopLoss = item.get("stopLossPrice")?.toString() ?: "—",
                takeProfit = item.get("takeProfitPrice")?.toString() ?: "—",
                riskAccepted = item.get("riskAccepted")?.asBoolean ?: false,
                paperApproved = item.get("paperApproved")?.asBoolean ?: false
            )
        } ?: emptyList()
        MonitorSnapshot(
            workerRunning = row.get("worker_running")?.asBoolean ?: false,
            lastScanAt = row.get("last_scan_at")?.asString ?: "Not scanned yet",
            lastScanPairs = row.get("last_scan_pairs")?.asInt ?: 0,
            actionableSignals = row.get("actionable_signals")?.asInt ?: 0,
            updatedAt = row.get("updated_at")?.asString ?: "",
            signals = signals
        )
    } finally {
        connection.disconnect()
    }
}

@Composable
fun LiveMonitoringScreen(onBackClick: () -> Unit) {
    var snapshot by remember { mutableStateOf<MonitorSnapshot?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        while (true) {
            try {
                snapshot = loadMonitorSnapshot()
                error = null
            } catch (exception: Exception) {
                error = exception.message ?: "Unable to reach monitoring service"
            } finally {
                loading = false
            }
            delay(15_000)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(18.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("LIVE MONITORING", style = MaterialTheme.typography.titleLarge)
            Button(onClick = onBackClick) { Text("Back") }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "Market data and strategy signals only. Exchange orders are disabled.",
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(Modifier.height(16.dp))

        when {
            loading && snapshot == null -> CircularProgressIndicator()
            error != null && snapshot == null -> {
                Text("Connection unavailable", color = MaterialTheme.colorScheme.error)
                Text(error ?: "")
                Text("The worker must be deployed and connected to the VisionTrade Supabase project.")
            }
            snapshot == null -> {
                Text("Waiting for the monitoring worker", style = MaterialTheme.typography.titleMedium)
                Text("The app is connected to the status database, but the worker has not published its first scan yet.")
            }
            else -> {
                val current = snapshot!!
                val statusColor = if (current.workerRunning) Color(0xFF19B978) else MaterialTheme.colorScheme.error
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text(
                            if (current.workerRunning) "WORKER RUNNING" else "WORKER STOPPED",
                            color = statusColor,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text("Pairs scanned: ${current.lastScanPairs}")
                        Text("Actionable signals: ${current.actionableSignals}")
                        Text("Last scan: ${current.lastScanAt}")
                        Text("Snapshot updated: ${current.updatedAt}")
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text("RECENT STRATEGY SIGNALS", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                if (current.signals.isEmpty()) {
                    Text("No actionable signals in the latest scan.")
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(current.signals) { signal ->
                            Card {
                                Column(Modifier.fillMaxWidth().padding(14.dp)) {
                                    Text("${signal.symbol} • ${signal.action}", style = MaterialTheme.typography.titleMedium)
                                    Text("${signal.marketType} • Reference: ${signal.referencePrice}")
                                    Text("Stop: ${signal.stopLoss} • Target: ${signal.takeProfit}")
                                    Text("Risk: ${if (signal.riskAccepted) "Accepted" else "Rejected"}")
                                    Text("Paper approval: ${if (signal.paperApproved) "Approved" else "Not approved"}")
                                }
                            }
                        }
                    }
                }
            }
        }
        if (error != null && snapshot != null) {
            Spacer(Modifier.height(8.dp))
            Text("Refresh issue: ${error}", color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(8.dp))
        Text("Auto-refresh: every 15 seconds", style = MaterialTheme.typography.labelSmall)
    }
}
