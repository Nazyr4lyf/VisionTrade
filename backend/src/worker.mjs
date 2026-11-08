import { BybitClient } from "./providers/bybit-client.mjs";
import { CoinGeckoClient } from "./providers/coingecko-client.mjs";

const intervalMs = Number(process.env.SCAN_INTERVAL_MS ?? 60_000);
const discoveryIntervalMs = Number(process.env.DISCOVERY_INTERVAL_MS ?? 21_600_000);
const minimumTurnoverUsd = Number(process.env.MIN_TURNOVER_24H_USD ?? 5_000_000);

if (!Number.isFinite(intervalMs) || intervalMs < 15_000) {
  throw new Error("SCAN_INTERVAL_MS must be at least 15000");
}
if (!Number.isFinite(minimumTurnoverUsd) || minimumTurnoverUsd < 0) {
  throw new Error("MIN_TURNOVER_24H_USD must be non-negative");
}

const bybit = new BybitClient();
const coinGecko = new CoinGeckoClient({ apiKey: process.env.COINGECKO_DEMO_API_KEY });
let stopping = false;
let lastDiscoveryAt = 0;
let discovery = [];

async function refreshDiscovery() {
  const [spot, perpetual] = await Promise.all([
    bybit.getAllUsdtInstruments("spot"),
    bybit.getAllUsdtInstruments("linear")
  ]);
  const [spotTickers, perpetualTickers, coins] = await Promise.all([
    bybit.getTickers("spot"),
    bybit.getTickers("linear"),
    coinGecko.getUsdtMarketDiscovery()
  ]);

  const tickerMap = new Map([...spotTickers, ...perpetualTickers]
    .map(ticker => [ticker.symbol, ticker]));
  const coinSymbols = new Set(coins.map(coin => coin.symbol));
  const spotSet = new Set(spot.map(item => item.symbol));
  const perpSet = new Set(perpetual.map(item => item.symbol));

  discovery = [...new Set([...spotSet, ...perpSet])]
    .filter(symbol => coinSymbols.has(symbol.replace(/USDT$/, "")))
    .map(symbol => ({
      symbol,
      markets: [
        ...(spotSet.has(symbol) ? ["SPOT"] : []),
        ...(perpSet.has(symbol) ? ["USDT_PERPETUAL"] : [])
      ],
      turnover24hUsd: Number(tickerMap.get(symbol)?.turnover24h ?? 0)
    }))
    .filter(item => item.turnover24hUsd >= minimumTurnoverUsd)
    .sort((a, b) => b.turnover24hUsd - a.turnover24hUsd);

  lastDiscoveryAt = Date.now();
  console.info(JSON.stringify({
    event: "discovery_refreshed",
    eligibleSymbols: discovery.length,
    at: new Date(lastDiscoveryAt).toISOString()
  }));
}

async function scanCycle() {
  if (Date.now() - lastDiscoveryAt >= discoveryIntervalMs) await refreshDiscovery();

  // Market-data collection only. Signal generation and paper-ledger persistence
  // are deliberately not enabled until the backend strategy parity tests exist.
  const sample = discovery.slice(0, 30);
  const results = await Promise.allSettled(sample.map(async item => {
    const market = item.markets.includes("SPOT") ? "spot" : "linear";
    const [oneMinute, fiveMinute] = await Promise.all([
      bybit.getKlines(market, item.symbol, "1", { limit: 120 }),
      bybit.getKlines(market, item.symbol, "5", { limit: 120 })
    ]);
    return {
      symbol: item.symbol,
      market,
      oneMinuteClosed: oneMinute.filter(candle => candle.isClosed).length,
      fiveMinuteClosed: fiveMinute.filter(candle => candle.isClosed).length
    };
  }));

  const successful = results.filter(result => result.status === "fulfilled").length;
  console.info(JSON.stringify({
    event: "market_data_cycle",
    symbolsRequested: sample.length,
    symbolsSucceeded: successful,
    symbolsFailed: results.length - successful,
    at: new Date().toISOString()
  }));
}

async function main() {
  console.info(JSON.stringify({ event: "worker_started", mode: "MARKET_DATA_ONLY", liveTrading: false }));
  while (!stopping) {
    const started = Date.now();
    try {
      await scanCycle();
    } catch (error) {
      console.error(JSON.stringify({ event: "scan_cycle_failed", message: error.message }));
    }
    const remaining = Math.max(0, intervalMs - (Date.now() - started));
    if (!stopping) await new Promise(resolve => {
      const timer = setTimeout(resolve, remaining);
      process.once("SIGTERM", () => { clearTimeout(timer); resolve(); });
    });
  }
}

process.once("SIGTERM", () => { stopping = true; });
process.once("SIGINT", () => { stopping = true; });
main().catch(error => {
  console.error(JSON.stringify({ event: "worker_fatal", message: error.message }));
  process.exitCode = 1;
});
