import test from "node:test";
import assert from "node:assert/strict";
import { formatStrategyAlert } from "../../supabase/functions/visiontrade-scheduled-monitor/telegram-alert.mjs";

const validSignal = {
  symbol: "ETHUSDT",
  marketType: "USDT_PERPETUAL",
  action: "BUY",
  referencePrice: 2120.8,
  signalCandleTimestampMillis: Date.parse("2026-11-10T08:30:00Z"),
  reasons: ["Price tests confirmed 5m support.", "Closed 1m bullish engulfing confirmation."]
};
const validLevels = {
  accepted: true,
  entryPrice: 2120.8,
  stopLossPrice: 2108.5,
  takeProfitPrice: 2148.2,
  grossRiskReward: 2.23
};

test("formats a professional alert with validated entry, stop, target and risk/reward", () => {
  const message = formatStrategyAlert(validSignal, validLevels);
  assert.match(message, /CONFIRMED STRATEGY SETUP/);
  assert.match(message, /ETHUSDT · LONG/);
  assert.match(message, /Entry:/);
  assert.match(message, /Stop Loss:/);
  assert.match(message, /Take Profit:/);
  assert.match(message, /2\.23R/);
  assert.match(message, /5m structure · 1m execution/);
  assert.match(message, /Live trading is disabled/);
  assert.doesNotMatch(message, /Candidate stop-loss/);
});

test("refuses to format a risk-rejected setup as a strategy signal", () => {
  assert.throws(
    () => formatStrategyAlert(validSignal, { ...validLevels, accepted: false }),
    /Rejected risk assessments/
  );
});

test("refuses missing or invalid price levels and reward ratio", () => {
  assert.throws(
    () => formatStrategyAlert(validSignal, { ...validLevels, stopLossPrice: null }),
    /valid stop-loss/
  );
  assert.throws(
    () => formatStrategyAlert(validSignal, { ...validLevels, grossRiskReward: 1.5 }),
    /at least 2\.00R/
  );
});

test("escapes dynamic text before sending HTML to Telegram", () => {
  const message = formatStrategyAlert(
    { ...validSignal, symbol: "ETH<USDT>", reasons: ["<script>alert(1)</script>"] },
    validLevels
  );
  assert.match(message, /ETH&lt;USDT&gt;/);
  assert.match(message, /&lt;script&gt;/);
  assert.doesNotMatch(message, /<script>/);
});
