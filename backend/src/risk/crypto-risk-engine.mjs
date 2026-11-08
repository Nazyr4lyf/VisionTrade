const EPSILON = 1e-10;

function positive(value, name, allowZero = false) {
  const n = Number(value);
  if (!Number.isFinite(n) || (allowZero ? n < 0 : n <= 0)) {
    throw new Error(`${name} must be a finite ${allowZero ? "non-negative" : "positive"} number.`);
  }
  return n;
}

function aligned(value, step) {
  const units = value / step;
  return Math.abs(units - Math.round(units)) <= EPSILON * Math.max(1, Math.abs(units));
}

function floorToStep(value, step) {
  const units = Math.floor(value / step + EPSILON);
  return Number((units * step).toPrecision(14));
}

/**
 * Independent backend risk gate. It is deliberately not connected to the
 * market-data worker until stop/target generation and persistence are ready.
 * This gate never creates or submits exchange orders.
 */
export class CryptoRiskEngine {
  constructor({ riskFraction = 0.01, minimumNetRiskReward = 2 } = {}) {
    if (!Number.isFinite(riskFraction) || riskFraction <= 0 || riskFraction > 1) {
      throw new Error("Risk fraction must be greater than 0 and no more than 1.");
    }
    if (!Number.isFinite(minimumNetRiskReward) || minimumNetRiskReward < 2) {
      throw new Error("Minimum net risk/reward cannot be below 2.0R.");
    }
    this.riskFraction = riskFraction;
    this.minimumNetRiskReward = minimumNetRiskReward;
  }

  calculate(request) {
    const equity = Number(request.equity);
    const budget = Number.isFinite(equity) && equity > 0 ? equity * this.riskFraction : 0;
    const reject = (reason, grossRiskReward = null, netRiskReward = null) => ({
      accepted: false, rejectionReason: reason, riskBudget: budget,
      quantity: 0, notional: 0, estimatedLossAtStop: 0,
      grossRiskReward, netRiskReward
    });

    let entry, stop, target, tick, step, minimumQuantity, minimumNotional, multiplier;
    try {
      entry = positive(request.entryPrice, "Entry price");
      stop = positive(request.stopLossPrice, "Stop-loss price");
      target = positive(request.takeProfitPrice, "Take-profit price");
      tick = positive(request.instrument?.priceTick, "Price tick");
      step = positive(request.instrument?.quantityStep, "Quantity step");
      minimumQuantity = positive(request.instrument?.minimumQuantity, "Minimum quantity");
      minimumNotional = positive(request.instrument?.minimumNotional, "Minimum notional", true);
      multiplier = positive(request.instrument?.contractMultiplier ?? 1, "Contract multiplier");
      positive(equity, "Equity");
    } catch (error) {
      return reject(error.message);
    }

    const { marketType, symbol } = request.instrument;
    const side = request.side;
    if (!symbol || !["SPOT", "USDT_PERPETUAL"].includes(marketType)) return reject("Unsupported exchange instrument.");
    if (!["LONG", "SHORT"].includes(side)) return reject("Unsupported position side.");
    if (![entry, stop, target].every(price => aligned(price, tick))) {
      return reject("Entry, stop-loss and take-profit prices must align to the exchange tick size.");
    }
    if (marketType === "SPOT" && side === "SHORT") {
      return reject("Spot risk calculation cannot open a naked short position.");
    }

    const isLong = side === "LONG";
    const stopValid = isLong ? stop < entry : stop > entry;
    const targetValid = isLong ? target > entry : target < entry;
    if (!stopValid || !targetValid) {
      return reject("Stop-loss and take-profit must be on the correct side of entry.");
    }

    const costs = request.costs ?? {};
    let entryFee, exitFee, slippage, funding;
    try {
      entryFee = positive(costs.entryFeeRate ?? 0, "Entry fee", true);
      exitFee = positive(costs.exitFeeRate ?? 0, "Exit fee", true);
      slippage = positive(costs.slippageRatePerSide ?? 0, "Slippage", true);
      funding = positive(costs.fundingRateAllowance ?? 0, "Funding allowance", true);
    } catch (error) {
      return reject(error.message);
    }
    if (slippage >= 1) return reject("Slippage rate must be below 100%.");

    const entryCosts = entry * (entryFee + slippage);
    const stopExitCosts = stop * (exitFee + slippage) + entry * funding;
    const targetExitCosts = target * (exitFee + slippage) + entry * funding;
    const grossLoss = Math.abs(entry - stop) * multiplier;
    const grossReward = Math.abs(target - entry) * multiplier;
    const netLoss = grossLoss + (entryCosts + stopExitCosts) * multiplier;
    const netReward = grossReward - (entryCosts + targetExitCosts) * multiplier;
    if (!(netLoss > 0)) return reject("Calculated loss per unit is invalid.");
    if (!(netReward > 0)) return reject("Estimated costs consume the entire target reward.");

    const grossRr = grossReward / grossLoss;
    const netRr = netReward / netLoss;
    if (netRr + EPSILON < this.minimumNetRiskReward) {
      return reject("Net risk/reward is below the required 2.0R after fees and slippage.", grossRr, netRr);
    }

    const quantity = floorToStep((budget / netLoss), step);
    if (!(quantity >= minimumQuantity) || !Number.isFinite(quantity)) {
      return reject("Risk-sized quantity is below the exchange minimum quantity.", grossRr, netRr);
    }
    const notional = quantity * entry * multiplier;
    if (notional + EPSILON < minimumNotional) {
      return reject("Risk-sized position is below the exchange minimum notional.", grossRr, netRr);
    }
    const estimatedLoss = quantity * netLoss;
    if (estimatedLoss > budget + EPSILON) {
      return reject("Rounded position would exceed the configured 1% risk budget.", grossRr, netRr);
    }

    return {
      accepted: true, rejectionReason: null, riskBudget: budget,
      quantity, notional, estimatedLossAtStop: estimatedLoss,
      grossRiskReward: grossRr, netRiskReward: netRr
    };
  }

  evaluateSignal({ signal, ...request }) {
    if (!signal || !["BUY", "SELL"].includes(signal.action)) {
      return {
        ...this.calculate({ ...request, side: "LONG" }),
        accepted: false,
        rejectionReason: "Only BUY or SELL strategy signals can enter the risk gate."
      };
    }
    if (signal.symbol !== request.instrument?.symbol ||
        signal.marketType !== request.instrument?.marketType) {
      return {
        ...this.calculate({ ...request, side: signal.action === "BUY" ? "LONG" : "SHORT" }),
        accepted: false,
        rejectionReason: "Signal and exchange instrument do not match."
      };
    }
    return this.calculate({
      ...request,
      side: signal.action === "BUY" ? "LONG" : "SHORT"
    });
  }
}
