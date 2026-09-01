package com.visiontrade.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.compose.ui.platform.LocalLifecycleOwner
import com.visiontrade.domain.models.ChartAnalysis
import com.visiontrade.domain.vision.VisionChartAnalyzer
import com.visiontrade.ui.components.PrimaryButton
import com.visiontrade.ui.components.SectionCard
import com.visiontrade.ui.theme.VisionTradeColors
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraAnalysisScreen(
    onAnalysisComplete: (ChartAnalysis) -> Unit,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val analyzer = remember { VisionChartAnalyzer() }
    var permissionGranted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    var capturedFiles by remember { mutableStateOf<List<File>>(emptyList()) }
    var status by remember { mutableStateOf("M15") }
    var isCapturing by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        permissionGranted = it
    }

    LaunchedEffect(permissionGranted, previewView) {
        if (!permissionGranted || previewView == null) return@LaunchedEffect
        val provider = ProcessCameraProvider.getInstance(context).get()
        val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView!!.surfaceProvider) }
        val capture = ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build()
        imageCapture = capture
        try {
            provider.unbindAll()
            provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, capture)
        } catch (e: Exception) {
            message = "Camera initialization failed: ${e.message}"
        }
    }

    Column(Modifier.fillMaxSize().background(VisionTradeColors.backgroundDark)) {
        TopAppBar(
            title = { Text("Scan Chart — $status") },
            navigationIcon = { IconButton(onClick = onBackClick) { Icon(Icons.Filled.ArrowBack, "Back", tint = VisionTradeColors.textPrimary) } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = VisionTradeColors.backgroundDarker, titleContentColor = VisionTradeColors.textPrimary)
        )

        if (!permissionGranted) {
            Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text("Camera permission is required to scan chart screenshots.", color = VisionTradeColors.textSecondary)
                Spacer(Modifier.height(16.dp))
                PrimaryButton("Allow Camera", { permissionLauncher.launch(Manifest.permission.CAMERA) })
            }
        } else {
            Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AndroidView(
                    factory = { PreviewView(it).also { previewView = it } },
                    modifier = Modifier.fillMaxWidth().weight(1f)
                )
                SectionCard(title = "Capture sequence") {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Capture in order: M15 → H1 → H4", color = VisionTradeColors.textPrimary)
                        Text("Keep the entire candle area visible and avoid glare. The analyzer will refuse unclear data.", color = VisionTradeColors.textSecondary, style = MaterialTheme.typography.bodySmall)
                        PrimaryButton(
                            text = if (isCapturing) "Capturing..." else "Capture $status",
                            onClick = {
                                val capture = imageCapture ?: return@PrimaryButton
                                isCapturing = true
                                val file = File(context.cacheDir, "visiontrade_${status}_${System.currentTimeMillis()}.jpg")
                                capture.takePicture(
                                    ImageCapture.OutputFileOptions.Builder(file).build(),
                                    ContextCompat.getMainExecutor(context),
                                    object : ImageCapture.OnImageSavedCallback {
                                        override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                                            val next = capturedFiles + file
                                            capturedFiles = next
                                            isCapturing = false
                                            if (next.size < 3) {
                                                status = listOf("M15", "H1", "H4")[next.size]
                                            } else {
                                                val bitmaps = next.mapNotNull { BitmapFactory.decodeFile(it.absolutePath) }
                                                val result = analyzer.analyzeThreeTimeframes(bitmaps)
                                                next.forEach { it.delete() }
                                                onAnalysisComplete(result)
                                            }
                                        }
                                        override fun onError(exception: ImageCaptureException) {
                                            isCapturing = false
                                            message = "WAIT — capture failed: ${exception.message}"
                                        }
                                    }
                                )
                            },
                            isLoading = isCapturing
                        )
                        message?.let { Text(it, color = VisionTradeColors.signalWait) }
                    }
                }
            }
        }
    }
}
