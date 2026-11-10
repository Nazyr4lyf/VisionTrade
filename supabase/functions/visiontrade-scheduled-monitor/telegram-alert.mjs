const escapeHtml = (value) => String(value ?? "").replace(/[&<>"']/g, (char) => ({
  "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;"
})[char]);

function price(value, label) {
  const number = Number(value);
  if (!Number.isFinite(number) || number <= 0) {
    throw new Error(`Approved alert is missing a valid ${label}.`);
  }
  return new Intl.NumberFormat("en-US", { maximumSignificantDigits: 10 }).format(number);
}

function signalTime(timestamp) {
  const value = Number(timestamp);
  if (!Number.isFinite(value) || value <= 0) return "Time unavailable";
  return new Date(value).toISOString().replace("T", " ").slice(0, 16) + " UTC";
}

export function formatStrategyAlert(signal, levels) {
  if (!levels?.accepted) {
    throw new Error("Rejected risk assessments must never be formatted as strategy signals.");
  }
  if (!["BUY", "SELL"].includes(signal?.action)) {
    throw new Error("Only BUY or SELL decisions can be formatted as strategy signals.");
  }
  const rr = Number(levels.grossRiskReward);
  if (!Number.isFinite(rr) || rr < 2) {
    throw new Error("Approved alert requires a valid gross risk/reward of at least 2.00R.");
  }
  const side = signal.action === "BUY" ? "LONG" : "SHORT";
  const direction = signal.action === "BUY" ? "🟢" : "🔴";
  const reasons = Array.isArray(signal.reasons) ? signal.reasons : [];
  const confirmations = reasons.length
    ? reasons.map((reason) => "  • " + escapeHtml(reason)).join("\n")
    : "  • Strategy conditions confirmed";
  return [
    "⚡ <b>NAXYUS VISIONTRADE</b>",
    "<b>CONFIRMED STRATEGY SETUP</b>",
    "",
    direction + " <b>" + escapeHtml(signal.symbol) + " · " + side + "</b>",
    "<code>" + escapeHtml(signal.marketType) + " · BYBIT</code>",
    "",
    "<b>TRADE PLAN</b>",
    "├ Entry: <code>" + price(levels.entryPrice ?? signal.referencePrice, "entry price") + "</code>",
    "├ Stop Loss: <code>" + price(levels.stopLossPrice, "stop-loss") + "</code>",
    "└ Take Profit: <code>" + price(levels.takeProfitPrice, "take-profit") + "</code>",
    "",
    "<b>RISK / REWARD</b>",
    "Gross R:R: <b>" + rr.toFixed(2) + "R</b>",
    "",
    "<b>STRATEGY VALIDATION</b>",
    "Timeframes: <b>5m structure · 1m execution</b>",
    confirmations,
    "",
    "<b>Signal time:</b> " + escapeHtml(signalTime(signal.signalCandleTimestampMillis)),
    "",
    "<i>Analysis alert only. No exchange order has been placed. Live trading is disabled.</i>"
  ].join("\n");
}
