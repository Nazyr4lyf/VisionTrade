import test from "node:test";
import assert from "node:assert/strict";
import { TelegramBot } from "../src/telegram/telegram-bot.mjs";

function response(result) {
  return { ok: true, json: async () => ({ ok: true, result }) };
}

test("Telegram bot requires token and allowed chat id", () => {
  assert.throws(() => new TelegramBot({ token: "", chatId: "123" }), /TELEGRAM_BOT_TOKEN/);
  assert.throws(() => new TelegramBot({ token: "token", chatId: "" }), /TELEGRAM_CHAT_ID/);
});

test("sendMessage posts only to configured chat", async () => {
  let request;
  const bot = new TelegramBot({
    token: "test-token",
    chatId: "12345",
    fetchImpl: async (url, options) => {
      request = { url, options };
      return response({ message_id: 1 });
    }
  });
  await bot.sendMessage("connected");
  assert.match(request.url, /sendMessage$/);
  assert.equal(JSON.parse(request.options.body).chat_id, "12345");
  assert.equal(JSON.parse(request.options.body).text, "connected");
});

test("only configured chat can issue commands and /status returns worker status", async () => {
  const sent = [];
  const bot = new TelegramBot({
    token: "test-token",
    chatId: "12345",
    fetchImpl: async (url, options) => {
      sent.push(JSON.parse(options.body));
      return response({ message_id: sent.length });
    }
  });
  await bot.handleUpdate({ message: { text: "/status", chat: { id: 999 } } }, () => ({ running: true }));
  assert.equal(sent.length, 0);

  await bot.handleUpdate({ message: { text: "/status", chat: { id: 12345 } } }, () => ({
    running: true, mode: "MARKET_DATA_ONLY", lastScanAt: "2026-11-08T12:00:00.000Z", lastScanPairs: 30
  }));
  assert.equal(sent.length, 1);
  assert.match(sent[0].text, /RUNNING/);
  assert.match(sent[0].text, /Live orders: DISABLED/);
});

test("pollOnce advances update offset", async () => {
  let requested;
  const bot = new TelegramBot({
    token: "test-token",
    chatId: "12345",
    fetchImpl: async (url, options) => {
      const body = JSON.parse(options.body);
      if (url.endsWith("/getUpdates")) requested = body;
      return response([{ update_id: 7, message: { text: "/help", chat: { id: 12345 } } }]);
    }
  });
  await bot.pollOnce(() => ({ running: true, mode: "MARKET_DATA_ONLY" }));
  assert.equal(requested.offset, 0);
  assert.equal(bot.offset, 8);
});

test("configured chat can request recent strategy signals", async () => {
  const sent = [];
  const bot = new TelegramBot({
    token: "test-token",
    chatId: "12345",
    fetchImpl: async (_url, options) => {
      sent.push(JSON.parse(options.body));
      return response({ message_id: sent.length });
    }
  });
  await bot.handleUpdate({ message: { text: "/signals", chat: { id: 12345 } } }, () => ({
    recentSignals: [{
      symbol: "BTCUSDT", marketType: "SPOT", action: "BUY",
      referencePrice: 65000, riskAccepted: true, grossRiskReward: 2.4,
      stopLossPrice: 64700, takeProfitPrice: 65700
    }]
  }));
  assert.equal(sent.length, 1);
  assert.match(sent[0].text, /BTCUSDT SPOT LONG/);
  assert.match(sent[0].text, /2.40R/);
  assert.match(sent[0].text, /SL 64700/);
  assert.match(sent[0].text, /TP 65700/);
  assert.match(sent[0].text, /no exchange orders/i);
});
