import { randomUUID } from "node:crypto";
import { calculateIndicators } from "../strategy/indicators.mjs";
import { ScalpingStrategyEngine } from "../strategy/scalping-strategy.mjs";
import { PaperTradeApprovalEngine } from "../risk/paper-trade-approval-engine.mjs";
import { PaperExecutionEngine } from "./paper-execution-engine.mjs";

/**
 * Explicitly gated composition of closed-candle strategy, risk approval and
 * persistent paper ledger. It never talks to an exchange order endpoint.
 * The caller must opt in with enabled:true; the worker remains MARKET_DATA_ONLY.
 */
export class PaperTradingPipeline {
  constructor({
    enabled = false,
    strategyEngine = new ScalpingStrategyEngine(),
    approvalEngine = new PaperTradeApprovalEngine(),
    executionEngine = new PaperExecutionEngine(),
    ledger,
    idFactory = randomUUID
  } = {}) {
    if (enabled && (!ledger || typeof ledger.openApprovedTrade !== "function")) {
      throw new Error("An enabled paper pipeline requires a persistent paper ledger.");
    }
    this.enabled = enabled;
    this.strategyEngine = strategyEngine;
    this.approvalEngine = approvalEngine;
    this.executionEngine = executionEngine;
    this.ledger = ledger ?? null;
    this.idFactory = idFactory;
  }

  async processOpenPositions({ marketDataByInstrument = {}, costsByInstrument = {}, nowMillis = Date.now() } = {}) {
    if (!this.enabled) {
      return { status: "DISABLED", processed: 0, results: [] };
    }
    if (!this.ledger || typeof this.ledger.listOpenTrades !== "function") {
      throw new Error("An enabled paper pipeline requires ledger listOpenTrades support.");
    }
    const openTrades = await this.ledger.listOpenTrades();
    const results = [];
    for (const trade of openTrades) {
      const key = `${trade.symbol}|${trade.marketType}`;
      const marketData = marketDataByInstrument[key];
      if (!marketData || !Array.isArray(marketData.candles)) {
        results.push({
          tradeId: trade.id,
          status: "SKIPPED",
          reason: "OPEN_POSITION_MARKET_DATA_UNAVAILABLE"
        });
        continue;
      }
      const costs = costsByInstrument[key] ?? {};
      const result = await this.processOpenTrade({
        trade,
        candles: marketData.candles,
        costs,
        nowMillis
      });
      results.push({ tradeId: trade.id, ...result });
    }
    return { status: "PROCESSED", processed: results.length, results };
  }

  async processOpenTrade({ trade, candles, costs = {}, nowMillis = Date.now() }) {
    if (!this.enabled) {
      return { status: "DISABLED", reason: "PAPER_PIPELINE_DISABLED", simulation: null, ledger: null };
    }
    if (!this.ledger || typeof this.ledger.closeTrade !== "function") {
      throw new Error("An enabled paper pipeline requires a ledger with closeTrade support.");
    }
    const simulation = this.executionEngine.evaluateExit({
      trade,
      candles,
      feeRate: costs.feeRate ?? 0,
      slippageBps: costs.slippageBps ?? 0,
      fundingCost: costs.fundingCost ?? 0
    });
    if (!simulation.closed) {
      return { status: "POSITION_OPEN", reason: simulation.reason, simulation, ledger: null };
    }
    const ledger = await this.ledger.closeTrade({
      tradeId: trade.id,
      exit: {
        reason: simulation.reason,
        fillPrice: simulation.fillPrice,
        netPnl: simulation.netPnl,
        realizedR: simulation.realizedR
      },
      closedAt: nowMillis
    });
    return {
      status: ledger.closed ? "PAPER_TRADE_CLOSED" : "PAPER_TRADE_NOT_CLOSED",
      reason: ledger.reason,
      simulation,
      ledger
    };
  }

  async processSnapshot({
    symbol, marketType, fiveMinuteCandles, oneMinuteCandles,
    instrument, equity, costs = {},
    marketDataReceivedAtMillis, nowMillis = Date.now()
  }) {
    if (!this.enabled) {
      return { status: "DISABLED", reason: "PAPER_PIPELINE_DISABLED", signal: null, approval: null, ledger: null };
    }

    const signal = this.strategyEngine.analyze({
      symbol, marketType, fiveMinuteCandles, oneMinuteCandles,
      marketDataReceivedAtMillis, nowMillis
    });
    if (signal.action === "NO_TRADE") {
      return { status: "NO_TRADE", reason: signal.reasons?.[0] ?? "NO_ACTIONABLE_SIGNAL", signal, approval: null, ledger: null };
    }

    const closedFiveMinute = fiveMinuteCandles.filter(candle => candle.isClosed);
    const atr = calculateIndicators(closedFiveMinute).atr14;
    if (!Number.isFinite(atr) || atr <= 0) {
      return { status: "REJECTED", reason: "INVALID_OR_MISSING_5M_ATR", signal, approval: null, ledger: null };
    }

    const approval = this.approvalEngine.evaluate({ signal, atr, instrument, equity, costs });
    if (!approval.approved) {
      return { status: "REJECTED", reason: approval.rejectionReason, signal, approval, ledger: null };
    }

    const latestClosedOneMinute = oneMinuteCandles
      .filter(candle => candle.isClosed)
      .reduce((latest, candle) => !latest || candle.timestampMillis > latest.timestampMillis ? candle : latest, null);
    if (!latestClosedOneMinute) {
      return { status: "REJECTED", reason: "NO_CLOSED_1M_CANDLE", signal, approval, ledger: null };
    }

    // A candle/action key makes retries of the same closed-candle signal idempotent.
    const idempotencyKey = [symbol, marketType, latestClosedOneMinute.timestampMillis, signal.action].join(":");
    const ledger = await this.ledger.openApprovedTrade({
      approval,
      idempotencyKey,
      tradeId: this.idFactory(),
      openedAt: nowMillis
    });
    return {
      status: ledger.created ? "PAPER_TRADE_OPENED" : "PAPER_TRADE_NOT_OPENED",
      reason: ledger.reason,
      signal,
      approval,
      ledger
    };
  }
}
