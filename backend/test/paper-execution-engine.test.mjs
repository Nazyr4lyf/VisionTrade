import test from "node:test";
import assert from "node:assert/strict";
import { PaperExecutionEngine } from "../src/paper/paper-execution-engine.mjs";

const engine = new PaperExecutionEngine();
const trade = (overrides = {}) => ({
  id: "paper-1", status: "OPEN", openedAt: 1, symbol: "BTCUSDT", marketType: "SPOT",
  side: "LONG", quantity: "0.1", entryPrice: "100",
  stopLossPrice: "98", takeProfitPrice: "104", riskBudget: "1",
  ...overrides
});
const candle = (overrides = {}) => ({
  timestampMillis: 60_000, open: 100, high: 101, low: 99, close: 100.5, isClosed: true,
  ...overrides
});

test("long stop is triggered and net PnL includes both fees and slippage", () => {
  const result = engine.evaluateExit({
    trade: trade(), candles: [candle({ low: 97.5 })], feeRate: 0.001, slippageBps: 10
  });
  assert.equal(result.closed, true);
  assert.equal(result.reason, "STOP_LOSS");
  assert.ok(result.fillPrice < 98);
  assert.ok(result.netPnl < result.grossPnl);
});

test("long gap-through stop fills at worse open before slippage", () => {
  const result = engine.evaluateExit({
    trade: trade(), candles: [candle({ open: 96, high: 97, low: 95, close: 96.5 })],
    slippageBps: 10
  });
  assert.equal(result.reason, "STOP_LOSS");
  assert.ok(result.fillPrice < 96);
});

test("if stop and target are both touched in one candle, stop wins conservatively", () => {
  const result = engine.evaluateExit({
    trade: trade(), candles: [candle({ high: 105, low: 97 })]
  });
  assert.equal(result.reason, "STOP_LOSS");
});

test("target exits at trigger price before costs", () => {
  const result = engine.evaluateExit({
    trade: trade(), candles: [candle({ high: 105, low: 99 })]
  });
  assert.equal(result.reason, "TAKE_PROFIT");
  assert.equal(result.fillPrice, 104);
  assert.ok(result.netPnl > 0);
});

test("short perpetual gap-through stop fills at worse open", () => {
  const result = engine.evaluateExit({
    trade: trade({ marketType: "USDT_PERPETUAL", side: "SHORT", stopLossPrice: 102, takeProfitPrice: 96 }),
    candles: [candle({ open: 104, high: 105, low: 103, close: 104 })],
    slippageBps: 10
  });
  assert.equal(result.reason, "STOP_LOSS");
  assert.ok(result.fillPrice > 104);
});

test("spot short is rejected", () => {
  assert.equal(engine.evaluateExit({
    trade: trade({ side: "SHORT", stopLossPrice: 102, takeProfitPrice: 96 }),
    candles: [candle({ high: 103 })]
  }).reason, "UNSUPPORTED_POSITION");
});

test("open candles are ignored and no exit is inferred", () => {
  const result = engine.evaluateExit({
    trade: trade(), candles: [candle({ low: 90, isClosed: false })]
  });
  assert.equal(result.closed, false);
  assert.equal(result.reason, "EXIT_NOT_HIT");
});

test("out-of-order candles fail closed", () => {
  const result = engine.evaluateExit({
    trade: trade(), candles: [
      candle({ timestampMillis: 120_000 }),
      candle({ timestampMillis: 60_000 })
    ]
  });
  assert.equal(result.reason, "INVALID_OR_OUT_OF_ORDER_CANDLE");
});

test("invalid execution costs fail closed", () => {
  assert.equal(engine.evaluateExit({
    trade: trade(), candles: [candle()], feeRate: -0.1
  }).reason, "INVALID_EXECUTION_COSTS");
});

test("non-open trade cannot be evaluated", () => {
  assert.equal(engine.evaluateExit({
    trade: trade({ status: "CLOSED" }), candles: [candle()]
  }).reason, "TRADE_NOT_OPEN");
});


test("candles that started before the trade opened cannot trigger an exit", () => {
  const result = engine.evaluateExit({
    trade: trade({ openedAt: 60_001 }),
    candles: [candle({ timestampMillis: 60_000, low: 90, high: 110 })]
  });
  assert.equal(result.closed, false);
  assert.equal(result.reason, "EXIT_NOT_HIT");
});
