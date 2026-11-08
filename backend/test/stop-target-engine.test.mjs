import test from "node:test";
import assert from "node:assert/strict";
import { StopTargetEngine } from "../src/risk/stop-target-engine.mjs";

const engine = new StopTargetEngine();
const base = {
  entryPrice: 100, atr: 1, priceTick: 0.01,
  supportZone: { low: 99, high: 99.2, touches: 2, lastConfirmedAtMillis: 1000 },
  resistanceZone: { low: 103, high: 103.2, touches: 2, lastConfirmedAtMillis: 2000 }
};

test("builds a conservative BUY stop below confirmed support and target at resistance", () => {
  const result = engine.build({ ...base, signal: { action: "BUY" } });
  assert.equal(result.accepted, true, result.rejectionReason);
  assert.ok(result.stopLossPrice < base.supportZone.low);
  assert.ok(result.takeProfitPrice <= base.resistanceZone.low);
  assert.ok(result.grossRiskReward >= 2);
  assert.equal(Math.round(result.stopLossPrice / base.priceTick), result.stopLossPrice / base.priceTick);
  assert.equal(Math.round(result.takeProfitPrice / base.priceTick), result.takeProfitPrice / base.priceTick);
});

test("builds a conservative SELL stop above resistance and target at support", () => {
  const result = engine.build({
    ...base, entryPrice: 102,
    signal: { action: "SELL" }
  });
  assert.equal(result.accepted, true, result.rejectionReason);
  assert.ok(result.stopLossPrice > base.resistanceZone.high);
  assert.ok(result.takeProfitPrice >= base.supportZone.high);
  assert.ok(result.grossRiskReward >= 2);
});

test("rejects NO_TRADE and missing confirmed zones", () => {
  const noTrade = engine.build({ ...base, signal: { action: "NO_TRADE" } });
  assert.equal(noTrade.accepted, false);
  assert.match(noTrade.rejectionReason, /BUY or SELL/);

  const missing = engine.build({
    ...base, signal: { action: "BUY" }, supportZone: null
  });
  assert.equal(missing.accepted, false);
  assert.match(missing.rejectionReason, /Support zone is required/);
});

test("rejects overlapping zones and entry outside the range", () => {
  const overlap = engine.build({
    ...base, signal: { action: "BUY" },
    resistanceZone: { low: 99.1, high: 100.2 }
  });
  assert.equal(overlap.accepted, false);
  assert.match(overlap.rejectionReason, /overlap/);

  const outside = engine.build({
    ...base, signal: { action: "BUY" }, entryPrice: 104
  });
  assert.equal(outside.accepted, false);
  assert.match(outside.rejectionReason, /outside/);
});

test("rejects a target zone that cannot provide gross 2R", () => {
  const result = engine.build({
    ...base, signal: { action: "BUY" },
    resistanceZone: { low: 101, high: 101.2 }
  });
  assert.equal(result.accepted, false);
  assert.match(result.rejectionReason, /gross 2.0R/);
});

test("rejects excessively wide ATR stops and invalid configuration", () => {
  const wide = engine.build({
    ...base, signal: { action: "BUY" }, supportZone: { low: 96, high: 96.2 }
  });
  assert.equal(wide.accepted, false);
  assert.match(wide.rejectionReason, /maximum ATR/);
  assert.throws(() => new StopTargetEngine({ maximumStopAtr: 1, minimumStopAtr: 2 }), /at least/);
});
