const TELEGRAM_API_BASE = "https://api.telegram.org";

export class TelegramNotifier {
  constructor({ botToken, chatId, fetchImpl = fetch, timeoutMs = 10_000 }) {
    if (typeof botToken !== "string" || botToken.trim() === "") {
      throw new Error("Telegram bot token is required");
    }
    if (typeof chatId !== "string" || chatId.trim() === "") {
      throw new Error("Telegram chat ID is required");
    }
    if (!Number.isFinite(timeoutMs) || timeoutMs < 1) {
      throw new Error("Telegram timeout must be a positive number");
    }
    this.botToken = botToken;
    this.chatId = chatId;
    this.fetchImpl = fetchImpl;
    this.timeoutMs = timeoutMs;
  }

  async sendMessage(text) {
    if (typeof text !== "string" || text.trim() === "" || text.length > 4096) {
      throw new Error("Telegram message must contain 1–4096 characters");
    }

    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), this.timeoutMs);
    try {
      const response = await this.fetchImpl(
        `${TELEGRAM_API_BASE}/bot${this.botToken}/sendMessage`,
        {
          method: "POST",
          headers: { "content-type": "application/json" },
          body: JSON.stringify({
            chat_id: this.chatId,
            text,
            disable_web_page_preview: true
          }),
          signal: controller.signal
        }
      );
      let payload;
      try {
        payload = await response.json();
      } catch {
        throw new Error("Telegram returned an unreadable response");
      }
      if (!response.ok || payload?.ok !== true) {
        throw new Error(`Telegram sendMessage failed (HTTP ${response.status})`);
      }
      return { messageId: payload.result?.message_id ?? null };
    } finally {
      clearTimeout(timeout);
    }
  }
}
