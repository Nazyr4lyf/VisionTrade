import test from "node:test";
import assert from "node:assert/strict";
import { BybitClient } from "../src/providers/bybit-client.mjs";

function response(body, status = 200) {
  return {
    ok: status >= 200 && status < 300,
    status,
    async json() { return body; }
  };
}

test("rejects unsupported market categories and intervals", async () => {
  const client = new BybitClient({ fetchImpl: async () => response({ retCode: 0, result: {} }) });
  await assert.rejects(() => client.getInstruments("option"), /Only Bybit spot and linear/);
  await assert.rejects(() => client.getKlines("spot", "BTCUSDT", "15"), /Only 1m and 5m/);
  await assert.rejects(() => client.getKlines("spot", "BTCUSD", "1"), /USDT-quoted/);
});

test("parses and chronologically sorts candles, marking current candle unclosed", async () => {
  const now = Date.now();
  const fetchImpl = async () => response({
    retCode: 0,
    result: {
      list: [
        [String(now), "101", "103", "100", "102", "10", "1020"],
        [String(now - 60_000), "99", "102", "98", "101", "12", "1200"]
      ]
    }
  });
  const client = new BybitClient({ fetchImpl, baseUrl: "https://example.test" });
  const candles = await client.getKlines("spot", "BTCUSDT", "1");
  assert.equal(candles.length, 2);
  assert.ok(candles[0].timestampMillis < candles[1].timestampMillis);
  assert.equal(candles[1].isClosed, false);
  assert.equal(candles[0].timeframeMinutes, 1);
});

test("rejects non-success Bybit response envelopes", async () => {
  const client = new BybitClient({
    fetchImpl: async () => response({ retCode: 10001, retMsg: "invalid parameter" }),
    baseUrl: "https://example.test"
  });
  await assert.rejects(() => client.getTickers("spot"), /invalid parameter/);
});
