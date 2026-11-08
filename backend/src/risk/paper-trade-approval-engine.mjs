import { StopTargetEngine } from "./stop-target-engine.mjs";
import { CryptoRiskEngine } from "./crypto-risk-engine.mjs";

/**
 * Pure approval pipeline for paper-trade candidates.
 * Does not persist, submit orders, or mutate account state.
 */
export class PaperTradeApprovalEngine {
  constructor({
    stopTargetEngine = new StopTargetEngine(),
    riskEngine = new CryptoRiskEngine()
  } = {}) {
    this.stopTargetEngine = stopTargetEngine;
    this.riskEngine = riskEngine;
  }

  evaluate({ signal, atr, instrument, equity, costs = {} }) {
    const reject = (reason, levels = null, risk = null) => ({
      approved: false,
      rejectionReason: reason,
      signalAction: signal?.action ?? null,
      levels,
      risk
    });

    if (!signal || !["BUY", "SELL"].includes(signal.action)) {
      return reject("Only actionable BUY or SELL signals can be evaluated.");
    }
    if (!instrument || signal.symbol !== instrument.symbol ||
        signal.marketType !== instrument.marketType) {
      return reject("Signal and exchange instrument do not match.");
    }
    const entryPrice = Number(signal.referencePrice);
    if (!Number.isFinite(entryPrice) || entryPrice <= 0) {
      return reject("Signal reference price is missing or invalid.");
    }

    const levels = this.stopTargetEngine.build({
      signal,
      entryPrice,
      atr,
      supportZone: signal.supportZone,
      resistanceZone: signal.resistanceZone,
      priceTick: instrument.priceTick
    });
    if (!levels.accepted) return reject(levels.rejectionReason, levels);

    const risk = this.riskEngine.evaluateSignal({
      signal,
      equity,
      entryPrice: levels.entryPrice,
      stopLossPrice: levels.stopLossPrice,
      takeProfitPrice: levels.takeProfitPrice,
      instrument,
      costs
    });
    if (!risk.accepted) return reject(risk.rejectionReason, levels, risk);

    return {
      approved: true,
      rejectionReason: null,
      signalAction: signal.action,
      levels,
      risk,
      paperTradeCandidate: {
        symbol: signal.symbol,
        marketType: signal.marketType,
        side: signal.action === "BUY" ? "LONG" : "SHORT",
        entryPrice: levels.entryPrice,
        stopLossPrice: levels.stopLossPrice,
        takeProfitPrice: levels.takeProfitPrice,
        quantity: risk.quantity,
        notional: risk.notional,
        riskBudget: risk.riskBudget,
        estimatedLossAtStop: risk.estimatedLossAtStop,
        netRiskReward: risk.netRiskReward
      }
    };
  }
}
