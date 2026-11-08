import { mapWithConcurrency } from "../utils/concurrency.mjs";

const MINUTE_MS = 60_000;

function finiteNumber(value) {
  const number = Number(value);
  return Number.isFinite(number) ? number : null;
}

function marketCategory(marketType) {
  if (marketType === "SPOT") return "spot";
  if (marketType === "USDT_PERPETUAL") return "linear";
  return null;
}

/**
 * Monitors existing persisted paper positions only.
 * It never creates positions or sends exchange orders.
 *
 * Perpetuals are deliberately skipped until historical funding-cost accounting
 * is implemented; silently assuming zero funding would overstate performance.
 */
export class OpenPositionMonitor {
  constructor({
    ledger,
    pipeline,
    bybit,
    spotCosts,
    maxCandleAgeMs = 180_000,
    concurrency = 3
  } = {}) {
    if (!ledger || typeof ledger.listOpenTrades !== "function") {
      throw new Error("Open-position monitoring requires a persistent ledger.");
    }
    if (!pipeline || typeof pipeline.processOpenTrade !== "function") {
      throw new Error("Open-position monitoring requires the paper exit pipeline.");
    }
    if (!bybit || typeof bybit.getKlines !== "function") {
      throw new Error("Open-position monitoring requires a public market-data client.");
    }
    if (!spotCosts ||
        !Number.isFinite(spotCosts.feeRate) || spotCosts.feeRate < 0 ||
        !Number.isFinite(spotCosts.slippageBps) || spotCosts.slippageBps < 0) {
      throw new Error("Explicit non-negative Spot fee and slippage assumptions are required.");
    }
    if (!Number.isSafeInteger(maxCandleAgeMs) || maxCandleAgeMs < MINUTE_MS) {
      throw new Error("maxCandleAgeMs must be at least one minute.");
    }
    if (!Number.isInteger(concurrency) || concurrency < 1 || concurrency > 10) {
      throw new Error("concurrency must be an integer between 1 and 10.");
    }
    this.ledger = ledger;
    this.pipeline = pipeline;
    this.bybit = bybit;
    this.spotCosts = { ...spotCosts, fundingCost: 0 };
    this.maxCandleAgeMs = maxCandleAgeMs;
    this.concurrency = concurrency;
  }

  async monitorTrade(trade, nowMillis) {
    if (!trade || trade.status !== "OPEN" || typeof trade.id !== "string" ||
        typeof trade.symbol !== "string" || !Number.isFinite(trade.openedAt) ||
        trade.openedAt <= 0 || trade.openedAt >= nowMillis) {
      return { tradeId: trade?.id ?? null, status: "SKIPPED", reason: "INVALID_OPEN_POSITION" };
    }

    const category = marketCategory(trade.marketType);
    if (!category) {
      return { tradeId: trade.id, status: "SKIPPED", reason: "UNSUPPORTED_MARKET_TYPE" };
    }
    if (trade.marketType === "USDT_PERPETUAL") {
      return { tradeId: trade.id, status: "SKIPPED", reason: "PERPETUAL_FUNDING_COST_MODEL_REQUIRED" };
    }

    // If the trade opened exactly on a minute boundary, that candle is fully
    // post-entry; otherwise begin with the next minute to avoid pre-entry OHLC.
    const expectedFirstCandle = Math.ceil(trade.openedAt / MINUTE_MS) * MINUTE_MS;
    if (expectedFirstCandle >= nowMillis) {
      return { tradeId: trade.id, status: "SKIPPED", reason: "NO_POST_ENTRY_CLOSED_CANDLE_YET" };
    }
    let rawCandles;
    try {
      rawCandles = await this.bybit.getKlines(category, trade.symbol, "1", {
        limit: 1000,
        start: expectedFirstCandle,
        end: nowMillis
      });
    } catch {
      return { tradeId: trade.id, status: "SKIPPED", reason: "MARKET_DATA_FETCH_FAILED" };
    }

    if (!Array.isArray(rawCandles) || rawCandles.length === 0) {
      return { tradeId: trade.id, status: "SKIPPED", reason: "NO_CANDLE_HISTORY" };
    }

    const candles = rawCandles.map(candle => {
      const timestampMillis = finiteNumber(candle.timestampMillis);
      const open = finiteNumber(candle.open);
      const high = finiteNumber(candle.high);
      const low = finiteNumber(candle.low);
      const close = finiteNumber(candle.close);
      return {
        timestampMillis, open, high, low, close,
        timeframeMinutes: candle.timeframeMinutes,
        isClosed: candle.isClosed === true
      };
    });

    if (candles.some(candle =>
      !Number.isSafeInteger(candle.timestampMillis) ||
      candle.timestampMillis > nowMillis ||
      candle.timeframeMinutes !== 1 ||
      candle.open === null || candle.high === null ||
      candle.low === null || candle.close === null
    )) {
      return { tradeId: trade.id, status: "SKIPPED", reason: "INVALID_CANDLE_DATA" };
    }

    const closedCandles = candles.filter(candle => candle.isClosed);
    if (!closedCandles.length) {
      return { tradeId: trade.id, status: "SKIPPED", reason: "NO_CLOSED_CANDLES" };
    }

    const postEntryCandles = closedCandles.filter(candle => candle.timestampMillis >= expectedFirstCandle);
    if (!postEntryCandles.length || postEntryCandles[0].timestampMillis !== expectedFirstCandle) {
      return { tradeId: trade.id, status: "SKIPPED", reason: "INSUFFICIENT_POST_ENTRY_HISTORY" };
    }
    for (let index = 1; index < postEntryCandles.length; index += 1) {
      if (postEntryCandles[index].timestampMillis - postEntryCandles[index - 1].timestampMillis !== MINUTE_MS) {
        return { tradeId: trade.id, status: "SKIPPED", reason: "GAPPED_POST_ENTRY_CANDLE_HISTORY" };
      }
    }

    const latest = postEntryCandles[postEntryCandles.length - 1];
    const latestCloseAt = latest.timestampMillis + MINUTE_MS;
    if (latestCloseAt > nowMillis || nowMillis - latestCloseAt > this.maxCandleAgeMs) {
      return { tradeId: trade.id, status: "SKIPPED", reason: "STALE_CLOSED_CANDLE_DATA" };
    }

    const result = await this.pipeline.processOpenTrade({
      trade,
      candles: postEntryCandles,
      costs: this.spotCosts,
      nowMillis
    });
    return { tradeId: trade.id, ...result };
  }

  async runCycle({ nowMillis = Date.now() } = {}) {
    if (!Number.isSafeInteger(nowMillis) || nowMillis <= 0) {
      throw new Error("nowMillis must be a positive safe integer.");
    }
    const trades = await this.ledger.listOpenTrades();
    if (!Array.isArray(trades)) {
      throw new Error("Persistent ledger returned an invalid open-position list.");
    }
    const results = await mapWithConcurrency(
      trades,
      this.concurrency,
      trade => this.monitorTrade(trade, nowMillis)
    );
    return {
      status: "PROCESSED",
      openPositions: trades.length,
      closed: results.filter(item => item.status === "fulfilled" && item.value.status === "PAPER_TRADE_CLOSED").length,
      skipped: results.filter(item => item.status === "fulfilled" && item.value.status === "SKIPPED").length,
      failed: results.filter(item => item.status === "rejected").length,
      results: results.map(item => item.status === "fulfilled"
        ? item.value
        : { status: "ERROR", reason: "POSITION_MONITOR_FAILED" })
    };
  }
}
