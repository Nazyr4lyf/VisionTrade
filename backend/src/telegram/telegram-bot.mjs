const API_BASE = "https://api.telegram.org";

export class TelegramBot {
  constructor({
    token = process.env.TELEGRAM_BOT_TOKEN,
    chatId = process.env.TELEGRAM_CHAT_ID,
    fetchImpl = fetch,
    pollTimeoutSeconds = 20,
    requestTimeoutMs = 30_000
  } = {}) {
    if (typeof token !== "string" || !token.trim()) throw new Error("TELEGRAM_BOT_TOKEN is required");
    if (chatId === undefined || chatId === null || !String(chatId).trim()) throw new Error("TELEGRAM_CHAT_ID is required");
    if (!Number.isInteger(pollTimeoutSeconds) || pollTimeoutSeconds < 1 || pollTimeoutSeconds > 50) {
      throw new Error("Telegram poll timeout must be between 1 and 50 seconds");
    }
    if (!Number.isFinite(requestTimeoutMs) || requestTimeoutMs <= pollTimeoutSeconds * 1000) {
      throw new Error("Telegram request timeout must exceed the long-poll timeout");
    }
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
      const response = await this.fetchImpl(API_BASE + "/bot" + this.token + "/" + method, {
        method: "POST",
        headers: { "content-type": "application/json" },
        body: JSON.stringify(payload),
        signal: controller.signal
      });
      const body = await response.json();
      if (!response.ok || body.ok !== true) throw new Error("Telegram API request failed (" + method + ")");
      return body.result;
    } catch (error) {
      if (error.name === "AbortError") throw new Error("Telegram API request timed out (" + method + ")");
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
      chat_id: this.chatId, text, disable_web_page_preview: true
    });
  }

  async handleUpdate(update, getStatus) {
    const message = update?.message;
    if (!message?.text || !message.chat || String(message.chat.id) !== this.chatId) return;
    const command = message.text.trim().split(/\s+/, 1)[0].split("@")[0].toLowerCase();
    if (command === "/start" || command === "/help") {
      await this.sendMessage(
        "NAXYUS VISIONTRADE\nBot connection is active.\n\nCommands:\n/status - worker and monitoring status\n/signals - latest strategy signals\n/help - show commands"
      );
    } else if (command === "/signals") {
      const signals = getStatus().recentSignals ?? [];
      const lines = signals.length
        ? signals.map(item => {
            const side = item.action === "BUY" ? "LONG" : "SHORT";
            const rr = item.riskAccepted && Number.isFinite(item.grossRiskReward)
              ? " | gross RR " + item.grossRiskReward.toFixed(2) + "R"
              : " | stop/target rejected";
            return item.symbol + " " + item.marketType + " " + side + " @ " + item.referencePrice + rr;
          }).join("\n")
        : "No actionable strategy signals in the latest scans.";
      await this.sendMessage("LATEST VISIONTRADE SIGNALS\n" + lines + "\n\nSignal only — no exchange orders.");
    } else if (command === "/status") {
      const status = getStatus();
      await this.sendMessage(
        "VISIONTRADE STATUS\n" +
        "Worker: " + (status.running ? "RUNNING" : "STOPPED") + "\n" +
        "Mode: " + status.mode + "\n" +
        "Last scan: " + (status.lastScanAt ?? "not yet completed") + "\n" +
        "Market pairs scanned: " + (status.lastScanPairs ?? 0) + "\n" +
        "Actionable signals: " + (status.actionableSignals ?? 0) + "\n" +
        "Live orders: DISABLED"
      );
    }
  }

  async pollOnce(getStatus) {
    const updates = await this.call("getUpdates", {
      offset: this.offset, timeout: this.pollTimeoutSeconds, allowed_updates: ["message"]
    }, this.requestTimeoutMs);
    for (const update of updates) {
      if (!Number.isSafeInteger(update.update_id)) continue;
      await this.handleUpdate(update, getStatus);
      this.offset = Math.max(this.offset, update.update_id + 1);
    }
  }

  async start(getStatus, { onError = () => {} } = {}) {
    this.stopped = false;
    while (!this.stopped) {
      try {
        await this.pollOnce(getStatus);
      } catch (error) {
        onError(error);
        if (!this.stopped) await new Promise(resolve => setTimeout(resolve, 3000));
      }
    }
  }

  stop() { this.stopped = true; }
}

export function telegramConfigFromEnv(env = process.env) {
  return { enabled: Boolean(env.TELEGRAM_BOT_TOKEN && env.TELEGRAM_CHAT_ID) };
}
