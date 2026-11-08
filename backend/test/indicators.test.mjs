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
