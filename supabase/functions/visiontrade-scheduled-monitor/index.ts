import { ScalpingStrategyEngine } from "./strategy/scalping-strategy.mjs";
import { StopTargetEngine } from "./risk/stop-target-engine.mjs";
import { calculateIndicators } from "./strategy/indicators.mjs";
import { formatStrategyAlert } from "./telegram-alert.mjs";

const cors = { "Access-Control-Allow-Origin": "*", "Access-Control-Allow-Headers": "authorization, apikey, content-type", "Access-Control-Allow-Methods": "POST, OPTIONS" };
const json = (body, status=200) => new Response(JSON.stringify(body), { status, headers: { ...cors, "Content-Type": "application/json" } });
const base = "https://api.bybit.com";
const strategy = new ScalpingStrategyEngine();
const riskEngine = new StopTargetEngine();
const getEnv = (key) => Deno.env.get(key) ?? "";
const supaUrl = getEnv("SUPABASE_URL").replace(/\/+$/, "");
const supaKey = getEnv("SUPABASE_SERVICE_ROLE_KEY") || (() => { try { return JSON.parse(getEnv("SUPABASE_SECRET_KEYS")).default || ""; } catch { return ""; } })();
const MAX_MARKET_PAIRS = 30;
const MIN_TURNOVER_USD = 5_000_000;
const SCAN_CONCURRENCY = 3;
const MAX_CANDLE_AGE = { "1": 120_000, "5": 360_000 };

async function bybit(path) {
  const res = await fetch(base + path, { headers: { accept: "application/json" } });
  if (!res.ok) throw new Error("Bybit HTTP " + res.status);
  const data = await res.json();
  if (data.retCode !== 0) throw new Error("Bybit API: " + (data.retMsg || "request failed"));
  return data.result;
}
async function marketSet(category) {
  const instruments = [];
  let cursor = null;
  let pageCount = 0;
  do {
    const query = new URLSearchParams({ category, limit: "1000" });
    if (cursor) query.set("cursor", cursor);
    const page = await bybit("/v5/market/instruments-info?" + query.toString());
    instruments.push(...(page.list || []));
    cursor = page.nextPageCursor || null;
    pageCount++;
    if (pageCount >= 20) throw new Error("Bybit instrument discovery exceeded the 20-page safety limit");
  } while (cursor);
  const tickers = await bybit("/v5/market/tickers?category=" + category);
  const volumes = new Map((tickers.list || []).map(x => [x.symbol, Number(x.turnover24h)]));
  return instruments
    .filter(x => x.quoteCoin === "USDT" && x.status === "Trading" && (category === "spot" || x.settleCoin === "USDT"))
    .map(x => ({ symbol: x.symbol, marketType: category === "spot" ? "SPOT" : "USDT_PERPETUAL", category, turnover: volumes.get(x.symbol) || 0, priceTick: Number(x.priceFilter?.tickSize) }))
    .filter(x => x.turnover >= MIN_TURNOVER_USD && Number.isFinite(x.priceTick) && x.priceTick > 0)
    .sort((a,b) => b.turnover - a.turnover);
}
async function candles(item, interval) {
  const result = await bybit("/v5/market/kline?category=" + item.category + "&symbol=" + item.symbol + "&interval=" + interval + "&limit=120");
  const duration = Number(interval) * 60_000;
  return (result.list || []).map(row => ({
    timestampMillis: Number(row[0]), open: Number(row[1]), high: Number(row[2]), low: Number(row[3]),
    close: Number(row[4]), volume: Number(row[5]), timeframeMinutes: Number(interval),
    isClosed: Number(row[0]) + duration <= Date.now()
  })).sort((a,b) => a.timestampMillis - b.timestampMillis);
}
async function mapWithConcurrency(items, limit, mapper) {
  const output = new Array(items.length);
  let cursor = 0;
  const workers = Array.from({ length: Math.min(limit, items.length) }, async () => {
    while (true) {
      const index = cursor++;
      if (index >= items.length) return;
      try { output[index] = { status: "fulfilled", value: await mapper(items[index]) }; }
      catch (reason) { output[index] = { status: "rejected", reason }; }
    }
  });
  await Promise.all(workers);
  return output;
}

async function telegramSend(message) {
  const token = getEnv("TELEGRAM_BOT_TOKEN");
  const chat = getEnv("TELEGRAM_CHAT_ID");
  if (!token || !chat) return { enabled: false, sent: false };
  const res = await fetch("https://api.telegram.org/bot" + token + "/sendMessage", {
    method: "POST", headers: { "content-type": "application/json" },
    body: JSON.stringify({ chat_id: chat, text: message, parse_mode: "HTML", disable_web_page_preview: true })
  });
  const body = await res.json();
  if (!res.ok || body.ok !== true) throw new Error("Telegram sendMessage failed (HTTP " + res.status + ")");
  return { enabled: true, sent: true, messageId: body.result?.message_id ?? null };
}
async function rest(path, init={}) {
  const res = await fetch(supaUrl + "/rest/v1/" + path, {
    ...init, headers: { apikey: supaKey, authorization: "Bearer " + supaKey, "content-type": "application/json", ...(init.headers || {}) }
  });
  const body = await res.text();
  if (!res.ok) throw new Error("Supabase REST " + res.status + ": " + body.slice(0,300));
  return body ? JSON.parse(body) : null;
}
function encodeFilter(value) { return encodeURIComponent(value); }
async function deliverPendingAlerts(telegramEnabled) {
  if (!telegramEnabled) return { sent: 0, failed: 0 };
  const pending = await rest("visiontrade_signal_events?select=id,market_type,symbol,action,signal_payload,telegram_attempts&telegram_status=in.(pending,failed)&telegram_next_attempt_at=lte." + encodeFilter(new Date().toISOString()) + "&order=created_at.asc&limit=20");
  let sent = 0, failed = 0;
  for (const event of pending || []) {
    try {
      const payload = event.signal_payload || {};
      const levels = payload.riskLevels || null;
      if (!levels?.accepted) {
        const now = new Date().toISOString();
        await rest("visiontrade_signal_events?id=eq." + event.id, {
          method: "PATCH", headers: { prefer: "return=minimal" },
          body: JSON.stringify({ telegram_status: "rejected", telegram_last_error: payload.riskReason || levels?.rejectionReason || "Risk validation was not approved; alert suppressed.", updated_at: now })
        });
        continue;
      }
      const message = formatStrategyAlert({
        symbol: event.symbol, marketType: event.market_type, action: event.action,
        referencePrice: payload.referencePrice, reasons: payload.reasons || [],
        signalCandleTimestampMillis: payload.evaluatedAtMillis
      }, levels);
      const result = await telegramSend(message);
      const now = new Date().toISOString();
      await rest("visiontrade_signal_events?id=eq." + event.id, {
        method: "PATCH", headers: { prefer: "return=minimal" },
        body: JSON.stringify({ telegram_status: "sent", telegram_attempts: event.telegram_attempts + 1,
          telegram_last_error: null, telegram_message_id: result.messageId, telegram_sent_at: now, updated_at: now })
      });
      sent++;
    } catch (error) {
      const attempts = event.telegram_attempts + 1;
      const backoffMinutes = Math.min(60, 2 ** Math.min(attempts, 6));
      const next = new Date(Date.now() + backoffMinutes * 60_000).toISOString();
      await rest("visiontrade_signal_events?id=eq." + event.id, {
        method: "PATCH", headers: { prefer: "return=minimal" },
        body: JSON.stringify({ telegram_status: "failed", telegram_attempts: attempts,
          telegram_last_error: String(error?.message || error).slice(0,500),
          telegram_next_attempt_at: next, updated_at: new Date().toISOString() })
      });
      failed++;
      console.error("VisionTrade Telegram delivery failed for event " + event.id + ":", error?.message || error);
    }
  }
  return { sent, failed };
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: cors });
  if (req.method !== "POST") return json({ error: "POST required" }, 405);
  if (!supaUrl || !supaKey) return json({ error: "Supabase server credentials are unavailable" }, 500);
  try {
    const prior = await rest("visiontrade_monitor_snapshot?select=updated_at,telegram_connected_at&id=eq.latest");
    if (prior?.[0]?.updated_at && Date.now() - Date.parse(prior[0].updated_at) < 240_000) {
      return json({ ok: true, skipped: true, reason: "A scan completed within the last four minutes." });
    }

    const [spot, linear] = await Promise.all([marketSet("spot"), marketSet("linear")]);
    const discovered = [...spot, ...linear].sort((a,b) => b.turnover - a.turnover);
    const sample = discovered.slice(0, MAX_MARKET_PAIRS);
    const nowMillis = Date.now();
    const settled = await mapWithConcurrency(sample, SCAN_CONCURRENCY, async item => {
      const [one, five] = await Promise.all([candles(item, "1"), candles(item, "5")]);
      const receivedAt = Date.now();
      const closedOne = one.filter(c => c.isClosed);
      const closedFive = five.filter(c => c.isClosed);
      const latestOne = closedOne.at(-1);
      const latestFive = closedFive.at(-1);
      const oneAge = latestOne ? receivedAt - (latestOne.timestampMillis + 60_000) : Infinity;
      const fiveAge = latestFive ? receivedAt - (latestFive.timestampMillis + 300_000) : Infinity;
      if (!latestOne || !latestFive || oneAge < 0 || fiveAge < 0 ||
          oneAge > MAX_CANDLE_AGE["1"] || fiveAge > MAX_CANDLE_AGE["5"]) {
        return { item, stale: true, signal: { symbol: item.symbol, marketType: item.marketType, action: "NO_TRADE", reasons: ["Market candle data is stale or no closed candle is available."] }, levels: null };
      }
      const signal = strategy.analyze({
        symbol: item.symbol, marketType: item.marketType,
        oneMinuteCandles: one, fiveMinuteCandles: five,
        marketDataReceivedAtMillis: receivedAt, nowMillis: receivedAt
      });
      signal.signalCandleTimestampMillis = latestOne.timestampMillis;
      let levels = null;
      if (["BUY", "SELL"].includes(signal.action)) {
        const values = calculateIndicators(closedFive);
        levels = riskEngine.build({ signal, entryPrice: signal.referencePrice, atr: values.atr14,
          supportZone: signal.supportZone, resistanceZone: signal.resistanceZone, priceTick: item.priceTick });
      }
      return { signal, levels, item, stale: false };
    });
    const results = settled.filter(x => x?.status === "fulfilled").map(x => x.value);
    const failures = settled.length - results.length;
    const actionable = results.filter(x => ["BUY", "SELL"].includes(x.signal.action));
    const rejectionReasons = {};
    for (const result of results) {
      if (result.signal.action === "NO_TRADE") {
        const reason = result.signal.reasons?.[0] || "Unspecified NO_TRADE";
        rejectionReasons[reason] = (rejectionReasons[reason] || 0) + 1;
      }
    }
    if (failures) rejectionReasons["SCAN_ERROR"] = failures;

    const now = new Date().toISOString();
    for (const {signal,levels} of actionable) {
      const candleAt = new Date(signal.signalCandleTimestampMillis).toISOString();
      const payload = {
        symbol: signal.symbol, marketType: signal.marketType, action: signal.action,
        referencePrice: signal.referencePrice, evaluatedAtMillis: signal.signalCandleTimestampMillis,
        riskAccepted: Boolean(levels?.accepted), grossRiskReward: levels?.grossRiskReward ?? null,
        stopLossPrice: levels?.stopLossPrice ?? null, takeProfitPrice: levels?.takeProfitPrice ?? null,
        riskReason: levels?.rejectionReason ?? null, paperApproved: false,
        paperApprovalReason: "Scheduled monitor sends signals only; paper approval remains separate.",
        reasons: signal.reasons, riskLevels: levels
      };
      await rest("visiontrade_signal_events?on_conflict=market_type,symbol,action,signal_candle_at", {
        method: "POST", headers: { prefer: "resolution=ignore-duplicates,return=minimal" },
        body: JSON.stringify([{ market_type: signal.marketType, symbol: signal.symbol, action: signal.action,
          signal_candle_at: candleAt, signal_payload: payload, telegram_status: levels?.accepted ? "pending" : "rejected",
          telegram_last_error: levels?.accepted ? null : (levels?.rejectionReason || "Risk validation was not approved.") }])
      });
    }

    const telegramEnabled = Boolean(getEnv("TELEGRAM_BOT_TOKEN") && getEnv("TELEGRAM_CHAT_ID"));
    let telegramConnectedAt = prior?.[0]?.telegram_connected_at ?? null;
    let telegramTestSent = false;
    if (telegramEnabled && !telegramConnectedAt) {
      try {
        await telegramSend("NAXYUS VISIONTRADE: Telegram connection verified. Scheduled market monitoring is active. Strategy alerts will be sent when confirmed signals appear. No exchange orders will be placed.");
        telegramConnectedAt = new Date().toISOString();
        telegramTestSent = true;
      } catch (error) {
        console.error("VisionTrade Telegram connection test failed:", error?.message || error);
      }
    }
    const delivery = await deliverPendingAlerts(telegramEnabled);

    const history = await rest("visiontrade_signal_events?select=signal_payload&order=signal_candle_at.desc&limit=10");
    const recentSignals = (history || []).map(row => row.signal_payload);
    await rest("visiontrade_scan_diagnostics", {
      method: "POST", headers: { prefer: "return=minimal" },
      body: JSON.stringify([{
        scanned_at: now, market_pairs_discovered: discovered.length, eligible_pairs: discovered.length,
        scanned_pairs: sample.length, succeeded_pairs: results.length, failed_pairs: failures,
        actionable_signals: actionable.length, stale_pairs: results.filter(x => x.stale).length,
        rejection_reasons: rejectionReasons
      }])
    });
    await rest("visiontrade_monitor_snapshot?on_conflict=id", {
      method: "POST", headers: { prefer: "resolution=merge-duplicates,return=minimal" },
      body: JSON.stringify([{
        id: "latest", mode: "SCHEDULED_LIVE_MARKET_MONITORING", worker_running: true,
        last_scan_at: now, last_scan_pairs: results.length, actionable_signals: actionable.length,
        recent_signals: recentSignals, telegram_connected_at: telegramConnectedAt, updated_at: now
      }])
    });
    return json({
      ok: true, mode: "SCHEDULED_LIVE_MARKET_MONITORING",
      discovered: discovered.length, eligible: discovered.length, scanned: results.length,
      failed: failures, stale: results.filter(x => x.stale).length,
      actionableSignals: actionable.length, rejectionReasons,
      telegramEnabled, telegramTestSent, telegramSent: delivery.sent, telegramFailed: delivery.failed,
      liveTrading: false, scannedAt: now
    });
  } catch (error) {
    console.error("visiontrade scheduled scan failed:", error?.message || error);
    return json({ error: "Scheduled scan failed", detail: error?.message || "unknown error" }, 500);
  }
});
