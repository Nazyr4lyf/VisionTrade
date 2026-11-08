const API_BASE = "https://api.telegram.org";

function safeInteger(value, name) {
  const parsed = Number(value);
  if (!Number.isSafeInteger(parsed) || parsed <= 0) throw new Error(`${name} must be a positive integer`);
  return parsed;
}

export class TelegramBot {
  constructor({
    token = process.env.TELEGRAM_BOT_TOKEN,
    chatId = process.env.TELEGRAM_CHAT_ID,
    fetchImpl = fetch,
    pollTimeoutSeconds = 20,
    requestTimeoutMs = 30_000
  } = {}) {
    if (!token || !token.trim()) throw new Error("TELEGRAM_BOT_TOKEN is required");
    if (!chatId || !String(chatId).trim()) throw new Error("TELEGRAM_CHAT_ID is required");
    this.token = token.trim();
    this.chatId = String(chatId).trim();
    this.fetchImpl = fetchImpl;
    this.pollTimeoutSeconds = pollTimeoutSeconds;
    this.requestTimeoutMs = requestTimeoutMs;
    this.offset = 0;
    this.stopped = false;
  }

  async call(method, payload = {}, timeoutMs = this.requestTimeoutMs) {
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), timeoutMs);
    try {
      const response = await this.fetchImpl(`${API_BASE}/bot${this.token}/${method}`, {
        method: "POST",
        headers: { "content-type": "application/json" },
        body: JSON.stringify(payload),
        signal: controller.signal
      });
      const body = await response.json();
      if (!response.ok || body.ok !== true) {
        throw new Error(`Telegram API request failed (${method})`);
      }
      return body.result;
    } catch (error) {
      if (error.name === "AbortError") throw new Error(`Telegram API request timed out (${method})`);
      throw error;
    } finally {
      clearTimeout(timer);
    }
  }

  async sendMessage(text) {
    if (typeof text !== "string" || !text.trim() || text.length > 4096) {
      throw new Error("Telegram message must contain 1 to 4096 characters");
    }
    return this.call("sendMessage", {
      chat_id: this.chatId,
      text,
      disable_web_page_preview: true
    });
  }

  async handleUpdate(update, getStatus) {
    const message = update?.message;
    if (!message?.text || !message.chat || String(message.chat.id) !== this.chatId) return;
    const command = message.text.trim().split(/\\s+/, 1)[0].split("@")[0].toLowerCase();
    if (command === "/start" || command === "/help") {
      await this.sendMessage(
        "NAXYUS VISIONTRADE\\nBot connection is active.\\n\\nCommands:\\n/status - worker and monitoring status\\n/help - show commands"
      );
    } else if (command === "/status") {
      const status = getStatus();
      await this.sendMessage(
        "VISIONTRADE STATUS\\n" +
        `Worker: ${status.running ? "RUNNING" : "STOPPED"}\\n` +
        `Mode: ${status.mode}\\n` +
        `Last scan: ${status.lastScanAt ?? "not yet completed"}\\n` +
        `Market pairs scanned: ${status.lastScanPairs ?? 0}\\n` +
        "Live orders: DISABLED"
      );
    }
  }

  async pollOnce(getStatus) {
    const updates = await this.call("getUpdates", {
      offset: this.offset,
      timeout: this.pollTimeoutSeconds,
      allowed_updates: ["message"]
    }, (this.pollTimeoutSeconds + 10) * 1000);
    for (const update of updates) {
      this.offset = Math.max(this.offset, update.update_id + 1);
      await this.handleUpdate(update, getStatus);
    }
  }

  async start(getStatus, { onError = () => {} } = {}) {
    this.stopped = false;
    while (!this.stopped) {
      try {
        await this.pollOnce(getStatus);
      } catch (error) {
        onError(error);
        await new Promise(resolve => setTimeout(resolve, 3000));
      }
    }
  }

  stop() {
    this.stopped = true;
  }
}

export function telegramConfigFromEnv(env = process.env) {
  const enabled = Boolean(env.TELEGRAM_BOT_TOKEN && env.TELEGRAM_CHAT_ID);
  return { enabled };
}
