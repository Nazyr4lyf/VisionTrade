/**
 * Conservative paper-only OHLC exit simulator.
 * If a candle touches both stop and target, stop is assumed first.
 * Stop gaps fill at the worse candle open; target fills at its trigger.
 * This module never submits exchange orders.
 */
export class PaperExecutionEngine {
  evaluateExit({ trade, candles, feeRate = 0, slippageBps = 0, fundingCost = 0 }) {
    if (!trade || trade.status !== "OPEN") {
      return { closed: false, reason: "TRADE_NOT_OPEN" };
    }
    if (!["LONG", "SHORT"].includes(trade.side) ||
        !["SPOT", "USDT_PERPETUAL"].includes(trade.marketType) ||
        (trade.marketType === "SPOT" && trade.side === "SHORT")) {
      return { closed: false, reason: "UNSUPPORTED_POSITION" };
    }
    const entry = Number(trade.entryPrice);
    const stop = Number(trade.stopLossPrice);
    const target = Number(trade.takeProfitPrice);
    const quantity = Number(trade.quantity);
    const riskBudget = Number(trade.riskBudget);
    if (![entry, stop, target, quantity, riskBudget].every(Number.isFinite) ||
        entry <= 0 || stop <= 0 || target <= 0 || quantity <= 0 || riskBudget <= 0 ||
        (trade.side === "LONG" ? !(stop < entry && target > entry) : !(target < entry && stop > entry))) {
      return { closed: false, reason: "INVALID_TRADE_LEVELS" };
    }
    if (!Array.isArray(candles) || !candles.length) {
      return { closed: false, reason: "NO_CLOSED_CANDLES" };
    }
    if (!Number.isFinite(feeRate) || feeRate < 0 ||
        !Number.isFinite(slippageBps) || slippageBps < 0 ||
        !Number.isFinite(fundingCost) || fundingCost < 0) {
      return { closed: false, reason: "INVALID_EXECUTION_COSTS" };
    }

    const openedAt = Number(trade.openedAt);
    if (!Number.isFinite(openedAt) || openedAt <= 0) {
      return { closed: false, reason: "INVALID_TRADE_OPEN_TIME" };
    }
    // Ignore any candle that began before the trade opened; its OHLC range may
    // contain price movement from before the position existed.
    const closed = candles.filter(c => c?.isClosed === true && c.timestampMillis >= openedAt);
    let previousTimestamp = -Infinity;
    for (const candle of closed) {
      if (!Number.isFinite(candle.timestampMillis) || candle.timestampMillis <= previousTimestamp ||
          ![candle.open, candle.high, candle.low, candle.close].every(Number.isFinite) ||
          candle.low <= 0 || candle.high < candle.low ||
          candle.open < candle.low || candle.open > candle.high ||
          candle.close < candle.low || candle.close > candle.high) {
        return { closed: false, reason: "INVALID_OR_OUT_OF_ORDER_CANDLE" };
      }
      previousTimestamp = candle.timestampMillis;
    }

    for (const candle of closed) {
      let reason = null;
      let trigger = null;
      let referenceFill = null;
      if (trade.side === "LONG") {
        const stopHit = candle.low <= stop;
        const targetHit = candle.high >= target;
        if (stopHit) {
          reason = "STOP_LOSS";
          trigger = stop;
          referenceFill = candle.open < stop ? candle.open : stop;
        } else if (targetHit) {
          reason = "TAKE_PROFIT";
          trigger = target;
          referenceFill = target;
        }
      } else {
        const stopHit = candle.high >= stop;
        const targetHit = candle.low <= target;
        if (stopHit) {
          reason = "STOP_LOSS";
          trigger = stop;
          referenceFill = candle.open > stop ? candle.open : stop;
        } else if (targetHit) {
          reason = "TAKE_PROFIT";
          trigger = target;
          referenceFill = target;
        }
      }
      if (!reason) continue;

      const slip = slippageBps / 10_000;
      const fillPrice = trade.side === "LONG"
        ? referenceFill * (reason === "STOP_LOSS" ? 1 - slip : 1 - slip)
        : referenceFill * (1 + slip);
      const grossPnl = (trade.side === "LONG" ? fillPrice - entry : entry - fillPrice) * quantity;
      const entryFee = entry * quantity * feeRate;
      const exitFee = fillPrice * quantity * feeRate;
      const netPnl = grossPnl - entryFee - exitFee - fundingCost;
      return {
        closed: true,
        reason,
        triggerPrice: trigger,
        fillPrice,
        candleTimestampMillis: candle.timestampMillis,
        grossPnl,
        entryFee,
        exitFee,
        fundingCost,
        netPnl,
        realizedR: netPnl / riskBudget
      };
    }
    return { closed: false, reason: "EXIT_NOT_HIT" };
  }
}
