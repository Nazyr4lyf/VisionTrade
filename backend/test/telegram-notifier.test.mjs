import test from "node:test";
import assert from "node:assert/strict";
import { TelegramNotifier } from "../src/notifications/telegram-notifier.mjs";

test("sends a message to the configured chat without logging credentials", async () => {
  let request;
  const notifier = new TelegramNotifier({
    botToken: "123456:TEST_TOKEN",
    chatId: "987654",
    fetchImpl: async (url, options) => {
      request = { url, options };
      return { ok: true, status: 200, json: async () => ({ ok: true, result: { message_id: 42 } }) };
    }
  });
  const result = await notifier.sendMessage("VisionTrade scanner is online");
  assert.equal(result.messageId, 42);
  assert.match(request.url, /sendMessage$/);
  assert.equal(request.options.method, "POST");
  assert.deepEqual(JSON.parse(request.options.body), {
    chat_id: "987654",
    text: "VisionTrade scanner is online",
    disable_web_page_preview: true
  });
});

test("requires bot token and chat ID", () => {
  assert.throws(() => new TelegramNotifier({ chatId: "1" }), /bot token is required/);
  assert.throws(() => new TelegramNotifier({ botToken: "token" }), /chat ID is required/);
});

test("rejects empty and overlong messages", async () => {
  const notifier = new TelegramNotifier({
    botToken: "token",
    chatId: "1",
    fetchImpl: async () => { throw new Error("must not send"); }
  });
  await assert.rejects(() => notifier.sendMessage(" "), /1–4096 characters/);
  await assert.rejects(() => notifier.sendMessage("x".repeat(4097)), /1–4096 characters/);
});

test("does not accept unsuccessful Telegram API responses", async () => {
  const notifier = new TelegramNotifier({
    botToken: "token",
    chatId: "1",
    fetchImpl: async () => ({
      ok: true, status: 200, json: async () => ({ ok: false, description: "denied" })
    })
  });
  await assert.rejects(() => notifier.sendMessage("test"), /Telegram sendMessage failed/);
});
