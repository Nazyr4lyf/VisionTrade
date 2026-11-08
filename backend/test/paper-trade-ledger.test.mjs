import test from "node:test";
import assert from "node:assert/strict";
import { PaperTradeLedger } from "../src/paper/paper-trade-ledger.mjs";

class MemoryPaperTradeStore {
  constructor() {
    this.trades = new Map();
    this.keys = new Map();
  }

  async createOpenTrade(trade) {
    const priorId = this.keys.get(trade.idempotencyKey);
    if (priorId) {
      return { created: false, reason: "DUPLICATE_IDEMPOTENCY_KEY", trade: this.trades.get(priorId) };
    }
    const active = [...this.trades.values()].find(item =>
      item.status === "OPEN" &&
      item.symbol === trade.symbol &&
      item.marketType === trade.marketType
    );
    if (active) return { created: false, reason: "OPEN_POSITION_EXISTS", trade: active };
    this.trades.set(trade.id, structuredClone(trade));
    this.keys.set(trade.idempotencyKey, trade.id);
    return { created: true, reason: null, trade: structuredClone(trade) };
  }

  async findById(id) {
    const trade = this.trades.get(id);
    return trade ? structuredClone(trade) : null;
  }

  async closeIfOpen(id, closure) {
    const trade = this.trades.get(id);
    if (!trade) return { closed: false, reason: "TRADE_NOT_FOUND", trade: null };
    if (trade.status !== "OPEN") {
      return { closed: false, reason: "ALREADY_CLOSED", trade: structuredClone(trade) };
    }
    Object.assign(trade, structuredClone(closure));
    return { closed: true, reason: null, trade: structuredClone(trade) };
  }
}

const approved = (overrides = {}) => ({
  approved: true,
  paperTradeCandidate: {
    symbol: "BTCUSDT",
    marketType: "SPOT",
    side: "LONG",
    entryPrice: 100,
    stopLossPrice: 98,
    takeProfitPrice: 104,
    quantity: 0.1,
    riskBudget: 1,
    estimatedLossAtStop: 0.9,
    netRiskReward: 2.1,
    ...overrides
  }
});

test("only approved candidates can enter the paper ledger", async () => {
  const ledger = new PaperTradeLedger({ store: new MemoryPaperTradeStore() });
  const result = await ledger.openApprovedTrade({
    approval: { approved: false },
    idempotencyKey: "signal-1",
    tradeId: "trade-1",
    openedAt: 1000
  });
  assert.equal(result.created, false);
  assert.equal(result.reason, "TRADE_NOT_APPROVED");
});

test("opens an approved paper trade with an explicit OPEN lifecycle", async () => {
  const ledger = new PaperTradeLedger({ store: new MemoryPaperTradeStore() });
  const result = await ledger.openApprovedTrade({
    approval: approved(),
    idempotencyKey: "signal-1",
    tradeId: "trade-1",
    openedAt: 1000
  });
  assert.equal(result.created, true);
  assert.equal(result.trade.status, "OPEN");
  assert.equal(result.trade.closedAt, null);
});

test("duplicate idempotency keys cannot create a second trade", async () => {
  const ledger = new PaperTradeLedger({ store: new MemoryPaperTradeStore() });
  const input = {
    approval: approved(),
    idempotencyKey: "signal-duplicate",
    tradeId: "trade-1",
    openedAt: 1000
  };
  assert.equal((await ledger.openApprovedTrade(input)).created, true);
  const duplicate = await ledger.openApprovedTrade({ ...input, tradeId: "trade-2" });
  assert.equal(duplicate.created, false);
  assert.equal(duplicate.reason, "DUPLICATE_IDEMPOTENCY_KEY");
});

test("one active position per instrument and side is enforced by the store", async () => {
  const ledger = new PaperTradeLedger({ store: new MemoryPaperTradeStore() });
  await ledger.openApprovedTrade({
    approval: approved(),
    idempotencyKey: "signal-a",
    tradeId: "trade-a",
    openedAt: 1000
  });
  const duplicate = await ledger.openApprovedTrade({
    approval: approved(),
    idempotencyKey: "signal-b",
    tradeId: "trade-b",
    openedAt: 1001
  });
  assert.equal(duplicate.created, false);
  assert.equal(duplicate.reason, "OPEN_POSITION_EXISTS");
});


test("ledger rejects a candidate below 2R or above its approved risk budget", async () => {
  const ledger = new PaperTradeLedger({ store: new MemoryPaperTradeStore() });
  await assert.rejects(
    ledger.openApprovedTrade({
      approval: approved({ netRiskReward: 1.99 }),
      idempotencyKey: "low-rr",
      tradeId: "low-rr",
      openedAt: 1000
    }),
    /at least 2.0 net R/
  );
  await assert.rejects(
    ledger.openApprovedTrade({
      approval: approved({ estimatedLossAtStop: 1.01 }),
      idempotencyKey: "over-budget",
      tradeId: "over-budget",
      openedAt: 1000
    }),
    /exceeds the approved risk budget/
  );
});


test("opposite-side signal cannot open a second simultaneous position on the same instrument", async () => {
  const ledger = new PaperTradeLedger({ store: new MemoryPaperTradeStore() });
  await ledger.openApprovedTrade({
    approval: approved({ marketType: "USDT_PERPETUAL" }),
    idempotencyKey: "long-perp",
    tradeId: "long-perp",
    openedAt: 1000
  });
  const opposite = await ledger.openApprovedTrade({
    approval: approved({ marketType: "USDT_PERPETUAL", side: "SHORT" }),
    idempotencyKey: "short-perp",
    tradeId: "short-perp",
    openedAt: 1001
  });
  assert.equal(opposite.created, false);
  assert.equal(opposite.reason, "OPEN_POSITION_EXISTS");
});

test("spot short candidate is rejected before reaching the store", async () => {
  const ledger = new PaperTradeLedger({ store: new MemoryPaperTradeStore() });
  await assert.rejects(
    ledger.openApprovedTrade({
      approval: approved({ side: "SHORT" }),
      idempotencyKey: "spot-short",
      tradeId: "trade-short",
      openedAt: 1000
    }),
    /unsupported instrument or position side/
  );
});

test("close is idempotent and preserves the simulated exit result", async () => {
  const ledger = new PaperTradeLedger({ store: new MemoryPaperTradeStore() });
  await ledger.openApprovedTrade({
    approval: approved(),
    idempotencyKey: "signal-close",
    tradeId: "trade-close",
    openedAt: 1000
  });
  const exit = { reason: "STOP_LOSS", fillPrice: 97.5, netPnl: -0.35, realizedR: -0.35 };
  const closed = await ledger.closeTrade({ tradeId: "trade-close", exit, closedAt: 2000 });
  assert.equal(closed.closed, true);
  assert.equal(closed.trade.status, "CLOSED");
  assert.equal(closed.trade.exit.fillPrice, 97.5);

  const repeated = await ledger.closeTrade({ tradeId: "trade-close", exit, closedAt: 3000 });
  assert.equal(repeated.closed, false);
  assert.equal(repeated.reason, "ALREADY_CLOSED");
  assert.equal(repeated.trade.closedAt, 2000);
});

test("unknown trade cannot be closed and invalid exit is rejected", async () => {
  const ledger = new PaperTradeLedger({ store: new MemoryPaperTradeStore() });
  const missing = await ledger.closeTrade({
    tradeId: "missing",
    exit: { reason: "TAKE_PROFIT", fillPrice: 104, netPnl: 0.3, realizedR: 0.3 },
    closedAt: 2000
  });
  assert.equal(missing.reason, "TRADE_NOT_FOUND");
  await assert.rejects(
    ledger.closeTrade({
      tradeId: "missing",
      exit: { reason: "MANUAL", fillPrice: 104, netPnl: 0, realizedR: 0 },
      closedAt: 2000
    }),
    /valid simulated exit/
  );
});
