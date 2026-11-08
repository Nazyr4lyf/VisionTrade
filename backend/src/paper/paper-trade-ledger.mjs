/**
 * Paper-only trade lifecycle coordinator.
 *
 * The injected store MUST provide atomic uniqueness for idempotencyKey and
 * one-open-position-per instrument/side, plus atomic close-if-open semantics.
 * This module intentionally ships no in-memory production fallback: durable
 * storage is supplied by the host application and is not yet wired to worker.
 */
export class PaperTradeLedger {
  constructor({ store }) {
    if (!store ||
        typeof store.createOpenTrade !== "function" ||
        typeof store.findById !== "function" ||
        typeof store.closeIfOpen !== "function") {
      throw new Error("A persistent trade store with atomic create/close methods is required.");
    }
    this.store = store;
  }

  async openApprovedTrade({ approval, idempotencyKey, tradeId, openedAt }) {
    if (!approval?.approved || !approval.paperTradeCandidate) {
      return { created: false, reason: "TRADE_NOT_APPROVED", trade: null };
    }
    if (typeof idempotencyKey !== "string" || !idempotencyKey.trim() ||
        typeof tradeId !== "string" || !tradeId.trim()) {
      throw new Error("Trade id and idempotency key are required.");
    }
    if (!Number.isFinite(openedAt) || openedAt <= 0) {
      throw new Error("openedAt must be a positive epoch timestamp.");
    }

    const candidate = approval.paperTradeCandidate;
    const requiredPositive = [
      ["entryPrice", candidate.entryPrice],
      ["stopLossPrice", candidate.stopLossPrice],
      ["takeProfitPrice", candidate.takeProfitPrice],
      ["quantity", candidate.quantity],
      ["riskBudget", candidate.riskBudget]
    ];
    for (const [name, value] of requiredPositive) {
      if (!Number.isFinite(Number(value)) || Number(value) <= 0) {
        throw new Error(`Approved candidate ${name} must be positive and finite.`);
      }
    }
    if (!candidate.symbol ||
        !["SPOT", "USDT_PERPETUAL"].includes(candidate.marketType) ||
        !["LONG", "SHORT"].includes(candidate.side) ||
        (candidate.marketType === "SPOT" && candidate.side === "SHORT")) {
      throw new Error("Approved candidate has an unsupported instrument or position side.");
    }

    const trade = {
      id: tradeId,
      idempotencyKey,
      status: "OPEN",
      symbol: candidate.symbol,
      marketType: candidate.marketType,
      side: candidate.side,
      quantity: Number(candidate.quantity),
      entryPrice: Number(candidate.entryPrice),
      stopLossPrice: Number(candidate.stopLossPrice),
      takeProfitPrice: Number(candidate.takeProfitPrice),
      riskBudget: Number(candidate.riskBudget),
      netRiskReward: Number(candidate.netRiskReward),
      openedAt,
      closedAt: null,
      exit: null
    };

    // Store operation must enforce idempotency and active-instrument uniqueness atomically.
    return this.store.createOpenTrade(trade);
  }

  async closeTrade({ tradeId, exit, closedAt }) {
    if (typeof tradeId !== "string" || !tradeId.trim()) {
      throw new Error("tradeId is required.");
    }
    if (!Number.isFinite(closedAt) || closedAt <= 0) {
      throw new Error("closedAt must be a positive epoch timestamp.");
    }
    if (!exit || !["STOP_LOSS", "TAKE_PROFIT"].includes(exit.reason) ||
        !Number.isFinite(Number(exit.fillPrice)) || Number(exit.fillPrice) <= 0 ||
        !Number.isFinite(Number(exit.netPnl)) ||
        !Number.isFinite(Number(exit.realizedR))) {
      throw new Error("A valid simulated exit is required.");
    }

    const existing = await this.store.findById(tradeId);
    if (!existing) return { closed: false, reason: "TRADE_NOT_FOUND", trade: null };
    if (existing.status === "CLOSED") {
      return { closed: false, reason: "ALREADY_CLOSED", trade: existing };
    }

    const closure = {
      status: "CLOSED",
      closedAt,
      exit: {
        reason: exit.reason,
        fillPrice: Number(exit.fillPrice),
        netPnl: Number(exit.netPnl),
        realizedR: Number(exit.realizedR)
      }
    };
    return this.store.closeIfOpen(tradeId, closure);
  }
}
