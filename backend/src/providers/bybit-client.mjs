import { fetchJson } from "./http.mjs";

const BASE_URL = "https://api.bybit.com";

function assertSuccess(payload, operation) {
  if (!payload || payload.retCode !== 0) {
    throw new Error(`Bybit ${operation} failed: ${payload?.retMsg ?? "invalid response"}`);
  }
  return payload.result;
}

function decimalString(value, field) {
  const text = String(value ?? "");
  if (!/^(?:0|[1-9]\d*)(?:\.\d+)?$/.test(text)) {
    throw new Error(`Invalid Bybit numeric field: ${field}`);
  }
  return text;
}

export class BybitClient {
  constructor({ fetchImpl = fetch, baseUrl = BASE_URL } = {}) {
    this.fetchImpl = fetchImpl;
    this.baseUrl = baseUrl.replace(/\/$/, "");
  }

  async getInstruments(category, { cursor, limit = 1000 } = {}) {
    if (!["spot", "linear"].includes(category)) {
      throw new Error("Only Bybit spot and linear markets are supported");
    }
    const params = new URLSearchParams({ category, limit: String(limit) });
    if (cursor) params.set("cursor", cursor);
    const payload = await fetchJson(
      `${this.baseUrl}/v5/market/instruments-info?${params}`,
      { fetchImpl: this.fetchImpl }
    );
    const result = assertSuccess(payload, "instrument discovery");
    return {
      items: (result.list ?? []).map(item => ({
        symbol: item.symbol,
        baseCoin: item.baseCoin,
        quoteCoin: item.quoteCoin,
        settleCoin: item.settleCoin,
        status: item.status,
        marketType: category === "spot" ? "SPOT" : "USDT_PERPETUAL",
        priceTick: decimalString(item.priceFilter?.tickSize, "tickSize"),
        quantityStep: decimalString(
          item.lotSizeFilter?.qtyStep ?? item.lotSizeFilter?.basePrecision,
          "qtyStep"
        ),
        minimumQuantity: decimalString(
          item.lotSizeFilter?.minOrderQty ?? item.lotSizeFilter?.minOrderAmt,
          "minOrderQty"
        ),
        minimumNotional: decimalString(
          item.lotSizeFilter?.minOrderAmt ?? item.lotSizeFilter?.minNotionalValue ?? "0",
          "minNotional"
        )
      })),
      nextCursor: result.nextPageCursor || null
    };
  }

  async getAllUsdtInstruments(category) {
    const all = [];
    let cursor;
    do {
      const page = await this.getInstruments(category, { cursor });
      all.push(...page.items.filter(item =>
        item.quoteCoin === "USDT" &&
        item.status === "Trading" &&
        (category === "spot" || item.settleCoin === "USDT")
      ));
      cursor = page.nextCursor;
    } while (cursor);
    return all;
  }

  async getTickers(category) {
    if (!["spot", "linear"].includes(category)) {
      throw new Error("Only Bybit spot and linear markets are supported");
    }
    const params = new URLSearchParams({ category });
    const payload = await fetchJson(
      `${this.baseUrl}/v5/market/tickers?${params}`,
      { fetchImpl: this.fetchImpl }
    );
    const result = assertSuccess(payload, "ticker retrieval");
    return (result.list ?? [])
      .filter(item => item.symbol.endsWith("USDT"))
      .map(item => ({
        symbol: item.symbol,
        lastPrice: decimalString(item.lastPrice, "lastPrice"),
        turnover24h: decimalString(item.turnover24h, "turnover24h"),
        volume24h: decimalString(item.volume24h, "volume24h")
      }));
  }

  async getKlines(category, symbol, interval, { limit = 200, start, end } = {}) {
    if (!["spot", "linear"].includes(category)) throw new Error("Unsupported market category");
    if (!/^[A-Z0-9]{3,30}USDT$/.test(symbol)) throw new Error("Only USDT-quoted symbols are supported");
    if (!["1", "5"].includes(String(interval))) throw new Error("Only 1m and 5m candles are supported");
    if (!Number.isInteger(limit) || limit < 1 || limit > 1000) throw new Error("Invalid kline limit");
    if (start !== undefined && (!Number.isSafeInteger(start) || start < 0)) throw new Error("Invalid kline start timestamp");
    if (end !== undefined && (!Number.isSafeInteger(end) || end < 0)) throw new Error("Invalid kline end timestamp");
    if (start !== undefined && end !== undefined && end <= start) throw new Error("Kline end must be after start");

    const params = new URLSearchParams({
      category, symbol, interval: String(interval), limit: String(limit)
    });
    if (start !== undefined) params.set("start", String(start));
    if (end !== undefined) params.set("end", String(end));
    const payload = await fetchJson(
      `${this.baseUrl}/v5/market/kline?${params}`,
      { fetchImpl: this.fetchImpl }
    );
    const result = assertSuccess(payload, "kline retrieval");
    const durationMs = Number(interval) * 60_000;
    return (result.list ?? [])
      .map(row => {
        const start = Number(row[0]);
        if (!Number.isSafeInteger(start)) throw new Error("Invalid Bybit candle timestamp");
        return {
          timestampMillis: start,
          open: decimalString(row[1], "open"),
          high: decimalString(row[2], "high"),
          low: decimalString(row[3], "low"),
          close: decimalString(row[4], "close"),
          volume: decimalString(row[5], "volume"),
          turnover: decimalString(row[6], "turnover"),
          timeframeMinutes: Number(interval),
          isClosed: start + durationMs <= Date.now()
        };
      })
      .sort((a, b) => a.timestampMillis - b.timestampMillis);
  }
}
