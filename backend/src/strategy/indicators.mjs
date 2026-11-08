/**
 * Closed-candle indicators kept numerically aligned with the Android domain engine.
 * All inputs are chronological market candles; incomplete candles are excluded.
 */
function closedCandles(candles) {
  const rows = candles.filter(c => c.isClosed).slice().sort((a, b) => a.timestampMillis - b.timestampMillis);
  if (new Set(rows.map(c => c.timeframeMinutes)).size > 1) throw new Error("Indicator input must contain one timeframe only");
  for (let i = 1; i < rows.length; i++) {
    if (rows[i - 1].timestampMillis === rows[i].timestampMillis) throw new Error("Duplicate candle timestamps are not allowed");
  }
  for (const c of rows) {
    for (const field of ["open", "high", "low", "close", "volume"]) {
      if (!Number.isFinite(Number(c[field]))) throw new Error(`Invalid candle field: ${field}`);
    }
  }
  return rows;
}

export function ema(values, period) {
  if (!Number.isInteger(period) || period <= 0 || values.length < period || values.some(v => !Number.isFinite(v))) return null;
  const multiplier = 2 / (period + 1);
  let result = values.slice(0, period).reduce((a, b) => a + b, 0) / period;
  for (let i = period; i < values.length; i++) result = values[i] * multiplier + result * (1 - multiplier);
  return Number.isFinite(result) ? result : null;
}

export function rsi(closes, period) {
  if (!Number.isInteger(period) || period <= 0 || closes.length < period + 1 || closes.some(v => !Number.isFinite(v))) return null;
  let gain = 0, loss = 0;
  for (let i = 1; i <= period; i++) {
    const change = closes[i] - closes[i - 1];
    if (change > 0) gain += change; else loss += Math.abs(change);
  }
  let avgGain = gain / period, avgLoss = loss / period;
  for (let i = period + 1; i < closes.length; i++) {
    const change = closes[i] - closes[i - 1];
    avgGain = (avgGain * (period - 1) + Math.max(change, 0)) / period;
    avgLoss = (avgLoss * (period - 1) + Math.max(-change, 0)) / period;
  }
  const value = avgGain === 0 && avgLoss === 0 ? 50 : avgLoss === 0 ? 100 : 100 - 100 / (1 + avgGain / avgLoss);
  return Number.isFinite(value) ? Math.max(0, Math.min(100, value)) : null;
}

export function atr(candles, period) {
  if (!Number.isInteger(period) || period <= 0 || candles.length < period + 1) return null;
  const ranges = [];
  for (let i = 1; i < candles.length; i++) {
    const c = candles[i], previousClose = Number(candles[i - 1].close);
    ranges.push(Math.max(Number(c.high) - Number(c.low), Math.abs(Number(c.high) - previousClose), Math.abs(Number(c.low) - previousClose)));
  }
  if (ranges.length < period || ranges.some(v => !Number.isFinite(v))) return null;
  let value = ranges.slice(0, period).reduce((a, b) => a + b, 0) / period;
  for (let i = period; i < ranges.length; i++) value = (value * (period - 1) + ranges[i]) / period;
  return Number.isFinite(value) && value >= 0 ? value : null;
}

export function adx(candles, period) {
  if (!Number.isInteger(period) || period <= 0 || candles.length < period * 2 + 1) return null;
  const tr = [], plus = [], minus = [];
  for (let i = 1; i < candles.length; i++) {
    const c = candles[i], p = candles[i - 1];
    const high = Number(c.high), low = Number(c.low), prevHigh = Number(p.high), prevLow = Number(p.low), prevClose = Number(p.close);
    tr.push(Math.max(high - low, Math.abs(high - prevClose), Math.abs(low - prevClose)));
    const up = high - prevHigh, down = prevLow - low;
    plus.push(up > down && up > 0 ? up : 0);
    minus.push(down > up && down > 0 ? down : 0);
  }
  let smoothTr = tr.slice(0, period).reduce((a,b)=>a+b,0);
  let smoothPlus = plus.slice(0, period).reduce((a,b)=>a+b,0);
  let smoothMinus = minus.slice(0, period).reduce((a,b)=>a+b,0);
  const dx = [];
  const currentDx = () => {
    if (smoothTr <= 0) return 0;
    const pdi = 100 * smoothPlus / smoothTr, mdi = 100 * smoothMinus / smoothTr, sum = pdi + mdi;
    return sum <= 0 ? 0 : 100 * Math.abs(pdi - mdi) / sum;
  };
  dx.push(currentDx());
  for (let i = period; i < tr.length; i++) {
    smoothTr = smoothTr - smoothTr / period + tr[i];
    smoothPlus = smoothPlus - smoothPlus / period + plus[i];
    smoothMinus = smoothMinus - smoothMinus / period + minus[i];
    dx.push(currentDx());
  }
  if (dx.length < period) return null;
  let result = dx.slice(0, period).reduce((a,b)=>a+b,0) / period;
  for (let i = period; i < dx.length; i++) result = (result * (period - 1) + dx[i]) / period;
  return Number.isFinite(result) ? Math.max(0, Math.min(100, result)) : null;
}

export function vwap(candles) {
  if (!candles.length) return null;
  let weighted = 0, volume = 0;
  for (const c of candles) {
    const typical = (Number(c.high) + Number(c.low) + Number(c.close)) / 3;
    const qty = Number(c.volume);
    weighted += typical * qty;
    volume += qty;
  }
  return Number.isFinite(weighted) && Number.isFinite(volume) && volume > 0 ? weighted / volume : null;
}

export function simpleAverage(values, period) {
  if (!Number.isInteger(period) || period <= 0 || values.length < period) return null;
  const window = values.slice(-period);
  if (window.some(v => !Number.isFinite(v))) return null;
  return window.reduce((a,b)=>a+b,0) / period;
}

export function relativeVolume(candles, period) {
  if (period <= 0 || candles.length < period + 1) return null;
  const baseline = simpleAverage(candles.slice(0, -1).map(c => Number(c.volume)), period);
  const latest = Number(candles.at(-1).volume);
  return baseline !== null && baseline > 0 && Number.isFinite(latest) ? latest / baseline : null;
}

export function calculateIndicators(input) {
  const candles = closedCandles(input);
  const closes = candles.map(c => Number(c.close));
  const volumes = candles.map(c => Number(c.volume));
  return {
    ema20: ema(closes, 20),
    ema50: ema(closes, 50),
    rsi7: rsi(closes, 7),
    atr14: atr(candles, 14),
    adx14: adx(candles, 14),
    vwap: vwap(candles),
    volumeSma20: simpleAverage(volumes, 20),
    relativeVolume: relativeVolume(candles, 20)
  };
}
