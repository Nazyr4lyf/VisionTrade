package com.visiontrade

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModelProvider

import com.visiontrade.data.database.AnalysisDatabase
import com.visiontrade.data.repository.AnalysisRepository
import com.visiontrade.domain.models.AnalysisHistoryEntry
import com.visiontrade.ui.screens.*
import com.visiontrade.ui.theme.VisionTradeTheme
import com.visiontrade.ui.viewmodel.AnalysisViewModel
import com.visiontrade.ui.viewmodel.AnalysisViewModelFactory
import com.visiontrade.ui.viewmodel.RiskViewModel

class MainActivity : ComponentActivity() {

    private lateinit var analysisViewModel: AnalysisViewModel
    private lateinit var riskViewModel: RiskViewModel

    private var currentScreen by mutableStateOf("welcome")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        // ==============================
        // DATABASE + REPOSITORY
        // ==============================

        val database = AnalysisDatabase.getDatabase(applicationContext)
        val repository = AnalysisRepository(database)

        // ==============================
        // VIEWMODELS
        // ==============================

        val factory = AnalysisViewModelFactory(repository)

        analysisViewModel =
            ViewModelProvider(this, factory)[AnalysisViewModel::class.java]

        riskViewModel =
            ViewModelProvider(this)[RiskViewModel::class.java]

        // Load saved analysis history
        analysisViewModel.loadHistory()

        setContent {
VisionTradeTheme {

                val signal by analysisViewModel.signalState.collectAsState()
            val isAnalysisLoading by analysisViewModel.isLoading.collectAsState()
            val analysisError by analysisViewModel.errorMessage.collectAsState()
                val history by analysisViewModel.historyState.collectAsState()

            when (currentScreen) {
"welcome" -> WelcomeScreen(
    onGetStarted = {
        currentScreen = "main"
    }
)
                    // =========================================
                    // MAIN
                    // =========================================

                    "main" -> MainScreen(
                        onAnalyzeScreenshot = {
                            currentScreen = "screenshot"
                        },

                        onScanCamera = {
                            currentScreen = "camera"
                        },

                        onManualData = {
                            currentScreen = "manual"
                        },

                        onLiveMonitoring = {
                            currentScreen = "live_monitoring"
                        },

                        onRiskCalculator = {
                            currentScreen = "risk"
                        },

                        onFlippingSimulator = {
                            currentScreen = "simulator"
                        },

                        onHistory = {
                            currentScreen = "history"
                        },

                        onSettings = {
                            currentScreen = "settings"
                        }
                    )

                    // =========================================
                    // SCREENSHOT ANALYSIS
                    // =========================================

                    "screenshot" -> ScreenshotAnalysisScreen(

                        onAnalysisComplete = { analysis ->
                            currentScreen = "result"
                            analysisViewModel.analyzeChart(analysis)
                        },

                        onBackClick = {
                            currentScreen = "main"
                        }
                    )

                    // =========================================
                    // CAMERA ANALYSIS
                    // =========================================

                    "camera" -> CameraAnalysisScreen(

                        onAnalysisComplete = { analysis ->
                            currentScreen = "result"
                            analysisViewModel.analyzeChart(analysis)
                        },

                        onBackClick = {
                            currentScreen = "main"
                        }
                    )

                    // =========================================
                    // MANUAL DATA
                    // =========================================

                    "manual" -> ManualDataEntryScreen(

                        onAnalysisComplete = { analysis ->
                            currentScreen = "result"
                            analysisViewModel.analyzeChart(analysis)
                        },

                        onBackClick = {
                            currentScreen = "main"
                        }
                    )

                    // =========================================
                    // RESULT
                    // =========================================

                     "result" -> {
                          if (signal != null) {
                              AnalysisResultScreen(
                                  signal = signal!!,
                                  onBackClick = {
                                      currentScreen = "main"
                                  }
                              )
                          } else if (isAnalysisLoading) {
                              androidx.compose.material3.CircularProgressIndicator()
                          } else if (analysisError != null) {
                              androidx.compose.material3.Text(
                                  text = analysisError ?: "Analysis error"
                              )
                          } else {
                              androidx.compose.material3.CircularProgressIndicator()
                          }
                      }


                    // =========================================
                    // LIVE MARKET MONITORING
                    // =========================================

                    "live_monitoring" -> LiveMonitoringScreen(
                        onBackClick = { currentScreen = "main" }
                    )

                    // =========================================
                    // RISK CALCULATOR
                    // =========================================

                    "risk" -> RiskCalculatorScreen(
                        onBackClick = {
                            currentScreen = "main"
                        }
                    )

                    // =========================================
                    // SIMULATOR
                    // =========================================

                    "simulator" -> SimulatorScreen(
                        riskViewModel = riskViewModel,
                        onBackClick = {
                            currentScreen = "main"
                        }
                    )

                    // =========================================
                    // HISTORY
                    // =========================================

                    "history" -> {

                        HistoryScreen(

                            history = history,

                            onBackClick = {
                                currentScreen = "main"
                            },

                            onDeleteEntry = { entry ->

                                analysisViewModel.deleteHistoryEntry(entry)
                            },

                            onClearAll = {

                                analysisViewModel.clearHistory()
                            }
                        )
                    }

                    // =========================================
                    // SETTINGS
                    // =========================================

                    "settings" -> SettingsScreen(

                        onBackClick = {
                            currentScreen = "main"
                        },

                        onClearHistory = {

                            analysisViewModel.clearHistory()
                        }
                    )

                    // =========================================
                    // FALLBACK
                    // =========================================

                    else -> MainScreen(

                        onAnalyzeScreenshot = {
                            currentScreen = "screenshot"
                        },

                        onScanCamera = {
                            currentScreen = "camera"
                        },

                        onManualData = {
                            currentScreen = "manual"
                        },

                        onLiveMonitoring = {
                            currentScreen = "live_monitoring"
                        },

                        onRiskCalculator = {
                            currentScreen = "risk"
                        },

                        onFlippingSimulator = {
                            currentScreen = "simulator"
                        },

                        onHistory = {
                            currentScreen = "history"
                        },

                        onSettings = {
                            currentScreen = "settings"
                        }
                    )
                }
            }
        }
    }
}
