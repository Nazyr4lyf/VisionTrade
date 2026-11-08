import test from "node:test";
import assert from "node:assert/strict";
import { PaperTradeApprovalEngine } from "../src/risk/paper-trade-approval-engine.mjs";

const engine = new PaperTradeApprovalEngine();
const instrument = {
  symbol: "BTCUSDT", marketType: "SPOT", priceTick: 0.01,
  quantityStep: 0.001, minimumQuantity: 0.001,
  minimumNotional: 5, contractMultiplier: 1
};
const longSignal = {
  symbol: "BTCUSDT", marketType: "SPOT", action: "BUY",
  referencePrice: 100,
  supportZone: { low: 99, high: 99.2 },
  resistanceZone: { low: 103, high: 103.2 }
};
const costs = {
  entryFeeRate: 0.00055, exitFeeRate: 0.00055,
  slippageRatePerSide: 0.0002, fundingRateAllowance: 0
};

test("approves a Spot BUY only after levels and net risk pass", () => {
  const result = engine.evaluate({ signal: longSignal, atr: 1, instrument, equity: 10_000, costs });
  assert.equal(result.approved, true, result.rejectionReason);
  assert.equal(result.paperTradeCandidate.side, "LONG");
  assert.ok(result.paperTradeCandidate.estimatedLossAtStop <= 100);
  assert.ok(result.paperTradeCandidate.netRiskReward >= 2);
  assert.ok(result.paperTradeCandidate.quantity >= instrument.minimumQuantity);
});

test("approves a Perpetual SELL with a short-side risk calculation", () => {
  const perpetual = { ...instrument, marketType: "USDT_PERPETUAL" };
  const signal = {
    ...longSignal, marketType: "USDT_PERPETUAL", action: "SELL",
    referencePrice: 102,
    supportZone: { low: 98.5, high: 98.8 }
  };
  const result = engine.evaluate({ signal, atr: 1, instrument: perpetual, equity: 10_000, costs });
  assert.equal(result.approved, true, result.rejectionReason);
  assert.equal(result.paperTradeCandidate.side, "SHORT");
  assert.ok(result.paperTradeCandidate.netRiskReward >= 2);
});

test("rejects Spot SELL rather than allowing a naked short", () => {
  const signal = { ...longSignal, action: "SELL", referencePrice: 102 };
  const result = engine.evaluate({ signal, atr: 1, instrument, equity: 10_000, costs });
  assert.equal(result.approved, false);
  assert.match(result.rejectionReason, /Spot risk calculation cannot open a naked short/);
});

test("rejects NO_TRADE and mismatched exchange instruments", () => {
  const noTrade = engine.evaluate({
    signal: { ...longSignal, action: "NO_TRADE" }, atr: 1, instrument, equity: 10_000
  });
  assert.equal(noTrade.approved, false);
  assert.match(noTrade.rejectionReason, /Only actionable/);
  const mismatch = engine.evaluate({
    signal: longSignal, atr: 1,
    instrument: { ...instrument, symbol: "ETHUSDT" }, equity: 10_000
  });
  assert.equal(mismatch.approved, false);
  assert.match(mismatch.rejectionReason, /do not match/);
});

test("rejects candidate if fees and slippage reduce net reward below 2R", () => {
  const result = engine.evaluate({
    signal: longSignal, atr: 1, instrument, equity: 10_000,
    costs: {
      entryFeeRate: 0.005, exitFeeRate: 0.005,
      slippageRatePerSide: 0.002, fundingRateAllowance: 0
    }
  });
  assert.equal(result.approved, false);
  assert.match(result.rejectionReason, /Net risk\/reward is below|consume the entire target reward/);
});
