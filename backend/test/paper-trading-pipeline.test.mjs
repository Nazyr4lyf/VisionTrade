import test from "node:test";
import assert from "node:assert/strict";
import { PaperTradingPipeline } from "../src/paper/paper-trading-pipeline.mjs";

function candles(timeframeMinutes, count = 40) {
  return Array.from({ length: count }, (_, index) => {
    const close = 100 + Math.sin(index / 3) * 0.5 + index * 0.01;
    return {
      timestampMillis: 1_000_000 + index * timeframeMinutes * 60_000,
      open: close - 0.05, high: close + 0.2, low: close - 0.2,
      close, volume: 100 + index, timeframeMinutes, isClosed: true
    };
  });
}

const signal = {
  symbol: "BTCUSDT", marketType: "SPOT", action: "BUY",
  referencePrice: 100,
  supportZone: { low: 99, high: 99.2 },
  resistanceZone: { low: 103, high: 103.2 },
  reasons: ["test signal"]
};
const instrument = {
  symbol: "BTCUSDT", marketType: "SPOT", priceTick: 0.01,
  quantityStep: 0.001, minimumQuantity: 0.001, minimumNotional: 5
};
const approval = {
  approved: true,
  paperTradeCandidate: {
    symbol: "BTCUSDT", marketType: "SPOT", side: "LONG",
    entryPrice: 100, stopLossPrice: 98, takeProfitPrice: 104,
    quantity: 0.1, riskBudget: 1, estimatedLossAtStop: 0.9, netRiskReward: 2.1
  }
};

function dependencies(overrides = {}) {
  const calls = { opened: 0, input: null };
  const ledger = {
    async openApprovedTrade(input) {
      calls.opened++;
      calls.input = input;
      return { created: true, reason: null, trade: { id: input.tradeId, status: "OPEN" } };
    }
  };
  return {
    calls,
    ledger,
    strategyEngine: { analyze: () => signal },
    approvalEngine: { evaluate: () => approval },
    ...overrides
  };
}

const snapshot = () => ({
  symbol: "BTCUSDT", marketType: "SPOT",
  fiveMinuteCandles: candles(5), oneMinuteCandles: candles(1),
  instrument, equity: 10_000, marketDataReceivedAtMillis: 2_000_000,
  nowMillis: 2_001_000
});

test("pipeline is disabled by default and performs no ledger writes", async () => {
  const deps = dependencies();
  const pipeline = new PaperTradingPipeline(deps);
  const result = await pipeline.processSnapshot(snapshot());
  assert.equal(result.status, "DISABLED");
  assert.equal(deps.calls.opened, 0);
});

test("enabled pipeline passes strategy signal through approval then opens paper ledger", async () => {
  const deps = dependencies();
  const pipeline = new PaperTradingPipeline({
    enabled: true, ledger: deps.ledger,
    strategyEngine: deps.strategyEngine,
    approvalEngine: deps.approvalEngine,
    idFactory: () => "paper-trade-test-1"
  });
  const result = await pipeline.processSnapshot(snapshot());
  assert.equal(result.status, "PAPER_TRADE_OPENED");
  assert.equal(deps.calls.opened, 1);
  assert.equal(deps.calls.input.tradeId, "paper-trade-test-1");
  assert.equal(deps.calls.input.idempotencyKey, "BTCUSDT:SPOT:3340000:BUY");
  assert.equal(deps.calls.input.approval, approval);
});

test("NO_TRADE signals never reach risk approval or persistence", async () => {
  const deps = dependencies({
    strategyEngine: { analyze: () => ({ ...signal, action: "NO_TRADE", reasons: ["no setup"] }) },
    approvalEngine: { evaluate: () => { throw new Error("must not run"); } }
  });
  const pipeline = new PaperTradingPipeline({ enabled: true, ledger: deps.ledger, strategyEngine: deps.strategyEngine, approvalEngine: deps.approvalEngine });
  const result = await pipeline.processSnapshot(snapshot());
  assert.equal(result.status, "NO_TRADE");
  assert.equal(deps.calls.opened, 0);
});

test("rejected risk approval never reaches the paper ledger", async () => {
  const deps = dependencies({ approvalEngine: { evaluate: () => ({ approved: false, rejectionReason: "RR_TOO_LOW" }) } });
  const pipeline = new PaperTradingPipeline({ enabled: true, ledger: deps.ledger, strategyEngine: deps.strategyEngine, approvalEngine: deps.approvalEngine });
  const result = await pipeline.processSnapshot(snapshot());
  assert.equal(result.status, "REJECTED");
  assert.equal(result.reason, "RR_TOO_LOW");
  assert.equal(deps.calls.opened, 0);
});

test("enabled pipeline refuses to start without a persistent ledger", () => {
  assert.throws(() => new PaperTradingPipeline({ enabled: true }), /persistent paper ledger/);
});


test("paper exit simulation closes the persistent ledger with simulated net result", async () => {
  const calls = [];
  const ledger = {
    async openApprovedTrade() { return { created: true }; },
    async closeTrade(input) {
      calls.push(input);
      return { closed: true, reason: null, trade: { id: input.tradeId, status: "CLOSED" } };
    }
  };
  const pipeline = new PaperTradingPipeline({ enabled: true, ledger });
  const result = await pipeline.processOpenTrade({
    trade: {
      id: "open-1", status: "OPEN", openedAt: 1,
      symbol: "BTCUSDT", marketType: "SPOT", side: "LONG",
      quantity: "0.1", entryPrice: "100", stopLossPrice: "98",
      takeProfitPrice: "104", riskBudget: "1"
    },
    candles: [{
      timestampMillis: 60_000, open: 100, high: 101, low: 97,
      close: 99, isClosed: true
    }],
    costs: { feeRate: 0.001, slippageBps: 5 },
    nowMillis: 120_000
  });
  assert.equal(result.status, "PAPER_TRADE_CLOSED");
  assert.equal(calls.length, 1);
  assert.equal(calls[0].tradeId, "open-1");
  assert.equal(calls[0].exit.reason, "STOP_LOSS");
  assert.equal(calls[0].closedAt, 120_000);
});

test("paper exit simulation leaves ledger untouched until a closed candle hits an exit", async () => {
  let closeCalls = 0;
  const ledger = {
    async openApprovedTrade() { return { created: true }; },
    async closeTrade() { closeCalls++; return { closed: true }; }
  };
  const pipeline = new PaperTradingPipeline({ enabled: true, ledger });
  const result = await pipeline.processOpenTrade({
    trade: {
      id: "open-2", status: "OPEN", openedAt: 1,
      symbol: "BTCUSDT", marketType: "SPOT", side: "LONG",
      quantity: "0.1", entryPrice: "100", stopLossPrice: "98",
      takeProfitPrice: "104", riskBudget: "1"
    },
    candles: [{
      timestampMillis: 60_000, open: 100, high: 101, low: 99,
      close: 100.5, isClosed: true
    }]
  });
  assert.equal(result.status, "POSITION_OPEN");
  assert.equal(closeCalls, 0);
});

test("batch open-position processing retrieves durable positions and closes simulated exits", async () => {
  const trade = {
    id: "batch-open-1", status: "OPEN", openedAt: 1,
    symbol: "BTCUSDT", marketType: "SPOT", side: "LONG",
    quantity: "0.1", entryPrice: "100", stopLossPrice: "98",
    takeProfitPrice: "104", riskBudget: "1"
  };
  const closed = [];
  let listCalls = 0;
  const ledger = {
    async openApprovedTrade() { return { created: true }; },
    async listOpenTrades() { listCalls++; return [trade]; },
    async closeTrade(input) {
      closed.push(input);
      return { closed: true, reason: null, trade: { ...trade, status: "CLOSED" } };
    }
  };
  const pipeline = new PaperTradingPipeline({ enabled: true, ledger });
  const result = await pipeline.processOpenPositions({
    marketDataByInstrument: {
      "BTCUSDT|SPOT": {
        candles: [{
          timestampMillis: 60_000, open: 100, high: 101, low: 97,
          close: 99, isClosed: true
        }]
      }
    },
    costsByInstrument: { "BTCUSDT|SPOT": { feeRate: 0.001 } },
    nowMillis: 120_000
  });
  assert.equal(listCalls, 1);
  assert.equal(result.status, "PROCESSED");
  assert.equal(result.processed, 1);
  assert.equal(result.results[0].status, "PAPER_TRADE_CLOSED");
  assert.equal(closed.length, 1);
  assert.equal(closed[0].tradeId, "batch-open-1");
});

test("batch open-position processing skips trades without matching market data", async () => {
  const ledger = {
    async openApprovedTrade() { return { created: true }; },
    async listOpenTrades() {
      return [{
        id: "missing-market-data", status: "OPEN",
        symbol: "ETHUSDT", marketType: "SPOT"
      }];
    },
    async closeTrade() { throw new Error("must not close without candles"); }
  };
  const pipeline = new PaperTradingPipeline({ enabled: true, ledger });
  const result = await pipeline.processOpenPositions();
  assert.equal(result.processed, 1);
  assert.equal(result.results[0].status, "SKIPPED");
  assert.equal(result.results[0].reason, "OPEN_POSITION_MARKET_DATA_UNAVAILABLE");
});
