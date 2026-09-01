# VisionTrade Android - Customization Guide

**Version**: 1.0  
**Date**: August 20, 2026  
**Purpose**: Help developers customize and extend the VisionTrade app

---

## TABLE OF CONTENTS

1. [Quick Start](#quick-start)
2. [Strategy Customization](#strategy-customization)
3. [Add New Indicators](#add-new-indicators)
4. [Modify UI Theme](#modify-ui-theme)
5. [Add New Screens](#add-new-screens)
6. [Broker API Integration](#broker-api-integration)
7. [Database Extensions](#database-extensions)
8. [Vision Pipeline Tuning](#vision-pipeline-tuning)
9. [Advanced Optimization](#advanced-optimization)
10. [Testing Custom Changes](#testing-custom-changes)

---

## Quick Start

### Finding What to Modify

**Strategy Logic**: `app/src/main/java/com/visiontrade/domain/strategy/StrategyEngine.kt`

**Indicators**: `app/src/main/java/com/visiontrade/domain/indicators/IndicatorEngine.kt`

**UI Screens**: `app/src/main/java/com/visiontrade/ui/screens/`

**Database**: `app/src/main/java/com/visiontrade/data/database/AnalysisDatabase.kt`

**Theme/Colors**: `app/src/main/java/com/visiontrade/ui/theme/Theme.kt`

**Resources**: `app/src/main/res/values/`

### File Structure

```
app/src/main/
├── java/com/visiontrade/
│   ├── domain/              ← Business logic (modify here for strategy)
│   │   ├── models/
│   │   ├── indicators/      ← Add indicators here
│   │   ├── strategy/        ← Modify strategy here
│   │   ├── risk/
│   │   └── vision/          ← Modify vision pipeline here
│   ├── data/                ← Database (modify here for persistence)
│   │   ├── database/        ← Room schema
│   │   └── repository/      ← Data access
│   └── ui/                  ← User interface (modify here for UI)
│       ├── screens/         ← Add screens here
│       ├── components/      ← Add components here
│       └── theme/           ← Modify colors here
└── res/
    └── values/              ← Strings, colors, themes
```

---

## Strategy Customization

### Location
`app/src/main/java/com/visiontrade/domain/strategy/StrategyEngine.kt`

### Current BUY Strategy (8 Conditions)

```kotlin
private fun evaluateBuySignal(analysis: ChartAnalysis): Boolean {
    // 1. EMA20 > EMA50 on M15
    // 2. EMA20 > EMA50 on H1
    // 3. EMA20 > EMA50 on H4
    // 4. RSI14 < 70
    // 5. ADX14 > 20
    // 6. Bullish engulfing on M15
    // 7. Price near support
    // 8. Overall bullish trend
    
    val conditions = listOf(
        analysis.m15Timeframe.ema20 > analysis.m15Timeframe.ema50,
        analysis.h1Timeframe.ema20 > analysis.h1Timeframe.ema50,
        analysis.h4Timeframe.ema20 > analysis.h4Timeframe.ema50,
        analysis.m15Timeframe.rsi14.value < 70.0,
        analysis.m15Timeframe.adx14.value > 20.0,
        analysis.m15Timeframe.bullishEngulfing,
        analysis.m15Timeframe.priceNearSupport,
        analysis.overallTrend == Trend.BULLISH
    )
    
    return conditions.all { it }  // ALL must be true
}
```

### Example 1: Modify RSI Threshold

**Original**:
```kotlin
analysis.m15Timeframe.rsi14.value < 70.0
```

**Modified** (more conservative):
```kotlin
analysis.m15Timeframe.rsi14.value < 65.0  // Stricter
```

**Modified** (more aggressive):
```kotlin
analysis.m15Timeframe.rsi14.value < 75.0  // Looser
```

### Example 2: Add Bollinger Bands Condition

```kotlin
// First, add to IndicatorEngine.kt
fun calculateBollingerBands(candles: List<Candle>, period: Int = 20): BollingerBandsValue {
    val sma = calculateSMA(candles.map { it.close }, period)
    val stdDev = calculateStdDev(candles.map { it.close }, period)
    return BollingerBandsValue(
        upper = sma + (2 * stdDev),
        middle = sma,
        lower = sma - (2 * stdDev)
    )
}

// Then add to strategy
private fun evaluateBuySignal(analysis: ChartAnalysis): Boolean {
    val conditions = listOf(
        // ... existing conditions ...
        analysis.m15Timeframe.price > analysis.m15Timeframe.bollingerLower,  // NEW
        // ... more conditions ...
    )
    return conditions.all { it }
}
```

### Example 3: Change from ALL to MAJORITY Logic

```kotlin
// Original: ALL conditions required
return conditions.all { it }

// Modified: 6 out of 8 required
val trueCount = conditions.count { it }
return trueCount >= 6

// Modified: 75% required
return conditions.count { it } >= (conditions.size * 0.75).toInt()
```

### Example 4: Add Time Filtering

```kotlin
private fun evaluateBuySignal(analysis: ChartAnalysis): Boolean {
    // Only allow trades between 9 AM and 5 PM
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    if (hour < 9 || hour > 17) {
        return false  // Outside trading hours
    }
    
    val conditions = listOf(
        // ... existing conditions ...
    )
    return conditions.all { it }
}
```

### Testing Your Changes

```bash
# Modify the strategy
# Edit: app/src/main/java/com/visiontrade/domain/strategy/StrategyEngine.kt

# Update tests
# Edit: app/src/test/java/com/visiontrade/domain/StrategyTests.kt

# Run tests
./test.sh -s
```

---

## Add New Indicators

### Location
`app/src/main/java/com/visiontrade/domain/indicators/IndicatorEngine.kt`

### Current Indicators
- EMA 20/50
- RSI 14
- ADX 14
- Support/Resistance

### Example 1: Add MACD

```kotlin
// Add to IndicatorEngine.kt

data class MACDValue(
    val macd: Double,
    val signal: Double,
    val histogram: Double,
    val status: DataStatus = DataStatus.VALID
)

fun calculateMACD(
    candles: List<Candle>,
    fastPeriod: Int = 12,
    slowPeriod: Int = 26,
    signalPeriod: Int = 9
): MACDValue {
    if (candles.size < slowPeriod) {
        return MACDValue(0.0, 0.0, 0.0, DataStatus.INSUFFICIENT_DATA)
    }
    
    val closes = candles.map { it.close }
    val ema12 = calculateEMA(closes, fastPeriod)
    val ema26 = calculateEMA(closes, slowPeriod)
    
    val macd = ema12 - ema26
    val signal = calculateEMA(listOf(macd), signalPeriod)
    val histogram = macd - signal
    
    return MACDValue(macd, signal, histogram, DataStatus.VALID)
}

// Then add to strategy
private fun evaluateBuySignal(analysis: ChartAnalysis): Boolean {
    val conditions = listOf(
        // ... existing ...
        analysis.m15Timeframe.macd.macd > analysis.m15Timeframe.macd.signal,  // MACD above signal
        // ... more ...
    )
    return conditions.all { it }
}
```

### Example 2: Add Stochastic Oscillator

```kotlin
data class StochasticValue(
    val k: Double,
    val d: Double,
    val status: DataStatus = DataStatus.VALID
)

fun calculateStochastic(
    candles: List<Candle>,
    period: Int = 14,
    smoothK: Int = 3,
    smoothD: Int = 3
): StochasticValue {
    if (candles.size < period) {
        return StochasticValue(0.0, 0.0, DataStatus.INSUFFICIENT_DATA)
    }
    
    val recent = candles.takeLast(period)
    val high = recent.maxOf { it.high }
    val low = recent.minOf { it.low }
    val close = candles.last().close
    
    val k = ((close - low) / (high - low)) * 100.0
    // Smooth K and D lines here
    
    return StochasticValue(k, k, DataStatus.VALID)  // Simplified
}
```

### Example 3: Add Moving Average Convergence

```kotlin
fun calculateMoneyFlowIndex(candles: List<Candle>, period: Int = 14): Double {
    if (candles.size < period) return 0.0
    
    val typicalPrice = candles.map { (it.high + it.low + it.close) / 3 }
    val volume = candles.map { it.volume.toDouble() }
    
    var positiveMF = 0.0
    var negativeMF = 0.0
    
    for (i in 1 until candles.size) {
        val rawMoneyFlow = typicalPrice[i] * volume[i]
        if (typicalPrice[i] > typicalPrice[i - 1]) {
            positiveMF += rawMoneyFlow
        } else {
            negativeMF += rawMoneyFlow
        }
    }
    
    val mfiRatio = if (negativeMF == 0.0) 100.0 else (positiveMF / negativeMF)
    return 100.0 - (100.0 / (1.0 + mfiRatio))
}
```

---

## Modify UI Theme

### Location
`app/src/main/java/com/visiontrade/ui/theme/Theme.kt`  
`app/src/main/res/values/colors.xml`

### Example 1: Change Brand Colors

**colors.xml**:
```xml
<!-- Current -->
<color name="primary_blue">#0066FF</color>
<color name="brand_green">#00FF41</color>

<!-- Modified - More red/orange theme -->
<color name="primary_blue">#FF6B00</color>
<color name="brand_green">#FF9500</color>
```

**Then update Theme.kt**:
```kotlin
private val VisionTradeColors = VisionTradeColorScheme(
    primaryBlue = Color(0xFF0066FF),  // New color
    brandGreen = Color(0xFF00FF41),   // New color
    // ...
)
```

### Example 2: Switch to Light Theme

**Theme.kt**:
```kotlin
// Original: Dark theme
@Composable
fun VisionTradeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,  // Dark
        // ...
    ) {
        content()
    }
}

// Modified: Light theme
val LightColorScheme = lightColorScheme(
    primary = Color(0xFF0066FF),
    secondary = Color(0xFF00FF41),
    background = Color(0xFFFFFFFF),  // White instead of dark
    surface = Color(0xFFF5F5F5),
    // ...
)

@Composable
fun VisionTradeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColorScheme,  // Light
        // ...
    ) {
        content()
    }
}
```

### Example 3: Add Custom Font

```kotlin
// Download font (e.g., Roboto-Bold.ttf)
// Place in: app/src/main/res/font/

@Composable
fun VisionTradeTheme(content: @Composable () -> Unit) {
    val fontFamily = FontFamily(
        Font(R.font.roboto_regular, FontWeight.Normal),
        Font(R.font.roboto_bold, FontWeight.Bold),
    )
    
    MaterialTheme(
        typography = Typography(
            displayLarge = TextStyle(
                fontFamily = fontFamily,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold
            ),
            // ... more styles ...
        ),
        // ...
    ) {
        content()
    }
}
```

### Example 4: Add Dark/Light Toggle

```kotlin
// In Theme.kt
@Composable
fun VisionTradeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),  // NEW parameter
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    
    MaterialTheme(
        colorScheme = colorScheme,
        // ...
    ) {
        content()
    }
}

// Use in app
@Composable
fun App() {
    val isDarkMode = remember { mutableStateOf(true) }
    
    VisionTradeTheme(darkTheme = isDarkMode.value) {
        // Your app content
    }
}
```

---

## Add New Screens

### Location
`app/src/main/java/com/visiontrade/ui/screens/`

### Example 1: Add Settings Screen

```kotlin
// NewSettingsScreen.kt
@Composable
fun AdvancedSettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    var strategyThreshold by remember { mutableStateOf(0.6f) }
    var notificationsEnabled by remember { mutableStateOf(true) }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.VisionTrade.background)
            .padding(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onNavigateBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
            }
            Text("Advanced Settings", style = MaterialTheme.typography.headlineSmall)
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Strategy Threshold
        Text("Strategy Confidence Threshold", style = MaterialTheme.typography.titleMedium)
        Slider(
            value = strategyThreshold,
            onValueChange = { strategyThreshold = it },
            valueRange = 0f..1f,
            steps = 10
        )
        Text("${(strategyThreshold * 100).toInt()}%")
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Notifications
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Enable Notifications", style = MaterialTheme.typography.bodyLarge)
            Switch(
                checked = notificationsEnabled,
                onCheckedChange = { notificationsEnabled = it }
            )
        }
    }
}
```

### Example 2: Add Notification Screen

```kotlin
@Composable
fun NotificationPreferencesScreen() {
    var tradeAlerts by remember { mutableStateOf(true) }
    var priceAlerts by remember { mutableStateOf(false) }
    var newsAlerts by remember { mutableStateOf(true) }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        SectionCard(title = "Trading Alerts") {
            CheckboxItem("New trade signals", tradeAlerts) { tradeAlerts = it }
            CheckboxItem("Risk warnings", true) { /* onChange */ }
            CheckboxItem("Stop loss triggered", true) { /* onChange */ }
        }
        
        SectionCard(title = "Market Alerts") {
            CheckboxItem("Price targets reached", priceAlerts) { priceAlerts = it }
            CheckboxItem("High volatility", false) { /* onChange */ }
            CheckboxItem("Major news", newsAlerts) { newsAlerts = it }
        }
    }
}

@Composable
fun CheckboxItem(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Text(label, modifier = Modifier.weight(1f))
    }
}
```

### Example 3: Add Statistics Dashboard

```kotlin
@Composable
fun StatisticsScreen() {
    val stats = remember {
        mutableStateOf(TradeStatistics(
            totalTrades = 42,
            winningTrades = 28,
            losingTrades = 14,
            averageWin = 150.0,
            averageLoss = 50.0,
            profitFactor = 2.5
        ))
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("Trading Statistics", style = MaterialTheme.typography.headlineSmall)
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Win Rate
        StatCard(
            title = "Win Rate",
            value = "${(stats.value.winningTrades.toDouble() / stats.value.totalTrades * 100).toInt()}%",
            color = Color.Green
        )
        
        StatCard(
            title = "Profit Factor",
            value = "%.2f".format(stats.value.profitFactor),
            color = if (stats.value.profitFactor > 1.5) Color.Green else Color.Yellow
        )
        
        StatCard(
            title = "Total P&L",
            value = "$${(stats.value.totalProfits - stats.value.totalLosses).toInt()}",
            color = if (stats.value.totalProfits > stats.value.totalLosses) Color.Green else Color.Red
        )
    }
}

@Composable
fun StatCard(title: String, value: String, color: Color) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(title)
            Text(value, color = color, fontWeight = FontWeight.Bold)
        }
    }
}
```

### Example 4: Register New Screen in Navigation

**MainActivity.kt**:
```kotlin
@Composable
fun AppNavigation(navController: NavHostController, viewModel: AnalysisViewModel) {
    NavHost(navController = navController, startDestination = "main") {
        composable("main") {
            MainScreen(
                onNavigateScreenshotAnalysis = { navController.navigate("screenshot") },
                onNavigateCameraAnalysis = { navController.navigate("camera") },
                onNavigateAdvancedSettings = { navController.navigate("advanced_settings") },  // NEW
                // ... other navigation ...
            )
        }
        
        // ... other routes ...
        
        // NEW ROUTE
        composable("advanced_settings") {
            AdvancedSettingsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
```

---

## Broker API Integration

### Location
`app/src/main/java/com/visiontrade/data/broker/`  (Create new package)

### Example 1: MetaTrader 5 Integration

```kotlin
// Create: app/src/main/java/com/visiontrade/data/broker/MetaTrader5Client.kt

interface BrokerClient {
    suspend fun connectAccount(credentials: BrokerCredentials): Boolean
    suspend fun getAccountBalance(): Double
    suspend fun placeBuyOrder(symbol: String, volume: Double, price: Double): OrderResult
    suspend fun placeSellOrder(symbol: String, volume: Double, price: Double): OrderResult
    suspend fun getCurrentPrice(symbol: String): Double
    suspend fun closeOrder(orderId: String): Boolean
}

data class BrokerCredentials(
    val accountNumber: String,
    val password: String,
    val serverAddress: String
)

data class OrderResult(
    val orderId: String,
    val status: String,
    val executionPrice: Double,
    val timestamp: Long
)

class MetaTrader5Client : BrokerClient {
    private var isConnected = false
    
    override suspend fun connectAccount(credentials: BrokerCredentials): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                // Connect to MT5 via WebSocket or API
                // This is a placeholder - implement with actual MT5 library
                isConnected = true
                true
            } catch (e: Exception) {
                false
            }
        }
    }
    
    override suspend fun getAccountBalance(): Double {
        if (!isConnected) return 0.0
        // Fetch from MT5 API
        return 10000.0  // Placeholder
    }
    
    override suspend fun placeBuyOrder(
        symbol: String,
        volume: Double,
        price: Double
    ): OrderResult {
        if (!isConnected) {
            throw IllegalStateException("Not connected to broker")
        }
        // Send order to MT5
        return OrderResult(
            orderId = "12345",
            status = "OPEN",
            executionPrice = price,
            timestamp = System.currentTimeMillis()
        )
    }
    
    override suspend fun placeSellOrder(
        symbol: String,
        volume: Double,
        price: Double
    ): OrderResult {
        // Similar to placeBuyOrder
        return OrderResult("", "", 0.0, 0L)
    }
    
    override suspend fun getCurrentPrice(symbol: String): Double {
        // Get current market price from MT5
        return 1.0850  // Placeholder
    }
    
    override suspend fun closeOrder(orderId: String): Boolean {
        // Close order in MT5
        return true
    }
}
```

### Example 2: Alpaca Integration

```kotlin
// Create: app/src/main/java/com/visiontrade/data/broker/AlpacaClient.kt

class AlpacaClient(private val apiKey: String, private val apiSecret: String) : BrokerClient {
    private val baseUrl = "https://api.alpaca.markets"
    
    override suspend fun connectAccount(credentials: BrokerCredentials): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val response = httpClient.get("$baseUrl/v2/account") {
                    header("APCA-API-KEY-ID", apiKey)
                    header("APCA-API-SECRET-KEY", apiSecret)
                }
                response.status.isSuccess()
            } catch (e: Exception) {
                false
            }
        }
    }
    
    override suspend fun placeBuyOrder(
        symbol: String,
        volume: Double,
        price: Double
    ): OrderResult {
        // Use Alpaca REST API
        return OrderResult("", "", 0.0, 0L)
    }
    
    // Implement other methods similarly
}
```

### Example 3: Add Trade Execution to Strategy

```kotlin
// In StrategyEngine.kt

class StrategyEngineWithBroker(
    private val brokerClient: BrokerClient
) {
    suspend fun executeBuySignal(analysis: ChartAnalysis) {
        if (shouldBuy(analysis)) {
            try {
                // Calculate position size
                val positionSize = riskCalculator.calculatePositionSize(
                    accountBalance = brokerClient.getAccountBalance(),
                    riskPercent = 2.0,
                    entryPrice = analysis.currentPrice,
                    stopLossPrice = analysis.supportLevel
                )
                
                // Place order
                val order = brokerClient.placeBuyOrder(
                    symbol = analysis.symbol,
                    volume = positionSize,
                    price = analysis.currentPrice
                )
                
                // Save order to database
                orderRepository.insert(order)
                
                println("Buy order placed: ${order.orderId}")
            } catch (e: Exception) {
                println("Order failed: ${e.message}")
            }
        }
    }
}
```

---

## Database Extensions

### Location
`app/src/main/java/com/visiontrade/data/database/`

### Example 1: Add Execution Log Table

```kotlin
// Add to AnalysisDatabase.kt

@Entity(tableName = "execution_log")
data class ExecutionLogEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val orderId: String,
    val symbol: String,
    val type: String,  // BUY or SELL
    val volume: Double,
    val executionPrice: Double,
    val profit: Double,
    val timestamp: Long = System.currentTimeMillis()
)

@Dao
interface ExecutionLogDao {
    @Insert
    suspend fun insert(entry: ExecutionLogEntry)
    
    @Query("SELECT * FROM execution_log ORDER BY timestamp DESC")
    fun getAllExecutions(): Flow<List<ExecutionLogEntry>>
    
    @Query("SELECT * FROM execution_log WHERE symbol = :symbol")
    fun getExecutionsBySymbol(symbol: String): Flow<List<ExecutionLogEntry>>
    
    @Query("SELECT SUM(profit) FROM execution_log")
    suspend fun getTotalProfit(): Double
    
    @Delete
    suspend fun delete(entry: ExecutionLogEntry)
}

// Update AnalysisDatabase
@Database(
    entities = [
        AnalysisHistoryEntity::class,
        ExecutionLogEntry::class  // NEW
    ],
    version = 2  // Increment version
)
abstract class AnalysisDatabase : RoomDatabase() {
    abstract fun analysisHistoryDao(): AnalysisHistoryDao
    abstract fun executionLogDao(): ExecutionLogDao  // NEW
}
```

### Example 2: Add Account Stats Table

```kotlin
@Entity(tableName = "account_stats")
data class AccountStatsEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val date: String,
    val balance: Double,
    val equity: Double,
    val drawdown: Double,
    val winRate: Double,
    val timestamp: Long = System.currentTimeMillis()
)

@Dao
interface AccountStatsDao {
    @Insert
    suspend fun insert(entry: AccountStatsEntry)
    
    @Query("SELECT * FROM account_stats ORDER BY timestamp DESC LIMIT 30")
    fun getLastMonthStats(): Flow<List<AccountStatsEntry>>
    
    @Query("SELECT * FROM account_stats WHERE date = :date LIMIT 1")
    suspend fun getStatsByDate(date: String): AccountStatsEntry?
}
```

### Example 3: Query Helper

```kotlin
// Create: app/src/main/java/com/visiontrade/data/repository/StatisticsRepository.kt

class StatisticsRepository(
    private val executionLogDao: ExecutionLogDao,
    private val accountStatsDao: AccountStatsDao
) {
    fun getTotalProfit(): Flow<Double> = flow {
        emit(executionLogDao.getTotalProfit())
    }
    
    fun getWinRate(): Flow<Double> = flow {
        val allExecutions = executionLogDao.getAllExecutions().first()
        val wins = allExecutions.count { it.profit > 0 }
        emit(if (allExecutions.isEmpty()) 0.0 else wins.toDouble() / allExecutions.size)
    }
    
    fun getMonthlyStats(): Flow<List<AccountStatsEntry>> {
        return accountStatsDao.getLastMonthStats()
    }
}
```

---

## Vision Pipeline Tuning

### Location
`app/src/main/java/com/visiontrade/domain/vision/VisionChartAnalyzer.kt`

### Example 1: Adjust Color Detection Thresholds

```kotlin
// In VisionChartAnalyzer.kt

object VisionConfig {
    // Color detection (RGB values)
    const val BULLISH_RED_RANGE_MIN = 0
    const val BULLISH_RED_RANGE_MAX = 50
    
    const val BEARISH_GREEN_RANGE_MIN = 250
    const val BEARISH_GREEN_RANGE_MAX = 255
    
    // Confidence thresholds
    const val MIN_CANDLE_CONFIDENCE = 0.6  // 60% minimum
    const val MIN_OHLC_EXTRACTION_CONFIDENCE = 0.5
    const val MIN_CHART_COVERAGE = 0.7  // 70% of chart must be readable
    
    // Data requirements
    const val MIN_CANDLES_REQUIRED = 50  // Minimum candles per timeframe
    const val MIN_TIMEFRAMES_QUALITY = 2  // At least 2 good timeframes
}

private fun detectCandleColor(pixel: Int): CandleColor? {
    val red = (pixel shr 16) and 0xFF
    val green = (pixel shr 8) and 0xFF
    val blue = pixel and 0xFF
    
    return when {
        red > VisionConfig.BEARISH_GREEN_RANGE_MIN -> CandleColor.BULLISH
        green > VisionConfig.BEARISH_GREEN_RANGE_MIN -> CandleColor.BEARISH
        else -> null  // Uncertain
    }
}
```

### Example 2: Improve Candle Detection

```kotlin
private fun detectCandlesInImage(bitmap: Bitmap): List<DetectedCandle> {
    val candles = mutableListOf<DetectedCandle>()
    val pixels = IntArray(bitmap.width * bitmap.height)
    bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
    
    var currentCandle: MutableList<Int>? = null
    var minX = 0
    var maxX = 0
    
    // Scan horizontally to find candle columns
    for (x in 0 until bitmap.width) {
        var hasCandle = false
        
        // Check if this column has candle pixels
        for (y in 0 until bitmap.height) {
            val pixel = pixels[y * bitmap.width + x]
            if (detectCandleColor(pixel) != null) {
                hasCandle = true
                break
            }
        }
        
        if (hasCandle) {
            if (currentCandle == null) {
                currentCandle = mutableListOf(x)
                minX = x
            }
            maxX = x
        } else if (currentCandle != null) {
            // End of candle
            val candle = extractCandleOHLC(bitmap, minX, maxX)
            candles.add(candle)
            currentCandle = null
        }
    }
    
    return candles
}
```

### Example 3: Confidence Scoring

```kotlin
private fun calculateImageQuality(
    bullishCandlesDetected: Int,
    bearishCandlesDetected: Int,
    averageConfidence: Double,
    chartCoverage: Double
): ImageQuality {
    val totalCandles = bullishCandlesDetected + bearishCandlesDetected
    
    return when {
        totalCandles < 30 || averageConfidence < 0.5 -> ImageQuality.POOR
        totalCandles < 50 || averageConfidence < 0.65 -> ImageQuality.FAIR
        chartCoverage < 0.6 || averageConfidence < 0.75 -> ImageQuality.GOOD
        else -> ImageQuality.EXCELLENT
    }
}
```

---

## Advanced Optimization

### Example 1: Optimize APK Size

In `build.gradle.kts`:
```kotlin
android {
    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    
    packagingOptions {
        exclude("META-INF/proguard/androidx-*.pro")
        exclude("META-INF/kotlin-stdlib.kotlin_module")
    }
}
```

### Example 2: Enable R8 Code Optimization

```kotlin
android {
    buildTypes {
        release {
            // R8 (successor to ProGuard)
            isMinifyEnabled = true
            // R8 is automatically used for minification
        }
    }
}
```

### Example 3: Optimize Images

```kotlin
// Replace PNG with WebP (smaller)
android {
    buildTypes {
        all {
            // Automatically convert PNGs to WebP
        }
    }
}
```

### Example 4: Lazy Load Components

```kotlin
@Composable
fun MainScreen() {
    var selectedTab by remember { mutableStateOf(0) }
    
    when (selectedTab) {
        0 -> AnalysisScreen()  // Loaded immediately
        1 -> {
            // Lazy load when needed
            LazyColumn {
                item {
                    RiskCalculatorScreen()
                }
            }
        }
        2 -> HistoryScreen()   // Lazy loaded
    }
}
```

---

## Testing Custom Changes

### Example 1: Test Custom Strategy

```kotlin
// Create: app/src/test/java/com/visiontrade/domain/CustomStrategyTests.kt

class CustomStrategyTests {
    @Test
    fun testCustomBuyConditions() {
        // Arrange
        val strategy = StrategyEngine()
        val analysis = ChartAnalysis(
            m15Timeframe = TimeframeData(
                ema20 = 1.0850,
                ema50 = 1.0840,
                rsi14 = IndicatorValue(value = 65.0),  // Below 70
                adx14 = IndicatorValue(value = 25.0),  // Above 20
                bullishEngulfing = true,
                priceNearSupport = true
            ),
            h1Timeframe = TimeframeData(
                ema20 = 1.0848,
                ema50 = 1.0838,
                rsi14 = IndicatorValue(value = 60.0),
                adx14 = IndicatorValue(value = 22.0),
                bullishEngulfing = true,
                priceNearSupport = true
            ),
            h4Timeframe = TimeframeData(
                ema20 = 1.0845,
                ema50 = 1.0835,
                rsi14 = IndicatorValue(value = 58.0),
                adx14 = IndicatorValue(value = 23.0),
                bullishEngulfing = true,
                priceNearSupport = true
            ),
            overallTrend = Trend.BULLISH
        )
        
        // Act
        val result = strategy.analyzeChart(analysis)
        
        // Assert
        assertEquals(Signal.BUY, result.signal)
        assertEquals(Confidence.HIGH, result.confidence)
    }
    
    @Test
    fun testCustomThresholds() {
        // Test that your modified thresholds work
        val analysis = ChartAnalysis(
            // ... with RSI at new threshold ...
        )
        val result = strategy.analyzeChart(analysis)
        assertEquals(Signal.BUY, result.signal)
    }
}
```

### Example 2: Test New Indicator

```kotlin
class CustomIndicatorTests {
    @Test
    fun testMACDCalculation() {
        // Arrange
        val candles = listOf(
            Candle(1.0800, 1.0850, 1.0750, 1.0820),
            Candle(1.0820, 1.0860, 1.0810, 1.0840),
            // ... more candles ...
        )
        
        // Act
        val macd = IndicatorEngine().calculateMACD(candles)
        
        // Assert
        assertNotNull(macd)
        assertTrue(macd.macd != 0.0)
    }
}
```

### Example 3: Integration Test

```kotlin
@RunWith(AndroidJUnit4::class)
class StrategyIntegrationTests {
    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()
    
    private lateinit var database: AnalysisDatabase
    private lateinit var analysisRepository: AnalysisRepository
    
    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().context
        database = Room.inMemoryDatabaseBuilder(context, AnalysisDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        analysisRepository = AnalysisRepository(database.analysisHistoryDao())
    }
    
    @Test
    fun testStrategyAnalysisFlow() = runBlockingTest {
        // Arrange
        val analysis = ChartAnalysis(/* ... */)
        
        // Act
        analysisRepository.insert(AnalysisHistoryEntity.fromAnalysis(analysis))
        
        // Assert
        val saved = analysisRepository.getAll().first()
        assertEquals(1, saved.size)
        assertEquals(Signal.BUY, saved[0].signal)
    }
}
```

---

## Frequently Asked Questions

### Q: How do I change the app icon?

A: Replace images in:
- `app/src/main/res/mipmap-hdpi/ic_launcher.png`
- `app/src/main/res/mipmap-mdpi/ic_launcher.png`
- `app/src/main/res/mipmap-xhdpi/ic_launcher.png`
- `app/src/main/res/mipmap-xxhdpi/ic_launcher.png`
- `app/src/main/res/mipmap-xxxhdpi/ic_launcher.png`
- `app/src/main/res/drawable-xxxhdpi/ic_launcher_foreground.png`

### Q: How do I add a new dependency?

A: Edit `app/build.gradle.kts`:
```kotlin
dependencies {
    implementation("com.example:library:1.0.0")  // Add here
}
```

Then sync Gradle.

### Q: How do I change the minimum Android version?

A: Edit `build.gradle.kts` (app module):
```kotlin
android {
    defaultConfig {
        minSdk = 26  // Change this
    }
}
```

### Q: How do I add logging?

A: Use `Log` from Android:
```kotlin
import android.util.Log

Log.d("VisionTrade", "Debug message")
Log.e("VisionTrade", "Error message", exception)
```

View logs:
```bash
adb logcat | grep VisionTrade
```

### Q: How do I add proguard rules for custom libraries?

A: Add to `app/proguard-rules.pro`:
```
-keep class com.example.library.** { *; }
-keepclassmembers class com.example.library.** { *; }
```

### Q: How do I share code between screens?

A: Use ViewModel:
```kotlin
class SharedViewModel : ViewModel() {
    val sharedData = mutableStateOf("")
}

// In Screen 1
val sharedViewModel: SharedViewModel = hiltViewModel()
sharedViewModel.sharedData.value = "data"

// In Screen 2
val sharedViewModel: SharedViewModel = hiltViewModel()
val data = sharedViewModel.sharedData.value
```

---

## Common Mistakes to Avoid

❌ **Don't** modify `/build/` directory files - they're generated

❌ **Don't** hardcode values - use resources (`strings.xml`, `colors.xml`)

❌ **Don't** call network operations on main thread - use coroutines/Dispatchers

❌ **Don't** ignore null safety - use proper null checks

❌ **Don't** skip testing - always test custom code

✅ **Do** follow the existing code patterns

✅ **Do** run tests after making changes

✅ **Do** commit changes frequently

✅ **Do** document your customizations

---

## Resources

- [Android Developer Docs](https://developer.android.com/)
- [Jetpack Compose](https://developer.android.com/jetpack/compose)
- [Room Database](https://developer.android.com/training/data-storage/room)
- [CameraX](https://developer.android.com/training/camerax)
- [Kotlin Coroutines](https://kotlinlang.org/docs/coroutines-overview.html)
- [Material Design 3](https://m3.material.io/)

---

## Support

For issues or questions:
1. Check existing code patterns
2. Review test files for examples
3. Check Android documentation
4. Ask in Android development communities

Good luck customizing VisionTrade! 🚀

