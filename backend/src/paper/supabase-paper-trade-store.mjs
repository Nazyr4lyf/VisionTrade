function mapNumeric(value) {
  // PostgreSQL NUMERIC may carry more precision than JavaScript Number can retain.
  // Keep database decimals as strings at the persistence boundary.
  return value == null ? null : String(value);
}

function mapTrade(row) {
  if (!row) return null;
  return {
    id: row.id,
    idempotencyKey: row.idempotency_key,
    status: row.status,
    symbol: row.symbol,
    marketType: row.market_type,
    side: row.side,
    quantity: mapNumeric(row.quantity),
    entryPrice: mapNumeric(row.entry_price),
    stopLossPrice: mapNumeric(row.stop_loss_price),
    takeProfitPrice: mapNumeric(row.take_profit_price),
    riskBudget: mapNumeric(row.risk_budget),
    estimatedLossAtStop: mapNumeric(row.estimated_loss_at_stop),
    netRiskReward: mapNumeric(row.net_risk_reward),
    openedAt: new Date(row.opened_at).getTime(),
    closedAt: row.closed_at ? new Date(row.closed_at).getTime() : null,
    exit: row.exit_reason ? {
      reason: row.exit_reason,
      fillPrice: mapNumeric(row.exit_fill_price),
      netPnl: mapNumeric(row.exit_net_pnl),
      realizedR: mapNumeric(row.exit_realized_r)
    } : null
  };
}

/**
 * Server-only Supabase PostgREST store.
 * Requires a service-role key and the matching SQL schema/RPC functions.
 * Never instantiate this in an Android or browser client.
 */
export class SupabasePaperTradeStore {
  constructor({
    url = process.env.SUPABASE_URL,
    serviceRoleKey = process.env.SUPABASE_SERVICE_ROLE_KEY,
    fetchImpl = globalThis.fetch
  } = {}) {
    if (!url || !serviceRoleKey) {
      throw new Error("SUPABASE_URL and SUPABASE_SERVICE_ROLE_KEY are required.");
    }
    const parsed = new URL(url);
    if (parsed.protocol !== "https:" && parsed.hostname !== "localhost" && parsed.hostname !== "127.0.0.1") {
      throw new Error("Supabase URL must use HTTPS.");
    }
    if (typeof fetchImpl !== "function") throw new Error("A Fetch API implementation is required.");
    this.baseUrl = parsed.toString().replace(/\/$/, "");
    this.serviceRoleKey = serviceRoleKey;
    this.fetchImpl = fetchImpl;
  }

  async rpc(name, body) {
    const response = await this.fetchImpl(`${this.baseUrl}/rest/v1/rpc/${name}`, {
      method: "POST",
      headers: {
        apikey: this.serviceRoleKey,
        Authorization: `Bearer ${this.serviceRoleKey}`,
        "Content-Type": "application/json",
        Accept: "application/json"
      },
      body: JSON.stringify(body)
    });
    const text = await response.text();
    let payload = null;
    if (text) {
      try { payload = JSON.parse(text); }
      catch { throw new Error("Supabase returned a non-JSON response."); }
    }
    if (!response.ok) {
      const message = typeof payload?.message === "string" ? payload.message : "Supabase paper-trade storage request failed.";
      throw new Error(message);
    }
    return payload;
  }

  async createOpenTrade(trade) {
    const result = await this.rpc("create_open_paper_trade", { p_trade: trade });
    return {
      created: Boolean(result?.created),
      reason: result?.reason ?? null,
      trade: mapTrade(result?.trade)
    };
  }

  async findById(id) {
    return mapTrade(await this.rpc("get_paper_trade_by_id", { p_trade_id: id }));
  }

  async listOpenTrades() {
    const rows = await this.rpc("list_open_paper_trades", {});
    if (!Array.isArray(rows)) {
      throw new Error("Supabase returned an invalid open paper-trade list.");
    }
    return rows.map(mapTrade);
  }

  async closeIfOpen(id, closure) {
    const result = await this.rpc("close_paper_trade", {
      p_trade_id: id,
      p_closure: {
        status: closure.status,
        closedAt: closure.closedAt,
        exit: closure.exit
      }
    });
    return {
      closed: Boolean(result?.closed),
      reason: result?.reason ?? null,
      trade: mapTrade(result?.trade)
    };
  }
}
