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
    private var historyState by mutableStateOf<List<AnalysisHistoryEntry>>(emptyList())

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

                        onRiskCalculator = {
                            currentScreen = "risk"
                        },

                        onFlippingSimulator = {
                            currentScreen = "simulator"
                        },

                        onHistory = {
                            historyState = analysisViewModel.historyState.value
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

                            analysisViewModel.analyzeChart(analysis)

                            currentScreen = "result"
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

                            analysisViewModel.analyzeChart(analysis)

                            currentScreen = "result"
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

                            analysisViewModel.analyzeChart(analysis)

                            currentScreen = "result"
                        },

                        onBackClick = {
                            currentScreen = "main"
                        }
                    )

                    // =========================================
                    // RESULT
                    // =========================================

                    "result" -> {

                        val signal = analysisViewModel.signalState.value

                        if (signal != null) {

                            AnalysisResultScreen(
                                signal = signal,

                                onBackClick = {
                                    currentScreen = "main"
                                }
                            )

                        } else {

                            MainScreen(
                                onAnalyzeScreenshot = {
                                    currentScreen = "screenshot"
                                },

                                onScanCamera = {
                                    currentScreen = "camera"
                                },

                                onManualData = {
                                    currentScreen = "manual"
                                },

                                onRiskCalculator = {
                                    currentScreen = "risk"
                                },

                                onFlippingSimulator = {
                                    currentScreen = "simulator"
                                },

                                onHistory = {
                                    historyState =
                                        analysisViewModel.historyState.value
                                    currentScreen = "history"
                                },

                                onSettings = {
                                    currentScreen = "settings"
                                }
                            )
                        }
                    }

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
                        onBackClick = {
                            currentScreen = "main"
                        }
                    )

                    // =========================================
                    // HISTORY
                    // =========================================

                    "history" -> {

                        historyState =
                            analysisViewModel.historyState.value

                        HistoryScreen(

                            history = historyState,

                            onBackClick = {
                                currentScreen = "main"
                            },

                            onDeleteEntry = { entry ->

                                analysisViewModel.deleteHistoryEntry(entry)

                                historyState =
                                    analysisViewModel.historyState.value
                            },

                            onClearAll = {

                                analysisViewModel.clearHistory()

                                historyState = emptyList()
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

                            historyState = emptyList()
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

                        onRiskCalculator = {
                            currentScreen = "risk"
                        },

                        onFlippingSimulator = {
                            currentScreen = "simulator"
                        },

                        onHistory = {
                            historyState =
                                analysisViewModel.historyState.value
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
