export function formatStrategyAlert(signal) {
  if (!signal || !["BUY", "SELL"].includes(signal.action)) throw new Error("Only actionable strategy signals can be formatted");
  const side = signal.action === "BUY" ? "LONG" : "SHORT";
  const price = Number(signal.referencePrice);
  if (!Number.isFinite(price) || price <= 0) throw new Error("Signal reference price is invalid");
  const zone = signal.action === "BUY" ? signal.supportZone : signal.resistanceZone;
  const zoneText = zone && Number.isFinite(Number(zone.low)) && Number.isFinite(Number(zone.high))
    ? `${zone.low} – ${zone.high}` : "not available";
  return [
    "🔔 NAXYUS VISIONTRADE — STRATEGY SIGNAL",
    `${signal.symbol} | ${signal.marketType} | ${side}`,
    `Reference price: ${price}`,
    `Relevant confirmed zone: ${zoneText}`,
    "",
    "Strategy confirmations:",
    ...signal.reasons.map(reason => `• ${reason}`),
    "",
    "STATUS: SIGNAL ONLY — not a paper-trade approval.",
    "No exchange order has been placed. Live trading is disabled."
  ].join("\n");
}

export class SignalAlertDeduplicator {
  constructor() { this.lastAlertByKey = new Map(); }

  fingerprint(signal) {
    if (!signal || !["BUY", "SELL"].includes(signal.action)) return null;
    const candleTime = Number(signal.signalCandleTimestampMillis ?? signal.evaluatedAtMillis);
    if (!Number.isSafeInteger(candleTime) || candleTime <= 0) return null;
    return { key: `${signal.marketType}:${signal.symbol}`, value: `${signal.action}:${candleTime}` };
  }

  hasSent(signal) {
    const item = this.fingerprint(signal);
    return item ? this.lastAlertByKey.get(item.key) === item.value : false;
  }

  markSent(signal) {
    const item = this.fingerprint(signal);
    if (item) this.lastAlertByKey.set(item.key, item.value);
  }
}
