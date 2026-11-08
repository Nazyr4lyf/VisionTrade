import test from "node:test";
import assert from "node:assert/strict";
import { formatStrategyAlert, SignalAlertDeduplicator } from "../src/strategy/signal-alerts.mjs";

const signal = {
  symbol: "BTCUSDT", marketType: "SPOT", action: "BUY", referencePrice: 65000,
  supportZone: { low: 64800, high: 64900 },
  reasons: ["Support confirmed.", "Bullish 1m close."], evaluatedAtMillis: 1800000000000
};

test("formats an actionable strategy alert with explicit no-order status", () => {
  const message = formatStrategyAlert(signal);
  assert.match(message, /BTCUSDT \| SPOT \| LONG/);
  assert.match(message, /Reference price: 65000/);
  assert.match(message, /SIGNAL ONLY/);
  assert.match(message, /Live trading is disabled/);
});

test("rejects NO_TRADE and invalid prices", () => {
  assert.throws(() => formatStrategyAlert({ ...signal, action: "NO_TRADE" }), /Only actionable/);
  assert.throws(() => formatStrategyAlert({ ...signal, referencePrice: 0 }), /invalid/);
});

test("deduplicates the same symbol, market, action and evaluated candle", () => {
  const dedupe = new SignalAlertDeduplicator();
  assert.equal(dedupe.hasSent(signal), false);
  dedupe.markSent(signal);
  assert.equal(dedupe.hasSent(signal), true);
  assert.equal(dedupe.hasSent({ ...signal, evaluatedAtMillis: signal.evaluatedAtMillis + 60_000 }), false);
  assert.equal(dedupe.hasSent({ ...signal, marketType: "USDT_PERPETUAL" }), false);
  assert.equal(dedupe.hasSent({ ...signal, action: "NO_TRADE" }), false);
});
