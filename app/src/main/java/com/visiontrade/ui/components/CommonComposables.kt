package com.visiontrade.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.visiontrade.ui.theme.*

// ============= SIGNAL DISPLAY =============
@Composable
fun SignalBadge(signal: String, confidence: String, modifier: Modifier = Modifier) {
    val backgroundColor = when (signal.uppercase()) {
        "BUY" -> VisionTradeColors.signalBuy.copy(alpha = 0.2f)
        "SELL" -> VisionTradeColors.signalSell.copy(alpha = 0.2f)
        else -> VisionTradeColors.signalWait.copy(alpha = 0.2f)
    }
    
    val signalColor = when (signal.uppercase()) {
        "BUY" -> VisionTradeColors.signalBuy
        "SELL" -> VisionTradeColors.signalSell
        else -> VisionTradeColors.signalWait
    }

    Box(
        modifier = modifier
            .background(
                color = backgroundColor,
                shape = RoundedCornerShape(12.dp)
            )
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = signal.uppercase(),
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 28.sp,
                    color = signalColor
                )
            )
            Text(
                text = confidence.uppercase(),
                style = MaterialTheme.typography.labelMedium.copy(
                    color = VisionTradeColors.textSecondary
                )
            )
        }
    }
}

// ============= DATA ROW =============
@Composable
fun DataRow(label: String, value: String, color: androidx.compose.ui.graphics.Color = VisionTradeColors.textPrimary) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = VisionTradeColors.textSecondary
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = color
        )
    }
}

// ============= SECTION CARD =============
@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = VisionTradeColors.surface,
                shape = RoundedCornerShape(12.dp)
            )
            .padding(16.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.SemiBold
            ),
            color = VisionTradeColors.textPrimary,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        content()
    }
}

// ============= REASON LIST =============
@Composable
fun ReasonsList(reasons: List<String>) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        reasons.forEach { reason ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "•",
                    color = VisionTradeColors.brandGreen,
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = reason,
                    color = VisionTradeColors.textSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

// ============= PRIMARY BUTTON =============
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = VisionTradeColors.brandBlue,
            contentColor = VisionTradeColors.backgroundDark
        ),
        enabled = enabled && !isLoading,
        shape = RoundedCornerShape(8.dp)
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier
                    .size(20.dp)
                    .padding(end = 8.dp),
                strokeWidth = 2.dp,
                color = VisionTradeColors.backgroundDark
            )
        }
        Text(
            text = if (isLoading) "Processing..." else text,
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = FontWeight.SemiBold
            )
        )
    }
}

// ============= SECONDARY BUTTON =============
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = VisionTradeColors.brandGreen
        ),
        border = BorderStroke(
            width = 1.dp,
            color = VisionTradeColors.brandGreen
        ),
        enabled = enabled,
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = FontWeight.SemiBold
            )
        )
    }
}

// ============= INPUT FIELD =============
@Composable
fun TextInputField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    keyboardType: androidx.compose.ui.text.input.KeyboardType = androidx.compose.ui.text.input.KeyboardType.Text
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = VisionTradeColors.surface,
            unfocusedContainerColor = VisionTradeColors.surface,
            focusedTextColor = VisionTradeColors.textPrimary,
            unfocusedTextColor = VisionTradeColors.textPrimary,
            focusedLabelColor = VisionTradeColors.brandBlue,
            unfocusedLabelColor = VisionTradeColors.textSecondary,
            focusedIndicatorColor = VisionTradeColors.brandBlue,
            unfocusedIndicatorColor = VisionTradeColors.border
        ),
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
            keyboardType = keyboardType
        ),
        textStyle = MaterialTheme.typography.bodyMedium
    )
}

// ============= LOADING INDICATOR =============
@Composable
fun LoadingScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(VisionTradeColors.backgroundDark),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator(
                color = VisionTradeColors.brandBlue,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "Analyzing chart...",
                color = VisionTradeColors.textSecondary,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

// ============= ERROR MESSAGE =============
@Composable
fun ErrorBanner(message: String, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = VisionTradeColors.signalSell.copy(alpha = 0.2f),
                shape = RoundedCornerShape(8.dp)
            )
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = message,
            color = VisionTradeColors.signalSell,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onDismiss) {
            // X icon
            Text("✕", color = VisionTradeColors.signalSell)
        }
    }
}
