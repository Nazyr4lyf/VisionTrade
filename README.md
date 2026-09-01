# VisionTrade AI Assistant

Professional Forex trading analysis Android application with deterministic strategy logic.

## Overview

VisionTrade is a lightweight, offline-first Android app for analyzing Forex trading charts and providing deterministic BUY/SELL/WAIT signals based on multi-timeframe technical analysis.

**Core Philosophy:** NO GUESSING. If conditions are not met, return WAIT.

## Features

### Trading Analysis
- **Multi-timeframe Analysis**: M15, H1, H4 confirmation
- **Technical Indicators**: EMA 20/50, RSI 14, ADX 14
- **Pattern Detection**: Bullish/Bearish engulfing patterns
- **Market Structure**: Support/resistance zone calculation
- **Image Analysis**: Extract chart data from screenshots
- **Deterministic Strategy**: 8 conditions for BUY/SELL signals

### Risk Management
- **Position Sizing Calculator**: Calculate lot size based on risk
- **Risk/Reward Analysis**: Evaluate trade setup quality
- **Account Growth Simulator**: Test strategy hypothetically

### Data Management
- **Local History**: Store analysis results locally (Room DB)
- **No Cloud Dependency**: All processing offline
- **Screenshot Capture**: Analyze chart images

## Architecture

```
app/src/main/java/com/visiontrade/
├── domain/
│   ├── models/          # Core data structures
│   ├── indicators/      # EMA, RSI, ADX engines
│   ├── strategy/        # BUY/SELL/WAIT logic
│   └── risk/           # Position sizing & simulation
├── data/
│   ├── database/       # Room database & DAOs
│   └── repository/     # Data access abstraction
├── ui/
│   ├── screens/        # Compose screens
│   ├── components/     # Reusable Compose components
│   ├── theme/          # Material 3 theme & colors
│   └── viewmodel/      # ViewModels & state management
└── MainActivity.kt      # Entry point
```

## Technical Stack

- **Language**: Kotlin
- **UI Framework**: Jetpack Compose
- **Architecture**: MVVM + Clean Architecture
- **Database**: Room
- **Coroutines**: Async operations
- **Testing**: JUnit + Kotlin Test

## Strategy Specification

### BUY Signal (All 8 conditions required)
1. EMA20 > EMA50 on M15
2. EMA20 > EMA50 on H1
3. EMA20 > EMA50 on H4
4. RSI14 < 70
5. ADX14 > 20
6. Bullish engulfing pattern on M15
7. Price near support zone (0.2% tolerance)
8. Overall trend is bullish

### SELL Signal (All 8 conditions required)
1. EMA20 < EMA50 on M15
2. EMA20 < EMA50 on H1
3. EMA20 < EMA50 on H4
4. RSI14 > 30
5. ADX14 > 20
6. Bearish engulfing pattern on M15
7. Price near resistance zone (0.2% tolerance)
8. Overall trend is bearish

### WAIT Signal
- Default when conditions are incomplete
- No guessing on missing indicators
- Graceful degradation

## Building

### Prerequisites
- Android SDK 26+ (API level 26)
- Gradle 8.0+
- Java 17+
- Android Studio Flamingo or later

### Build Steps

```bash
cd VisionTradeAndroid
./gradlew build
./gradlew assembleDebug  # For debug APK
./gradlew assembleRelease  # For release APK
```

### Generated APK Location

Debug: `app/build/outputs/apk/debug/app-debug.apk`
Release: `app/build/outputs/apk/release/app-release.apk`

### Installing

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

## Project Structure

### Domain Layer (Business Logic)

#### Models (`domain/models/`)
- `TradingModels.kt`: Core data classes (Candle, EMAValue, RSIValue, ADXValue, etc.)

#### Indicators (`domain/indicators/`)
- `IndicatorEngine.kt`: Deterministic calculations
  - EMA calculation
  - RSI calculation
  - ADX calculation
  - Support/Resistance zones
  - Engulfing pattern detection

#### Strategy (`domain/strategy/`)
- `StrategyEngine.kt`: Trading decision logic
  - BUY condition checking
  - SELL condition checking
  - Conflict resolution
  - Confidence scoring

#### Risk (`domain/risk/`)
- `RiskCalculator.kt`: Position sizing
- `AccountGrowthSimulator.kt`: Simulation engine

### Data Layer

#### Database (`data/database/`)
- `AnalysisDatabase.kt`: Room database setup
- `AnalysisHistoryDao`: Data access object
- `AnalysisHistoryEntity`: Database entity

#### Repository (`data/repository/`)
- `AnalysisRepository.kt`: Data abstraction layer

### UI Layer

#### Screens (`ui/screens/`)
- `MainScreen.kt`: Entry point
- `AnalysisResultScreen.kt`: Signal display
- Additional screens (Risk, Simulator, History, Settings)

#### Components (`ui/components/`)
- `CommonComposables.kt`: Reusable UI components
  - SignalBadge
  - DataRow
  - SectionCard
  - Buttons
  - Input fields

#### Theme (`ui/theme/`)
- `Theme.kt`: Material 3 color scheme
- `Typography.kt`: Font styles

#### ViewModels (`ui/viewmodel/`)
- `AnalysisViewModel.kt`: Coordinates analysis
- `RiskViewModel.kt`: Risk calculations
- `ViewModelFactory.kt`: Dependency injection

### Testing (`test/` and `androidTest/`)

#### Unit Tests
- `IndicatorTests.kt`: EMA, RSI, ADX calculations
- `StrategyTests.kt`: Signal logic
- `RiskTests.kt`: Position sizing and simulation

## Indicator Calculations

### EMA (Exponential Moving Average)
```
EMA = Close * k + EMA_prev * (1 - k)
where k = 2 / (period + 1)
```

### RSI (Relative Strength Index)
```
RSI = 100 - (100 / (1 + RS))
RS = Average Gain / Average Loss
```

### ADX (Average Directional Index)
Based on True Range and Directional Movement

### Support/Resistance
Calculated from swing highs/lows in the lookback period (default: 50 candles)

## Color Scheme (VisionTrade Brand)

| Element | Color | Hex |
|---------|-------|-----|
| Brand Blue | Electric Blue | #0066FF |
| Brand Green | Neon Green | #00FF41 |
| Brand Cyan | Cyan | #00D9FF |
| BUY Signal | Green | #00FF41 |
| SELL Signal | Red | #FF0040 |
| WAIT Signal | Yellow | #FFB800 |
| Background | Dark Blue-Black | #0A0E27 |
| Surface | Navy | #1A1F3A |

## Testing

Run tests:
```bash
./gradlew test                    # Run unit tests
./gradlew connectedAndroidTest    # Run instrumented tests
```

## Known Limitations

1. **Vision Analysis**: Currently uses demo data. Real image analysis requires:
   - ML Kit or TensorFlow integration
   - Chart type detection
   - Candlestick OCR
   - Indicator line detection

2. **Real-time Data**: Currently uses static demo data. To add live feeds:
   - Integrate broker API (e.g., OANDA, IQ Option)
   - WebSocket connection
   - Real-time price updates

3. **Camera Integration**: Camera functionality not fully implemented
   - Requires CameraX integration
   - Image processing pipeline

## Future Enhancements

- [ ] ML-based vision analysis (ML Kit)
- [ ] Live data integration (broker APIs)
- [ ] Advanced pattern recognition
- [ ] Multi-asset support
- [ ] Custom strategy builder
- [ ] Backtesting engine
- [ ] Cloud sync (optional)
- [ ] Alert notifications
- [ ] Advanced charting

## Important Disclaimers

⚠️ **EDUCATIONAL USE ONLY**

This application is a technical analysis tool for personal experimentation and education. It:
- Does NOT guarantee trading profits
- Does NOT execute trades automatically
- Does NOT predict market direction with certainty
- Is provided "AS-IS" without warranties

**Trading is risky.** Past performance does not guarantee future results. Only trade with money you can afford to lose.

## License

This project is provided for educational purposes.

## Contact & Support

For issues, questions, or contributions, refer to the project documentation.

---

Built with ❤️ for serious traders who value precision and deterministic logic.
