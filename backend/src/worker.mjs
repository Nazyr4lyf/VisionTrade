import { BybitClient } from "./providers/bybit-client.mjs";
import { CoinGeckoClient } from "./providers/coingecko-client.mjs";

const intervalMs = Number(process.env.SCAN_INTERVAL_MS ?? 60_000);
const discoveryIntervalMs = Number(process.env.DISCOVERY_INTERVAL_MS ?? 21_600_000);
const minimumTurnoverUsd = Number(process.env.MIN_TURNOVER_24H_USD ?? 5_000_000);

if (!Number.isFinite(intervalMs) || intervalMs < 15_000) {
  throw new Error("SCAN_INTERVAL_MS must be at least 15000");
}
if (!Number.isFinite(discoveryIntervalMs) || discoveryIntervalMs < 60_000) {
  throw new Error("DISCOVERY_INTERVAL_MS must be at least 60000");
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

  const coinSymbols = new Set(coins.map(coin => coin.symbol));
  const spotTurnover = new Map(spotTickers.map(ticker => [ticker.symbol, Number(ticker.turnover24h)]));
  const perpetualTurnover = new Map(perpetualTickers.map(ticker => [ticker.symbol, Number(ticker.turnover24h)]));

  discovery = [
    ...spot.map(item => ({
      symbol: item.symbol,
      market: "spot",
      turnover24hUsd: spotTurnover.get(item.symbol) ?? 0
    })),
    ...perpetual.map(item => ({
      symbol: item.symbol,
      market: "linear",
      turnover24hUsd: perpetualTurnover.get(item.symbol) ?? 0
    }))
  ]
    .filter(item =>
      coinSymbols.has(item.symbol.replace(/USDT$/, "")) &&
      Number.isFinite(item.turnover24hUsd) &&
      item.turnover24hUsd >= minimumTurnoverUsd
    )
    .sort((a, b) => b.turnover24hUsd - a.turnover24hUsd);

  lastDiscoveryAt = Date.now();
  console.info(JSON.stringify({
    event: "discovery_refreshed",
    eligibleMarketPairs: discovery.length,
    at: new Date(lastDiscoveryAt).toISOString()
  }));
}

async function scanCycle() {
  if (Date.now() - lastDiscoveryAt >= discoveryIntervalMs) await refreshDiscovery();

  // Market-data collection only. Strategy parity and paper-ledger persistence
  // must pass their own tests before signal generation is enabled.
  const sample = discovery.slice(0, 30);
  const results = await Promise.allSettled(sample.map(async item => {
    const [oneMinute, fiveMinute] = await Promise.all([
      bybit.getKlines(item.market, item.symbol, "1", { limit: 120 }),
      bybit.getKlines(item.market, item.symbol, "5", { limit: 120 })
    ]);
    return {
      symbol: item.symbol,
      market: item.market === "spot" ? "SPOT" : "USDT_PERPETUAL",
      oneMinuteClosed: oneMinute.filter(candle => candle.isClosed).length,
      fiveMinuteClosed: fiveMinute.filter(candle => candle.isClosed).length
    };
  }));

  const successful = results.filter(result => result.status === "fulfilled").length;
  console.info(JSON.stringify({
    event: "market_data_cycle",
    marketPairsRequested: sample.length,
    marketPairsSucceeded: successful,
    marketPairsFailed: results.length - successful,
    at: new Date().toISOString()
  }));
}

function waitForNextCycle(milliseconds) {
  return new Promise(resolve => {
    const finish = () => {
      clearTimeout(timer);
      process.off("SIGTERM", finish);
      process.off("SIGINT", finish);
      resolve();
    };
    const timer = setTimeout(finish, milliseconds);
    process.once("SIGTERM", finish);
    process.once("SIGINT", finish);
  });
}

async function main() {
  console.info(JSON.stringify({
    event: "worker_started",
    mode: "MARKET_DATA_ONLY",
    liveTrading: false
  }));
  while (!stopping) {
    const started = Date.now();
    try {
      await scanCycle();
    } catch (error) {
      console.error(JSON.stringify({ event: "scan_cycle_failed", message: error.message }));
    }
    const remaining = Math.max(0, intervalMs - (Date.now() - started));
    if (!stopping) await waitForNextCycle(remaining);
  }
}

const requestStop = () => { stopping = true; };
process.once("SIGTERM", requestStop);
process.once("SIGINT", requestStop);
main().catch(error => {
  console.error(JSON.stringify({ event: "worker_fatal", message: error.message }));
  process.exitCode = 1;
});
