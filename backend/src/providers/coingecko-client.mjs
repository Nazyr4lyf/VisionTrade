import { fetchJson } from "./http.mjs";

const BASE_URL = "https://api.coingecko.com/api/v3";

export class CoinGeckoClient {
  constructor({ fetchImpl = fetch, baseUrl = BASE_URL, apiKey } = {}) {
    this.fetchImpl = fetchImpl;
    this.baseUrl = baseUrl.replace(/\/$/, "");
    this.apiKey = apiKey;
  }

  async getUsdtMarketDiscovery({ page = 1, perPage = 250 } = {}) {
    if (!Number.isInteger(page) || page < 1) throw new Error("Invalid CoinGecko page");
    if (!Number.isInteger(perPage) || perPage < 1 || perPage > 250) {
      throw new Error("CoinGecko perPage must be between 1 and 250");
    }

    const params = new URLSearchParams({
      vs_currency: "usd",
      order: "market_cap_desc",
      per_page: String(perPage),
      page: String(page),
      sparkline: "false"
    });
    const headers = this.apiKey ? { "x-cg-demo-api-key": this.apiKey } : {};
    const rows = await fetchJson(
      `${this.baseUrl}/coins/markets?${params}`,
      { fetchImpl: this.fetchImpl, headers }
    );

    if (!Array.isArray(rows)) throw new Error("Invalid CoinGecko market discovery response");
    return rows.map(row => ({
      id: String(row.id),
      name: String(row.name),
      symbol: String(row.symbol).toUpperCase(),
      marketCapRank: Number.isFinite(row.market_cap_rank) ? row.market_cap_rank : null,
      marketCapUsd: Number.isFinite(row.market_cap) ? String(row.market_cap) : null
    }));
  }
}
