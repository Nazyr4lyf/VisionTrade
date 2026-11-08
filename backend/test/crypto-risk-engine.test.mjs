import test from "node:test";
import assert from "node:assert/strict";
import { CryptoRiskEngine } from "../src/risk/crypto-risk-engine.mjs";

const engine = new CryptoRiskEngine();
const costs = {
  entryFeeRate: 0.0005, exitFeeRate: 0.0005,
  slippageRatePerSide: 0.0002, fundingRateAllowance: 0.0001
};
const spot = {
  symbol: "BTCUSDT", marketType: "SPOT", priceTick: 0.01,
  quantityStep: 0.001, minimumQuantity: 0.001, minimumNotional: 5,
  contractMultiplier: 1
};
const perpetual = { ...spot, marketType: "USDT_PERPETUAL" };
const base = {
  equity: 1000, entryPrice: 100, stopLossPrice: 99,
  takeProfitPrice: 103, instrument: spot, costs
};

test("sizes a Spot long within the 1% risk budget and net 2R floor", () => {
  const result = engine.calculate({ ...base, side: "LONG" });
  assert.equal(result.accepted, true, result.rejectionReason);
  assert.equal(result.riskBudget, 10);
  assert.ok(result.estimatedLossAtStop <= result.riskBudget);
  assert.ok(result.netRiskReward >= 2);
  assert.equal(Math.round(result.quantity / spot.quantityStep), result.quantity / spot.quantityStep);
});

test("rejects a setup below 2R after estimated costs", () => {
  const result = engine.calculate({ ...base, takeProfitPrice: 102, side: "LONG" });
  assert.equal(result.accepted, false);
  assert.ok(result.netRiskReward < 2);
});

test("Spot cannot open a naked short", () => {
  const result = engine.calculate({
    ...base, side: "SHORT", stopLossPrice: 101, takeProfitPrice: 97
  });
  assert.equal(result.accepted, false);
  assert.match(result.rejectionReason, /Spot/);
});

test("Perpetual short is evaluated with short-side stop and target", () => {
  const result = engine.calculate({
    ...base, instrument: perpetual, side: "SHORT",
    stopLossPrice: 101, takeProfitPrice: 97
  });
  assert.equal(result.accepted, true, result.rejectionReason);
});

test("rejects prices outside exchange tick size", () => {
  const result = engine.calculate({
    ...base, entryPrice: 100.005, side: "LONG"
  });
  assert.equal(result.accepted, false);
  assert.match(result.rejectionReason, /tick size/);
});

test("signal gate rejects NO_TRADE and instrument mismatch", () => {
  const noTrade = engine.evaluateSignal({
    signal: { symbol: "BTCUSDT", marketType: "SPOT", action: "NO_TRADE" },
    ...base
  });
  assert.equal(noTrade.accepted, false);
  assert.match(noTrade.rejectionReason, /Only BUY or SELL/);

  const mismatch = engine.evaluateSignal({
    signal: { symbol: "ETHUSDT", marketType: "SPOT", action: "BUY" },
    ...base
  });
  assert.equal(mismatch.accepted, false);
  assert.match(mismatch.rejectionReason, /do not match/);
});

test("signal gate maps BUY and SELL into the correct position side", () => {
  const buy = engine.evaluateSignal({
    signal: { symbol: "BTCUSDT", marketType: "SPOT", action: "BUY" },
    ...base
  });
  assert.equal(buy.accepted, true, buy.rejectionReason);

  const sell = engine.evaluateSignal({
    signal: { symbol: "BTCUSDT", marketType: "SPOT", action: "SELL" },
    ...base, stopLossPrice: 101, takeProfitPrice: 97
  });
  assert.equal(sell.accepted, false);
  assert.match(sell.rejectionReason, /Spot/);
});
