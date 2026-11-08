const EPSILON = 1e-10;

function positive(value, name) {
  const number = Number(value);
  if (!Number.isFinite(number) || number <= 0) {
    throw new Error(`${name} must be a finite positive number.`);
  }
  return number;
}

function floorToTick(value, tick) {
  return Number((Math.floor(value / tick + EPSILON) * tick).toPrecision(14));
}

function ceilToTick(value, tick) {
  return Number((Math.ceil(value / tick - EPSILON) * tick).toPrecision(14));
}

function validZone(zone, name) {
  if (!zone || !Number.isFinite(Number(zone.low)) ||
      !Number.isFinite(Number(zone.high))) {
    throw new Error(`${name} zone is required and must have finite bounds.`);
  }
  const low = Number(zone.low), high = Number(zone.high);
  if (low <= 0 || high < low) throw new Error(`${name} zone bounds are invalid.`);
  return { low, high };
}

/**
 * Builds conservative, non-repainting candidate levels from already-confirmed
 * zones and a closed-candle ATR value. It never approves risk or places orders.
 * The opposing zone is the target; if it cannot provide at least the configured
 * gross R multiple, the candidate is rejected instead of inventing a target.
 */
export class StopTargetEngine {
  constructor({
    stopBufferAtr = 0.15,
    minimumStopAtr = 0.5,
    maximumStopAtr = 2.5,
    minimumGrossRiskReward = 2
  } = {}) {
    for (const [name, value] of Object.entries({
      stopBufferAtr, minimumStopAtr, maximumStopAtr, minimumGrossRiskReward
    })) positive(value, name);
    if (maximumStopAtr < minimumStopAtr) {
      throw new Error("Maximum stop distance must be at least the minimum stop distance.");
    }
    if (minimumGrossRiskReward < 2) {
      throw new Error("Minimum gross risk/reward cannot be below 2.0R.");
    }
    this.config = { stopBufferAtr, minimumStopAtr, maximumStopAtr, minimumGrossRiskReward };
  }

  build({ signal, entryPrice, atr, supportZone, resistanceZone, priceTick }) {
    const reject = reason => ({
      accepted: false, rejectionReason: reason,
      entryPrice: null, stopLossPrice: null, takeProfitPrice: null,
      stopDistance: null, targetDistance: null, grossRiskReward: null
    });
    if (!signal || !["BUY", "SELL"].includes(signal.action)) {
      return reject("Only BUY or SELL signals can receive stop/target levels.");
    }

    let entry, volatility, tick, support, resistance;
    try {
      entry = positive(entryPrice, "Entry price");
      volatility = positive(atr, "ATR");
      tick = positive(priceTick, "Price tick");
      support = validZone(supportZone, "Support");
      resistance = validZone(resistanceZone, "Resistance");
    } catch (error) {
      return reject(error.message);
    }
    if (support.high >= resistance.low) {
      return reject("Support and resistance zones overlap or are out of order.");
    }
    if (entry <= support.low || entry >= resistance.high) {
      return reject("Entry price is outside the confirmed support/resistance range.");
    }

    const { stopBufferAtr, minimumStopAtr, maximumStopAtr, minimumGrossRiskReward } = this.config;
    const buffer = volatility * stopBufferAtr;
    const isLong = signal.action === "BUY";
    let stop, target;
    if (isLong) {
      stop = floorToTick(support.low - buffer, tick);
      target = floorToTick(resistance.low, tick);
    } else {
      stop = ceilToTick(resistance.high + buffer, tick);
      target = ceilToTick(support.high, tick);
    }

    if (!(stop > 0) || (isLong ? !(stop < entry && target > entry) : !(stop > entry && target < entry))) {
      return reject("Generated stop-loss and take-profit are not on valid sides of entry.");
    }

    const stopDistance = Math.abs(entry - stop);
    const targetDistance = Math.abs(target - entry);
    const stopAtr = stopDistance / volatility;
    if (stopAtr < minimumStopAtr) {
      return reject("Stop distance is too tight relative to ATR.");
    }
    if (stopAtr > maximumStopAtr) {
      return reject("Stop distance exceeds the maximum ATR risk distance.");
    }
    const grossRiskReward = targetDistance / stopDistance;
    if (grossRiskReward + EPSILON < minimumGrossRiskReward) {
      return reject("Opposing confirmed zone does not provide the minimum gross 2.0R target.");
    }

    return {
      accepted: true, rejectionReason: null,
      entryPrice: entry, stopLossPrice: stop, takeProfitPrice: target,
      stopDistance, targetDistance, stopDistanceAtr: stopAtr, grossRiskReward
    };
  }
}
