package com.visiontrade.ui.screens

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
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

private const val DASHBOARD_RPC_URL =
    "https://jjcpaasizhczyqnozblo.supabase.co/rest/v1/rpc/visiontrade_monitor_dashboard"
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
    val telegramStatus: String,
    val telegramAttempts: Int,
    val candleAt: String,
    val reasons: List<String>
)

private data class ScanDiagnostics(
    val scannedAt: String,
    val discovered: Int,
    val eligible: Int,
    val scanned: Int,
    val succeeded: Int,
    val failed: Int,
    val actionable: Int,
    val stale: Int,
    val rejectionReasons: List<Pair<String, Int>>
)

private data class MonitorDashboard(
    val workerRunning: Boolean,
    val lastScanAt: String,
    val lastScanPairs: Int,
    val actionableSignals: Int,
    val updatedAt: String,
    val telegramConnected: Boolean,
    val diagnostics: ScanDiagnostics?,
    val signals: List<MonitorSignal>
)

private fun JsonObject.string(name: String, fallback: String = "—"): String {
    val value = get(name)
    return if (value == null || value.isJsonNull) fallback else value.asString
}

private fun JsonObject.int(name: String, fallback: Int = 0): Int =
    get(name)?.takeUnless(JsonElement::isJsonNull)?.asInt ?: fallback

private fun JsonObject.bool(name: String, fallback: Boolean = false): Boolean =
    get(name)?.takeUnless(JsonElement::isJsonNull)?.asBoolean ?: fallback

private fun parseDashboard(root: JsonObject): MonitorDashboard {
    val snapshot = root.getAsJsonObject("snapshot") ?: JsonObject()
    val latest = root.getAsJsonObject("latest_scan")
    val diagnostics = latest?.let { scan ->
        val reasons = scan.getAsJsonObject("rejection_reasons")
            ?.entrySet()
            ?.map { it.key to it.value.asInt }
            ?.sortedByDescending { it.second }
            ?: emptyList()
        ScanDiagnostics(
            scannedAt = scan.string("scanned_at"),
            discovered = scan.int("market_pairs_discovered"),
            eligible = scan.int("eligible_pairs"),
            scanned = scan.int("scanned_pairs"),
            succeeded = scan.int("succeeded_pairs"),
            failed = scan.int("failed_pairs"),
            actionable = scan.int("actionable_signals"),
            stale = scan.int("stale_pairs"),
            rejectionReasons = reasons
        )
    }
    val signals = root.getAsJsonArray("signal_history")?.mapNotNull { element ->
        if (!element.isJsonObject) return@mapNotNull null
        val event = element.asJsonObject
        val payload = event.getAsJsonObject("signal_payload") ?: JsonObject()
        val reasons = payload.getAsJsonArray("reasons")
            ?.map { it.asString }
            ?: emptyList()
        MonitorSignal(
            symbol = event.string("symbol", payload.string("symbol", "UNKNOWN")),
            marketType = event.string("market_type", payload.string("marketType", "MARKET")),
            action = event.string("action", payload.string("action", "UNKNOWN")),
            referencePrice = payload.get("referencePrice")?.takeUnless(JsonElement::isJsonNull)?.toString() ?: "—",
            stopLoss = payload.get("stopLossPrice")?.takeUnless(JsonElement::isJsonNull)?.toString() ?: "—",
            takeProfit = payload.get("takeProfitPrice")?.takeUnless(JsonElement::isJsonNull)?.toString() ?: "—",
            riskAccepted = payload.bool("riskAccepted"),
            telegramStatus = event.string("telegram_status", "unknown").uppercase(),
            telegramAttempts = event.int("telegram_attempts"),
            candleAt = event.string("signal_candle_at"),
            reasons = reasons
        )
    } ?: emptyList()

    return MonitorDashboard(
        workerRunning = snapshot.bool("worker_running"),
        lastScanAt = snapshot.string("last_scan_at", "Not scanned yet"),
        lastScanPairs = snapshot.int("last_scan_pairs"),
        actionableSignals = snapshot.int("actionable_signals"),
        updatedAt = snapshot.string("updated_at", ""),
        telegramConnected = !snapshot.string("telegram_connected_at", "").let { it.isBlank() || it == "—" },
        diagnostics = diagnostics,
        signals = signals
    )
}

private suspend fun loadMonitorDashboard(): MonitorDashboard = withContext(Dispatchers.IO) {
    val connection = (URL(DASHBOARD_RPC_URL).openConnection() as HttpURLConnection).apply {
        requestMethod = "POST"
        connectTimeout = 12_000
        readTimeout = 12_000
        doOutput = true
        setRequestProperty("apikey", SUPABASE_PUBLISHABLE_KEY)
        setRequestProperty("Authorization", "Bearer $SUPABASE_PUBLISHABLE_KEY")
        setRequestProperty("Content-Type", "application/json")
        setRequestProperty("Accept", "application/json")
    }
    try {
        connection.outputStream.use { it.write("{}".toByteArray(Charsets.UTF_8)) }
        if (connection.responseCode !in 200..299) {
            val detail = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            throw IllegalStateException("Monitoring service HTTP ${connection.responseCode}: ${detail.take(180)}")
        }
        val body = connection.inputStream.bufferedReader().use { it.readText() }
        parseDashboard(JsonParser.parseString(body).asJsonObject)
    } finally {
        connection.disconnect()
    }
}

@Composable
fun LiveMonitoringScreen(onBackClick: () -> Unit) {
    var dashboard by remember { mutableStateOf<MonitorDashboard?>(null) }
    var loading by remember { mutableStateOf(true) }
    var refreshing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    suspend fun refresh() {
        refreshing = true
        try {
            dashboard = loadMonitorDashboard()
            error = null
        } catch (exception: Exception) {
            error = exception.message ?: "Unable to reach monitoring service"
        } finally {
            loading = false
            refreshing = false
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            refresh()
            delay(15_000)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("LIVE MONITORING", style = MaterialTheme.typography.titleLarge)
                Button(onClick = onBackClick) { Text("Back") }
            }
            Text(
                "Live market data and strategy signals only. Exchange orders are disabled.",
                style = MaterialTheme.typography.bodySmall
            )
        }

        if (loading && dashboard == null) {
            item { CircularProgressIndicator() }
        } else if (dashboard == null) {
            item {
                Card {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text("Monitoring connection unavailable", color = MaterialTheme.colorScheme.error)
                        Text(error ?: "The dashboard could not be loaded.")
                        Button(onClick = { /* next automatic refresh retries */ }) { Text("Retrying automatically") }
                    }
                }
            }
        } else {
            val current = dashboard!!
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(
                            if (current.workerRunning) "WORKER RUNNING" else "WORKER STATUS UNKNOWN / STOPPED",
                            color = if (current.workerRunning) Color(0xFF19B978) else MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text("Pairs in latest scan: ${current.lastScanPairs}")
                        Text("Signals in latest scan: ${current.actionableSignals}")
                        Text("Last scan: ${current.lastScanAt}")
                        Text("Telegram connection: ${if (current.telegramConnected) "Verified" else "Not verified"}")
                        Text("Updated: ${current.updatedAt}")
                    }
                }
            }

            current.diagnostics?.let { scan ->
                item {
                    Text("LATEST SCAN DIAGNOSTICS", style = MaterialTheme.typography.titleMedium)
                    Card {
                        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Discovered: ${scan.discovered} • Eligible: ${scan.eligible}")
                            Text("Scanned: ${scan.scanned} • Successful: ${scan.succeeded}")
                            Text("Failed: ${scan.failed} • Stale candles: ${scan.stale}")
                            Text("Actionable signals: ${scan.actionable}")
                            Text("Scan time: ${scan.scannedAt}", style = MaterialTheme.typography.bodySmall)
                            if (scan.rejectionReasons.isNotEmpty()) {
                                Spacer(Modifier.height(6.dp))
                                Text("WHY COINS WERE REJECTED", style = MaterialTheme.typography.labelLarge)
                                scan.rejectionReasons.forEach { (reason, count) ->
                                    Text("• $count — $reason", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }

            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("SIGNAL HISTORY", style = MaterialTheme.typography.titleMedium)
                    Text("${current.signals.size} records", style = MaterialTheme.typography.labelSmall)
                }
                Text("Signals remain visible after later scans return no setup.", style = MaterialTheme.typography.bodySmall)
            }
            if (current.signals.isEmpty()) {
                item { Text("No confirmed strategy signals have been recorded yet.") }
            } else {
                items(current.signals) { signal ->
                    Card {
                        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("${signal.symbol} • ${signal.action}", style = MaterialTheme.typography.titleMedium)
                            Text("${signal.marketType} • Candle: ${signal.candleAt}")
                            Text("Reference: ${signal.referencePrice} • Stop: ${signal.stopLoss} • Target: ${signal.takeProfit}")
                            Text("Risk validation: ${if (signal.riskAccepted) "Accepted" else "Not approved / unavailable"}")
                            Text("Telegram: ${signal.telegramStatus} • Attempts: ${signal.telegramAttempts}")
                            signal.reasons.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                }
            }
        }

        if (error != null && dashboard != null) {
            item { Text("Refresh issue: ${error}", color = MaterialTheme.colorScheme.error) }
        }
        item {
            Text(
                if (refreshing) "Refreshing…" else "Auto-refresh: every 15 seconds",
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}
