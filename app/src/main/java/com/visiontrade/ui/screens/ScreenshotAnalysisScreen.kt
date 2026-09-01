package com.visiontrade.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.visiontrade.domain.models.ChartAnalysis
import com.visiontrade.domain.vision.VisionChartAnalyzer
import com.visiontrade.ui.theme.VisionTradeColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenshotAnalysisScreen(
    onAnalysisComplete: (ChartAnalysis) -> Unit,
    onBackClick: () -> Unit
) {
    val analyzer = remember { VisionChartAnalyzer() }

    var m15Bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var h1Bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var h4Bitmap by remember { mutableStateOf<Bitmap?>(null) }

    var activeSlot by remember { mutableStateOf<TimeframeSlot?>(null) }
    var isAnalyzing by remember { mutableStateOf(false) }

    var statusMessage by remember {
        mutableStateOf("Add the M15, H1 and H4 screenshots.")
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->

        val slot = activeSlot
        activeSlot = null

        if (uri == null) {
            return@rememberLauncherForActivityResult
        }

        try {
            val bitmap = analyzerBitmapFromUri(
                uri = uri,
                resolver = null
            )

            if (bitmap == null) {
                statusMessage = "WAIT — the selected image could not be read."
                return@rememberLauncherForActivityResult
            }

            when (slot) {
                TimeframeSlot.M15 -> {
                    m15Bitmap = bitmap
                    statusMessage = "M15 screenshot added."
                }

                TimeframeSlot.H1 -> {
                    h1Bitmap = bitmap
                    statusMessage = "H1 screenshot added."
                }

                TimeframeSlot.H4 -> {
                    h4Bitmap = bitmap
                    statusMessage = "H4 screenshot added."
                }

                null -> Unit
            }
        } catch (_: Exception) {
            statusMessage = "WAIT — image processing failed safely."
        }
    }

    /*
     * We need the Android resolver to decode the selected URI.
     * This second launcher keeps the picker behaviour simple and reliable.
     */
    val context = androidx.compose.ui.platform.LocalContext.current

    val imageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->

        val slot = activeSlot
        activeSlot = null

        if (uri == null) return@rememberLauncherForActivityResult

        try {
            val bitmap = context.contentResolver
                .openInputStream(uri)
                ?.use { input ->
                    BitmapFactory.decodeStream(input)
                }

            if (bitmap == null) {
                statusMessage = "WAIT — selected image could not be decoded."
                return@rememberLauncherForActivityResult
            }

            when (slot) {
                TimeframeSlot.M15 -> {
                    m15Bitmap = bitmap
                    statusMessage = "M15 screenshot added successfully."
                }

                TimeframeSlot.H1 -> {
                    h1Bitmap = bitmap
                    statusMessage = "H1 screenshot added successfully."
                }

                TimeframeSlot.H4 -> {
                    h4Bitmap = bitmap
                    statusMessage = "H4 screenshot added successfully."
                }

                null -> Unit
            }
        } catch (_: Exception) {
            statusMessage = "WAIT — image processing failed safely."
        }
    }

    val selectedCount = listOf(
        m15Bitmap,
        h1Bitmap,
        h4Bitmap
    ).count { it != null }

    val allScreenshotsReady =
        m15Bitmap != null &&
            h1Bitmap != null &&
            h4Bitmap != null

    fun choose(slot: TimeframeSlot) {
        if (isAnalyzing) return

        activeSlot = slot
        imageLauncher.launch("image/*")
    }

    fun remove(slot: TimeframeSlot) {
        if (isAnalyzing) return

        when (slot) {
            TimeframeSlot.M15 -> m15Bitmap = null
            TimeframeSlot.H1 -> h1Bitmap = null
            TimeframeSlot.H4 -> h4Bitmap = null
        }

        statusMessage = "${slot.label} screenshot removed."
    }

    fun analyze() {
        if (!allScreenshotsReady) {
            statusMessage = "WAIT — add all three screenshots first."
            return
        }

        if (isAnalyzing) return

        isAnalyzing = true
        statusMessage = "Analyzing M15 + H1 + H4..."

        try {
            val result = analyzer.analyzeThreeTimeframes(
                listOf(
                    m15Bitmap!!,
                    h1Bitmap!!,
                    h4Bitmap!!
                )
            )

            onAnalysisComplete(result)
        } catch (_: Exception) {
            statusMessage = "WAIT — analysis failed safely."
        } finally {
            isAnalyzing = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VisionTradeColors.backgroundDark)
    ) {

        TopAppBar(
            title = {
                Column {
                    Text(
                        text = "Vision Analysis",
                        color = VisionTradeColors.textPrimary,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = "3-Timeframe Chart Scanner",
                        style = MaterialTheme.typography.labelSmall,
                        color = VisionTradeColors.textTertiary
                    )
                }
            },
            navigationIcon = {
                IconButton(
                    onClick = onBackClick,
                    enabled = !isAnalyzing
                ) {
                    Icon(
                        imageVector = Icons.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = VisionTradeColors.textPrimary
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = VisionTradeColors.backgroundDarker
            )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // --------------------------------------------------------
            // HEADER
            // --------------------------------------------------------

            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Chart Scanner",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = VisionTradeColors.textPrimary
                )

                Spacer(modifier = Modifier.height(5.dp))

                Text(
                    text = "Upload one clean exchange screenshot for each timeframe.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = VisionTradeColors.textSecondary
                )
            }

            // --------------------------------------------------------
            // PROGRESS
            // --------------------------------------------------------

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(VisionTradeColors.backgroundDarker)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "Analysis setup",
                        fontWeight = FontWeight.SemiBold,
                        color = VisionTradeColors.textPrimary
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = if (allScreenshotsReady) {
                            "All 3 timeframes are ready."
                        } else {
                            "Add $selectedCount of 3 screenshots."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = VisionTradeColors.textSecondary
                    )
                }

                Text(
                    text = "$selectedCount / 3",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (allScreenshotsReady) {
                        VisionTradeColors.signalBuy
                    } else {
                        VisionTradeColors.textPrimary
                    }
                )
            }

            // --------------------------------------------------------
            // TIMEFRAME CARDS
            // --------------------------------------------------------

            TimeframeCard(
                slot = TimeframeSlot.M15,
                bitmap = m15Bitmap,
                enabled = !isAnalyzing,
                onAdd = { choose(TimeframeSlot.M15) },
                onRemove = { remove(TimeframeSlot.M15) }
            )

            TimeframeCard(
                slot = TimeframeSlot.H1,
                bitmap = h1Bitmap,
                enabled = !isAnalyzing,
                onAdd = { choose(TimeframeSlot.H1) },
                onRemove = { remove(TimeframeSlot.H1) }
            )

            TimeframeCard(
                slot = TimeframeSlot.H4,
                bitmap = h4Bitmap,
                enabled = !isAnalyzing,
                onAdd = { choose(TimeframeSlot.H4) },
                onRemove = { remove(TimeframeSlot.H4) }
            )

            // --------------------------------------------------------
            // ANALYZE BUTTON
            // --------------------------------------------------------

            Button(
                onClick = ::analyze,
                enabled = allScreenshotsReady && !isAnalyzing,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp),
                shape = RoundedCornerShape(17.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = VisionTradeColors.signalBuy,
                    disabledContainerColor = VisionTradeColors.backgroundDarker
                )
            ) {
                if (isAnalyzing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp
                    )

                    Spacer(modifier = Modifier.width(10.dp))

                    Text(
                        text = "ANALYZING...",
                        fontWeight = FontWeight.Bold
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = null
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = if (allScreenshotsReady) {
                            "ANALYZE 3 CHARTS"
                        } else {
                            "ADD 3 SCREENSHOTS TO ANALYZE"
                        },
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // --------------------------------------------------------
            // STATUS
            // --------------------------------------------------------

            Text(
                text = statusMessage,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(VisionTradeColors.backgroundDarker)
                    .padding(14.dp),
                style = MaterialTheme.typography.bodySmall,
                color = if (allScreenshotsReady) {
                    VisionTradeColors.signalBuy
                } else {
                    VisionTradeColors.textSecondary
                }
            )

            // --------------------------------------------------------
            // SAFETY
            // --------------------------------------------------------

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(VisionTradeColors.backgroundDarker)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                Text(
                    text = "Analysis Rules",
                    fontWeight = FontWeight.Bold,
                    color = VisionTradeColors.textPrimary
                )

                RuleRow("M15 = execution timeframe")
                RuleRow("H1 = confirmation timeframe")
                RuleRow("H4 = higher-timeframe confirmation")
                RuleRow("Missing screenshot = no analysis")
                RuleRow("Poor image quality = WAIT")
                RuleRow("No guessed market data")
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun TimeframeCard(
    slot: TimeframeSlot,
    bitmap: Bitmap?,
    enabled: Boolean,
    onAdd: () -> Unit,
    onRemove: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(VisionTradeColors.backgroundDarker)
            .border(
                width = 1.dp,
                color = if (bitmap != null) {
                    VisionTradeColors.signalBuy
                } else {
                    VisionTradeColors.textTertiary.copy(alpha = 0.35f)
                },
                shape = RoundedCornerShape(20.dp)
            )
            .padding(14.dp)
    ) {

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = slot.label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = VisionTradeColors.textPrimary
                )

                Text(
                    text = slot.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = VisionTradeColors.textTertiary
                )
            }

            if (bitmap != null) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = "Added",
                    tint = VisionTradeColors.signalBuy
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (bitmap != null) {

            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "${slot.label} chart screenshot",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(190.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(VisionTradeColors.backgroundDark),
                contentScale = ContentScale.Fit
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {

                Button(
                    onClick = onAdd,
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(13.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = null
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    Text("Replace")
                }

                Button(
                    onClick = onRemove,
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(13.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = VisionTradeColors.backgroundDark
                    )
                ) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = null
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    Text("Remove")
                }
            }

        } else {

            Button(
                onClick = onAdd,
                enabled = enabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = null
                )

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = "ADD ${slot.label} SCREENSHOT",
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun RuleRow(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "•",
            color = VisionTradeColors.signalBuy,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = VisionTradeColors.textSecondary
        )
    }
}

private enum class TimeframeSlot(
    val label: String,
    val description: String
) {
    M15(
        label = "M15",
        description = "15-minute execution chart"
    ),
    H1(
        label = "H1",
        description = "1-hour confirmation chart"
    ),
    H4(
        label = "H4",
        description = "4-hour higher-timeframe chart"
    )
}

/*
 * Kept isolated so image decoding is not mixed into the UI logic.
 * The current screen uses the Android ContentResolver directly.
 */
private fun analyzerBitmapFromUri(
    uri: android.net.Uri,
    resolver: android.content.ContentResolver?
): Bitmap? {
    if (resolver == null) return null

    return resolver
        .openInputStream(uri)
        ?.use { BitmapFactory.decodeStream(it) }
}
