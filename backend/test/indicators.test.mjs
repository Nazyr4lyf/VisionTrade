import test from "node:test";
import assert from "node:assert/strict";
import { calculateIndicators, ema, rsi, relativeVolume } from "../src/strategy/indicators.mjs";

function candle(index, { close = 100 + index, volume = 10, closed = true, timeframeMinutes = 1 } = {}) {
  const price = close;
  return {
    timestampMillis: 1_800_000_000_000 + index * timeframeMinutes * 60_000,
    open: price - 0.4,
    high: price + 1,
    low: price - 1,
    close: price,
    volume,
    timeframeMinutes,
    isClosed: closed
  };
}

test("EMA uses the initial simple-average seed and recursive smoothing", () => {
  assert.equal(ema([1, 2, 3, 4, 5], 3), 4);
});

test("RSI handles rising, falling and flat markets", () => {
  assert.equal(rsi([1, 2, 3, 4, 5, 6, 7, 8], 7), 100);
  assert.equal(rsi([8, 7, 6, 5, 4, 3, 2, 1], 7), 0);
  assert.equal(rsi([5, 5, 5, 5, 5, 5, 5, 5], 7), 50);
});

test("relative volume compares the latest candle with the prior window", () => {
  const rows = Array.from({ length: 21 }, (_, i) => candle(i, { volume: i === 20 ? 20 : 10 }));
  assert.equal(relativeVolume(rows, 20), 2);
});

test("indicator calculation excludes unclosed candles", () => {
  const rows = Array.from({ length: 60 }, (_, i) => candle(i));
  rows.push(candle(60, { close: 500, closed: false }));
  const result = calculateIndicators(rows);
  assert.equal(result.ema20, ema(rows.slice(0, 60).map(c => c.close), 20));
  assert.ok(result.rsi7 !== null);
  assert.ok(result.atr14 !== null);
  assert.ok(result.adx14 !== null);
});

test("indicator input rejects mixed timeframes and duplicate timestamps", () => {
  const mixed = [candle(0), candle(1, { timeframeMinutes: 5 })];
  assert.throws(() => calculateIndicators(mixed), /one timeframe/);

  const duplicate = [candle(0), { ...candle(1), timestampMillis: candle(0).timestampMillis }];
  assert.throws(() => calculateIndicators(duplicate), /Duplicate candle timestamps/);
});


test("indicator outputs match the Android golden parity contract", () => {
  const rows = Array.from({ length: 80 }, (_, i) => {
    const close = 100 + Math.sin(i / 3) * 2 + i * 0.01;
    const open = close + (i % 2 === 0 ? 0.2 : -0.2);
    return {
      timestampMillis: 1_800_000_000_000 + i * 60_000,
      open, high: Math.max(open, close) + 0.8,
      low: Math.min(open, close) - 0.7, close,
      volume: 100 + (i % 7) * 5,
      timeframeMinutes: 1, isClosed: true
    };
  });
  const result = calculateIndicators(rows);
  const expected = {
    ema20: 100.74551780975858,
    ema50: 100.5416950330074,
    rsi7: 80.92377481582348,
    atr14: 1.7000000000000028,
    adx14: 20.897143294772288,
    vwap: 100.48630454185279,
    volumeSma20: 115,
    relativeVolume: 0.9544468546637744
  };
  for (const [key, value] of Object.entries(expected)) {
    assert.ok(Math.abs(result[key] - value) < 1e-8, `${key} differs from the Android golden contract: ${result[key]}`);
  }
});
