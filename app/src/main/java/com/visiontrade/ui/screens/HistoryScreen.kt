package com.visiontrade.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.visiontrade.domain.models.AnalysisHistoryEntry
import com.visiontrade.ui.components.SectionCard
import com.visiontrade.ui.theme.VisionTradeColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    history: List<AnalysisHistoryEntry>,
    onBackClick: () -> Unit,
    onDeleteEntry: (AnalysisHistoryEntry) -> Unit,
    onClearAll: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VisionTradeColors.backgroundDark)
    ) {
        // Header
        TopAppBar(
            title = { Text("Analysis History") },
            navigationIcon = {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = VisionTradeColors.textPrimary
                    )
                }
            },
            actions = {
                if (history.isNotEmpty()) {
                    IconButton(onClick = onClearAll) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = "Clear All",
                            tint = VisionTradeColors.signalSell
                        )
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = VisionTradeColors.backgroundDarker,
                titleContentColor = VisionTradeColors.textPrimary
            )
        )

        if (history.isEmpty()) {
            // Empty state
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    "No analysis history yet",
                    style = MaterialTheme.typography.headlineMedium,
                    color = VisionTradeColors.textSecondary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Text(
                    "Analyze charts to build your history",
                    style = MaterialTheme.typography.bodyMedium,
                    color = VisionTradeColors.textTertiary
                )
            }
        } else {
            // History list
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(history.size) { index ->
                    HistoryItemCard(
                        entry = history[index],
                        onDelete = { onDeleteEntry(history[index]) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryItemCard(
    entry: AnalysisHistoryEntry,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { /* Show details */ },
        colors = CardDefaults.cardColors(
            containerColor = VisionTradeColors.surface
        ),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            // Title row with signal and delete button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = entry.symbol,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = VisionTradeColors.textPrimary
                    )
                    Text(
                        text = "${entry.timeframe} • ${entry.signal}",
                        style = MaterialTheme.typography.labelSmall,
                        color = VisionTradeColors.textSecondary
                    )
                }
                
                // Signal badge
                Badge(
                    modifier = Modifier.padding(start = 8.dp),
                    containerColor = when (entry.signal.uppercase()) {
                        "BUY" -> VisionTradeColors.signalBuy
                        "SELL" -> VisionTradeColors.signalSell
                        else -> VisionTradeColors.signalWait
                    }
                ) {
                    Text(
                        entry.signal.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = VisionTradeColors.backgroundDark,
                        modifier = Modifier.padding(4.dp)
                    )
                }
            }

            Divider(
                modifier = Modifier.padding(vertical = 8.dp),
                color = VisionTradeColors.border
            )

            // Details
            Text(
                text = "Confidence: ${entry.confidence}",
                style = MaterialTheme.typography.bodySmall,
                color = VisionTradeColors.textSecondary,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Text(
                text = "Trend: ${entry.trend}",
                style = MaterialTheme.typography.bodySmall,
                color = VisionTradeColors.textSecondary,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // Delete button
            Button(
                onClick = onDelete,
                modifier = Modifier
                    .align(Alignment.End)
                    .height(32.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = VisionTradeColors.signalSell.copy(alpha = 0.2f),
                    contentColor = VisionTradeColors.signalSell
                ),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "Delete",
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
