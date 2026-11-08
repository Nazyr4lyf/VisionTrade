import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { ScalpingStrategyEngine, detectZones } from "../src/strategy/scalping-strategy.mjs";

const NOW = 1_800_000_000_000;
function candle(index, timeframeMinutes, close, {
  open = close - 0.15, high = Math.max(open, close) + 0.5,
  low = Math.min(open, close) - 0.5, volume = 100, isClosed = true
} = {}) {
  return {
    timestampMillis: NOW - (120 - index) * timeframeMinutes * 60_000,
    open, high, low, close, volume, turnover: close * volume,
    timeframeMinutes, isClosed
  };
}
function history(timeframe, count, closeAt) {
  return Array.from({ length: count }, (_, i) =>
    candle(i, timeframe, closeAt(i), { volume: 100 + (i % 5) }));
}
function input(overrides = {}) {
  return {
    symbol: "BTCUSDT", marketType: "USDT_PERPETUAL",
    fiveMinuteCandles: history(5, 120, i => 100 + Math.sin(i / 3) * 2),
    oneMinuteCandles: history(1, 120, i => 100 + Math.sin(i / 2) * 0.8),
    marketDataReceivedAtMillis: NOW - 100,
    nowMillis: NOW,
    ...overrides
  };
}

test("stale market data fails closed with NO_TRADE", () => {
  const result = new ScalpingStrategyEngine().analyze(input({
    marketDataReceivedAtMillis: NOW - 10_000
  }));
  assert.equal(result.action, "NO_TRADE");
  assert.match(result.reasons[0], /stale/);
});

test("insufficient closed candle history fails closed", () => {
  const result = new ScalpingStrategyEngine().analyze(input({
    fiveMinuteCandles: history(5, 30, i => 100 + i),
    oneMinuteCandles: history(1, 30, i => 100 + i)
  }));
  assert.equal(result.action, "NO_TRADE");
  assert.match(result.reasons[0], /Insufficient/);
});

test("an unclosed candle cannot satisfy the minimum history requirement", () => {
  const five = history(5, 100, i => 100 + Math.sin(i));
  const one = history(1, 60, i => 100 + Math.sin(i));
  five[99] = { ...five[99], isClosed: false };
  one[59] = { ...one[59], isClosed: false };
  const result = new ScalpingStrategyEngine().analyze(input({
    fiveMinuteCandles: five, oneMinuteCandles: one
  }));
  assert.equal(result.action, "NO_TRADE");
  assert.match(result.reasons[0], /Insufficient/);
});

test("wrong timeframe input fails closed", () => {
  const five = history(5, 120, i => 100 + Math.sin(i));
  five[0] = { ...five[0], timeframeMinutes: 1 };
  const result = new ScalpingStrategyEngine().analyze(input({ fiveMinuteCandles: five }));
  assert.equal(result.action, "NO_TRADE");
  assert.match(result.reasons[0], /timeframe/);
});

test("strong 5m directional movement is classified as trending and blocked", () => {
  const five = history(5, 120, i => 100 + i * 0.8);
  const one = history(1, 120, i => 100 + i * 0.1);
  const result = new ScalpingStrategyEngine().analyze(input({
    fiveMinuteCandles: five, oneMinuteCandles: one
  }));
  assert.equal(result.action, "NO_TRADE");
  assert.equal(result.regime, "TRENDING");
});

test("strategy output is deterministic and never submits an order", () => {
  const engine = new ScalpingStrategyEngine();
  const first = engine.analyze(input());
  const second = engine.analyze(input());
  assert.deepEqual(first, second);
  assert.ok(["BUY", "SELL", "NO_TRADE"].includes(first.action));
  assert.equal(first.evaluatedAtMillis, NOW);
  assert.equal("orderId" in first, false);
});

test("confirmed support/resistance zones require right-side confirmation and repeated touches", () => {
  const rows = [];
  const highs = [10, 12, 10, 12.1, 10, 12, 10, 11, 10];
  for (let i = 0; i < highs.length; i++) {
    rows.push(candle(i, 5, 10, {
      open: 9.8, high: highs[i], low: 8 + (i % 2) * 0.1, isClosed: true
    }));
  }
  const zones = detectZones(rows, { leftBars: 1, rightBars: 1, tolerance: 0.3, minimumTouches: 2 });
  assert.ok(zones.some(item => item.type === "RESISTANCE" && item.zone.touches >= 2));
});


test("future-dated closed candles fail closed with NO_TRADE", () => {
  const futureFive = history(5, 100, i => 100 + Math.sin(i / 3))
    .map((c, i) => ({ ...c, timestampMillis: NOW + (i + 1) * 5 * 60_000 }));
  const futureOne = history(1, 60, i => 100 + Math.sin(i / 2))
    .map((c, i) => ({ ...c, timestampMillis: NOW + (i + 1) * 60_000 }));
  const result = new ScalpingStrategyEngine().analyze(input({
    fiveMinuteCandles: futureFive,
    oneMinuteCandles: futureOne
  }));
  assert.equal(result.action, "NO_TRADE");
  assert.match(result.reasons[0], /future/);
});


test("shared Android/backend decision fixtures produce identical action and regime contracts", () => {
  const fixturePath = new URL("../../app/src/test/resources/scalping/decision-cases.json", import.meta.url);
  const cases = JSON.parse(readFileSync(fileURLToPath(fixturePath), "utf8"));
  const nowMillis = 1_800_000_000_000;
  const engine = new ScalpingStrategyEngine();

  for (const item of cases) {
    const makeRows = (count, timeframeMinutes, pattern) =>
      Array.from({ length: count }, (_, index) => {
        const close = pattern === "TREND"
          ? 100 + index * 0.8
          : 100 + Math.sin(index / (timeframeMinutes === 5 ? 3 : 2)) * (timeframeMinutes === 5 ? 2 : 0.8);
        const open = close - 0.15;
        const timestampMillis = item.futureCandles
          ? nowMillis + (index + 1) * timeframeMinutes * 60_000
          : nowMillis - (count - index) * timeframeMinutes * 60_000;
        return {
          timestampMillis, open, high: Math.max(open, close) + 0.5,
          low: Math.min(open, close) - 0.5, close, volume: 100 + (index % 5),
          turnover: close * (100 + (index % 5)), timeframeMinutes, isClosed: true
        };
      });
    const result = engine.analyze({
      symbol: "BTCUSDT",
      marketType: item.marketType,
      fiveMinuteCandles: makeRows(item.fiveCount, item.fiveTimeframeMinutes, item.fivePattern),
      oneMinuteCandles: makeRows(item.oneCount, item.oneTimeframeMinutes, "RANGE"),
      marketDataReceivedAtMillis: nowMillis + item.receivedAtOffsetMillis,
      nowMillis
    });
    assert.equal(result.action, item.expectedAction, item.id);
    assert.equal(result.regime, item.expectedRegime, item.id);
    const expectedReferencePrice = 100 + Math.sin((item.oneCount - 1) / 2) * 0.8;
    assert.ok(Math.abs(result.referencePrice - expectedReferencePrice) < 1e-10, `${item.id}: reference price parity`);
    assert.ok(result.reasons.join(" ").toLowerCase().includes(item.expectedReasonContains.toLowerCase()), item.id);
  }
});
