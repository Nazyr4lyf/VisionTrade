# VisionTrade Vision Pipeline

The app now supports a real local chart-image path without fabricating indicator values.

## Screenshot mode
Select exactly three screenshots in order: M15, H1, H4. The parser scans the chart for red/green candle pixels, reconstructs relative OHLC values, and requires at least 50 usable candles per timeframe. It then calculates EMA20, EMA50, RSI14, ADX14, support/resistance and engulfing patterns using the existing domain engine.

## Camera mode
CameraX captures three images in the same M15 → H1 → H4 sequence. The same local parser processes them.

## Safety
The parser never invents missing timeframes or fills missing indicators with demo values. If any timeframe cannot be extracted with sufficient confidence, ChartAnalysis is marked incomplete and the existing StrategyEngine returns WAIT.

## Important limitation
The parser depends on visible red/green candle rendering. Different chart themes, monochrome charts, overlays, heavy annotations, or screenshots with too few candles can cause WAIT. This is intentional.
