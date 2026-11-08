import { calculateIndicators } from "./indicators.mjs";

const NO_TRADE = "NO_TRADE";

function noTrade(symbol, marketType, reason, nowMillis, extras = {}) {
  return {
    symbol, marketType, action: NO_TRADE, regime: extras.regime ?? "UNKNOWN",
    referencePrice: extras.referencePrice ?? null,
    supportZone: extras.supportZone ?? null,
    resistanceZone: extras.resistanceZone ?? null,
    confidenceScore: 0, reasons: [reason], evaluatedAtMillis: nowMillis
  };
}

function distanceToZone(price, zone) {
  if (price < zone.low) return zone.low - price;
  if (price > zone.high) return price - zone.high;
  return 0;
}

function detectZones(candles, { leftBars = 3, rightBars = 3, tolerance, minimumTouches = 2 }) {
  if (!Number.isInteger(leftBars) || leftBars < 1 || !Number.isInteger(rightBars) || rightBars < 1) {
    throw new Error("Pivot windows must be positive integers");
  }
  if (!Number.isFinite(tolerance) || tolerance < 0) throw new Error("Zone tolerance must be non-negative");
  const highs = [], lows = [];
  const lastPivot = candles.length - rightBars - 1;
  for (let i = leftBars; i <= lastPivot; i++) {
    const current = candles[i];
    const left = candles.slice(i - leftBars, i);
    const right = candles.slice(i + 1, i + rightBars + 1);
    if (left.every(c => current.high > c.high) && right.every(c => current.high > c.high)) {
      highs.push({ price: current.high, timestampMillis: current.timestampMillis });
    }
    if (left.every(c => current.low < c.low) && right.every(c => current.low < c.low)) {
      lows.push({ price: current.low, timestampMillis: current.timestampMillis });
    }
  }

  function cluster(pivots, type) {
    const groups = [];
    for (const pivot of pivots.slice().sort((a, b) => a.price - b.price)) {
      const current = groups.at(-1);
      const center = current?.reduce((sum, item) => sum + item.price, 0) / (current?.length || 1);
      if (!current || Math.abs(pivot.price - center) > tolerance) groups.push([pivot]);
      else current.push(pivot);
    }
    return groups.filter(group => group.length >= minimumTouches).map(group => {
      const prices = group.map(item => item.price);
      return {
        type,
        zone: {
          low: Math.min(...prices), high: Math.max(...prices),
          touches: group.length,
          lastConfirmedAtMillis: Math.max(...group.map(item => item.timestampMillis))
        }
      };
    });
  }

  return [...cluster(highs, "RESISTANCE"), ...cluster(lows, "SUPPORT")]
    .sort((a, b) => (a.zone.low + a.zone.high) / 2 - (b.zone.low + b.zone.high) / 2);
}

function bullishEngulfing(previous, current) {
  return previous.close < previous.open && current.close > current.open &&
    current.open <= previous.close && current.close >= previous.open;
}
function bearishEngulfing(previous, current) {
  return previous.close > previous.open && current.close < current.open &&
    current.open >= previous.close && current.close <= previous.open;
}

/**
 * Node port of the Android range-scalping decision rules.
 * Signal generation is intentionally pure: no orders, persistence or side effects.
 */
export class ScalpingStrategyEngine {
  constructor({
    fiveMinuteLookback = 100, oneMinuteLookback = 60,
    pivotLeftBars = 3, pivotRightBars = 3,
    maximumMarketDataAgeMillis = 5_000,
    adxTrendThreshold = 25, relativeVolumeThreshold = 1.2,
    minimumRsi = 35, maximumRsi = 65,
    zoneAtrMultiplier = 0.3
  } = {}) {
    this.config = {
      fiveMinuteLookback, oneMinuteLookback, pivotLeftBars, pivotRightBars,
      maximumMarketDataAgeMillis, adxTrendThreshold, relativeVolumeThreshold,
      minimumRsi, maximumRsi, zoneAtrMultiplier
    };
    for (const [key, value] of Object.entries(this.config)) {
      if (!Number.isFinite(value) || value <= 0) throw new Error(`Invalid strategy config: ${key}`);
    }
  }

  analyze({
    symbol, marketType, fiveMinuteCandles, oneMinuteCandles,
    marketDataReceivedAtMillis, nowMillis = Date.now()
  }) {
    if (typeof symbol !== "string" || !symbol.trim()) throw new Error("Symbol is required");
    if (!["SPOT", "USDT_PERPETUAL"].includes(marketType)) throw new Error("Unsupported market type");
    if (!Array.isArray(fiveMinuteCandles) || !Array.isArray(oneMinuteCandles)) {
      throw new Error("5m and 1m candle arrays are required");
    }
    if (!Number.isFinite(nowMillis) || nowMillis <= 0 ||
        !Number.isFinite(marketDataReceivedAtMillis) || marketDataReceivedAtMillis <= 0) {
      throw new Error("Market data timestamps must be positive");
    }
    if (marketDataReceivedAtMillis > nowMillis ||
        nowMillis - marketDataReceivedAtMillis > this.config.maximumMarketDataAgeMillis) {
      return noTrade(symbol, marketType, "Market data is stale or has an invalid receive timestamp.", nowMillis);
    }

    const five = fiveMinuteCandles.filter(c => c.isClosed).slice().sort((a,b)=>a.timestampMillis-b.timestampMillis);
    const one = oneMinuteCandles.filter(c => c.isClosed).slice().sort((a,b)=>a.timestampMillis-b.timestampMillis);
    if (five.some(c => c.timestampMillis > nowMillis) || one.some(c => c.timestampMillis > nowMillis)) {
      return noTrade(symbol, marketType, "A candle timestamp is in the future; market data is invalid.", nowMillis);
    }
    if (five.length < this.config.fiveMinuteLookback || one.length < this.config.oneMinuteLookback) {
      return noTrade(symbol, marketType, "Insufficient closed-candle history for the 5m/1m strategy.", nowMillis);
    }
    if (five.some(c => c.timeframeMinutes !== 5) || one.some(c => c.timeframeMinutes !== 1)) {
      return noTrade(symbol, marketType, "Candle timeframe does not match the required 5m/1m strategy.", nowMillis);
    }
    for (const rows of [five, one]) {
      for (let i=1;i<rows.length;i++) {
        if (rows[i-1].timestampMillis >= rows[i].timestampMillis) {
          return noTrade(symbol, marketType, "Candle timestamps must be unique and strictly increasing.", nowMillis);
        }
      }
    }

    let fiveIndicators, oneIndicators;
    try {
      fiveIndicators = calculateIndicators(five);
      oneIndicators = calculateIndicators(one);
    } catch (error) {
      return noTrade(symbol, marketType, error.message, nowMillis);
    }
    const price = Number(one.at(-1).close);
    const atr = fiveIndicators.atr14, adx = fiveIndicators.adx14;
    const rsi = oneIndicators.rsi7, vwap = oneIndicators.vwap, relativeVolume = oneIndicators.relativeVolume;
    if ([atr, adx, rsi, vwap, relativeVolume].some(value => value === null || !Number.isFinite(value))) {
      return noTrade(symbol, marketType, "Required indicator data is unavailable.", nowMillis, { referencePrice: price });
    }

    const regime = adx > this.config.adxTrendThreshold ? "TRENDING" : "RANGE";
    if (regime !== "RANGE") {
      return noTrade(symbol, marketType, "Range-scalping is disabled while 5m ADX indicates a strong trend.", nowMillis, { regime, referencePrice: price });
    }

    const tolerance = atr * this.config.zoneAtrMultiplier;
    const zones = detectZones(five, {
      leftBars: this.config.pivotLeftBars,
      rightBars: this.config.pivotRightBars,
      tolerance,
      minimumTouches: 2
    });
    const midpoint = zone => (zone.low + zone.high) / 2;
    const support = zones.filter(item => item.type === "SUPPORT")
      .map(item => item.zone).sort((a,b)=>Math.abs(midpoint(a)-price)-Math.abs(midpoint(b)-price))[0] ?? null;
    const resistance = zones.filter(item => item.type === "RESISTANCE")
      .map(item => item.zone).sort((a,b)=>Math.abs(midpoint(a)-price)-Math.abs(midpoint(b)-price))[0] ?? null;
    if (!support || !resistance || midpoint(support) >= midpoint(resistance)) {
      return noTrade(symbol, marketType, "A valid confirmed support and resistance range was not found.", nowMillis,
        { regime, referencePrice: price, supportZone: support, resistanceZone: resistance });
    }

    const nearSupport = distanceToZone(price, support) <= tolerance;
    const nearResistance = distanceToZone(price, resistance) <= tolerance;
    const previous = one.at(-2), current = one.at(-1);
    const bullish = bullishEngulfing(previous, current);
    const bearish = bearishEngulfing(previous, current);
    const volumeConfirmed = relativeVolume >= this.config.relativeVolumeThreshold;
    const aboveVwap = price >= vwap, belowVwap = price <= vwap;
    const rsiConfirmed = rsi >= this.config.minimumRsi && rsi <= this.config.maximumRsi;

    const longConfirmed = nearSupport && bullish && aboveVwap && rsiConfirmed && volumeConfirmed;
    const shortConfirmed = nearResistance && bearish && belowVwap && rsiConfirmed && volumeConfirmed;
    const common = { regime, referencePrice: price, supportZone: support, resistanceZone: resistance };
    if (longConfirmed && shortConfirmed) {
      return noTrade(symbol, marketType, "Conflicting long and short confirmations were detected.", nowMillis, common);
    }
    if (longConfirmed) {
      return { symbol, marketType, action: "BUY", ...common, confidenceScore: 100,
        reasons: ["Price tests confirmed 5m support.", "Closed 1m bullish engulfing confirmation.",
          "1m close is at or above VWAP.", "1m RSI(7) is in the 35–65 band.",
          "1m relative volume is at least 1.20x its prior 20-candle average."],
        evaluatedAtMillis: nowMillis };
    }
    if (shortConfirmed) {
      if (marketType === "SPOT") {
        return noTrade(symbol, marketType, "Bearish setup detected, but Spot mode cannot open a short position.", nowMillis, common);
      }
      return { symbol, marketType, action: "SELL", ...common, confidenceScore: 100,
        reasons: ["Price tests confirmed 5m resistance.", "Closed 1m bearish engulfing confirmation.",
          "1m close is at or below VWAP.", "1m RSI(7) is in the 35–65 band.",
          "1m relative volume is at least 1.20x its prior 20-candle average."],
        evaluatedAtMillis: nowMillis };
    }
    return noTrade(symbol, marketType, "Entry confirmation is incomplete or price is not at a confirmed range boundary.",
      nowMillis, common);
  }
}

export { detectZones };
