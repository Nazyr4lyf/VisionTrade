import test from "node:test";
import assert from "node:assert/strict";
import { OpenPositionMonitor } from "../src/paper/open-position-monitor.mjs";

const now = 180_000;
const trade = {
  id: "paper-spot-1", status: "OPEN", openedAt: 60_000,
  symbol: "BTCUSDT", marketType: "SPOT", side: "LONG",
  quantity: "0.1", entryPrice: "100", stopLossPrice: "98",
  takeProfitPrice: "104", riskBudget: "1"
};
const validCandle = {
  timestampMillis: 120_000, open: "100", high: "101",
  low: "99", close: "100.5", isClosed: true
};

function makeMonitor({ trades = [trade], candles = [validCandle], fetchError, pipelineResult } = {}) {
  const calls = { fetched: [], processed: [] };
  const ledger = { async listOpenTrades() { return trades; } };
  const pipeline = {
    async processOpenTrade(input) {
      calls.processed.push(input);
      return pipelineResult ?? { status: "POSITION_OPEN", reason: "EXIT_NOT_HIT" };
    }
  };
  const bybit = {
    async getKlines(...args) {
      calls.fetched.push(args);
      if (fetchError) throw fetchError;
      return candles;
    }
  };
  const monitor = new OpenPositionMonitor({
    ledger, pipeline, bybit,
    spotCosts: { feeRate: 0.001, slippageBps: 5 },
    maxCandleAgeMs: 180_000
  });
  return { monitor, calls };
}

test("reads persisted open positions and fetches closed Spot candles from after entry", async () => {
  const { monitor, calls } = makeMonitor();
  const result = await monitor.runCycle({ nowMillis: now });
  assert.equal(result.openPositions, 1);
  assert.equal(result.closed, 0);
  assert.equal(result.skipped, 0);
  assert.equal(calls.fetched.length, 1);
  assert.deepEqual(calls.fetched[0], ["spot", "BTCUSDT", "1", {
    limit: 1000, start: 120_000, end: now
  }]);
  assert.equal(calls.processed.length, 1);
  assert.equal(calls.processed[0].costs.fundingCost, 0);
  assert.equal(calls.processed[0].candles[0].open, 100);
});

test("does not call exit pipeline when market data fetch fails", async () => {
  const { monitor, calls } = makeMonitor({ fetchError: new Error("provider down") });
  const result = await monitor.runCycle({ nowMillis: now });
  assert.equal(result.skipped, 1);
  assert.equal(result.results[0].reason, "MARKET_DATA_FETCH_FAILED");
  assert.equal(calls.processed.length, 0);
});

test("skips stale closed candle data without invoking the exit pipeline", async () => {
  const { monitor, calls } = makeMonitor({
    candles: [{ ...validCandle, timestampMillis: 0 }]
  });
  const result = await monitor.runCycle({ nowMillis: 600_000 });
  assert.equal(result.skipped, 1);
  assert.equal(result.results[0].reason, "STALE_CLOSED_CANDLE_DATA");
  assert.equal(calls.processed.length, 0);
});

test("skips incomplete history instead of making a potentially false exit decision", async () => {
  const { monitor, calls } = makeMonitor({
    candles: [{ ...validCandle, timestampMillis: 180_000 }]
  });
  const result = await monitor.runCycle({ nowMillis: now });
  assert.equal(result.results[0].reason, "INSUFFICIENT_POST_ENTRY_HISTORY");
  assert.equal(calls.processed.length, 0);
});

test("skips perpetual positions until historical funding costs are modelled", async () => {
  const { monitor, calls } = makeMonitor({
    trades: [{ ...trade, marketType: "USDT_PERPETUAL" }]
  });
  const result = await monitor.runCycle({ nowMillis: now });
  assert.equal(result.skipped, 1);
  assert.equal(result.results[0].reason, "PERPETUAL_FUNDING_COST_MODEL_REQUIRED");
  assert.equal(calls.fetched.length, 0);
});

test("skips invalid candle prices before invoking exit simulation", async () => {
  const { monitor, calls } = makeMonitor({
    candles: [{ ...validCandle, high: "not-a-price" }]
  });
  const result = await monitor.runCycle({ nowMillis: now });
  assert.equal(result.results[0].reason, "INVALID_CANDLE_DATA");
  assert.equal(calls.processed.length, 0);
});

test("reports closed positions from the pipeline", async () => {
  const { monitor } = makeMonitor({
    pipelineResult: { status: "PAPER_TRADE_CLOSED", reason: "STOP_LOSS" }
  });
  const result = await monitor.runCycle({ nowMillis: now });
  assert.equal(result.closed, 1);
});
