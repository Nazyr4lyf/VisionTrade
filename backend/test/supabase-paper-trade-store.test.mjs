import test from "node:test";
import assert from "node:assert/strict";
import { SupabasePaperTradeStore } from "../src/paper/supabase-paper-trade-store.mjs";

const tradeRow = {
  id: "trade-1",
  idempotency_key: "signal-1",
  status: "OPEN",
  symbol: "BTCUSDT",
  market_type: "SPOT",
  side: "LONG",
  quantity: "0.100000000000000019",
  entry_price: "100.000000000000000123",
  stop_loss_price: "98",
  take_profit_price: "104",
  risk_budget: "1",
  estimated_loss_at_stop: "0.9",
  net_risk_reward: "2.1",
  opened_at: "2026-11-08T10:00:00.000Z",
  closed_at: null,
  exit_reason: null,
  exit_fill_price: null,
  exit_net_pnl: null,
  exit_realized_r: null
};

function response(payload, ok = true, status = 200) {
  return {
    ok,
    status,
    async text() { return JSON.stringify(payload); }
  };
}

test("requires server URL and service-role key", () => {
  assert.throws(() => new SupabasePaperTradeStore({ url: "https://example.supabase.co" }), /required/);
  assert.throws(() => new SupabasePaperTradeStore({ url: "http://example.supabase.co", serviceRoleKey: "secret" }), /HTTPS/);
});

test("createOpenTrade calls atomic RPC and preserves PostgreSQL NUMERIC precision", async () => {
  let request;
  const store = new SupabasePaperTradeStore({
    url: "https://visiontrade.supabase.co/",
    serviceRoleKey: "server-secret",
    fetchImpl: async (url, options) => {
      request = { url, options };
      return response({ created: true, reason: null, trade: tradeRow });
    }
  });

  const result = await store.createOpenTrade({
    id: "trade-1",
    idempotencyKey: "signal-1",
    symbol: "BTCUSDT",
    marketType: "SPOT",
    side: "LONG",
    quantity: 0.1,
    entryPrice: 100,
    stopLossPrice: 98,
    takeProfitPrice: 104,
    riskBudget: 1,
    estimatedLossAtStop: 0.9,
    netRiskReward: 2.1,
    openedAt: 1794132000000
  });

  assert.equal(request.url, "https://visiontrade.supabase.co/rest/v1/rpc/create_open_paper_trade");
  assert.equal(request.options.headers.apikey, "server-secret");
  assert.equal(request.options.headers.Authorization, "Bearer server-secret");
  assert.equal(JSON.parse(request.options.body).p_trade.symbol, "BTCUSDT");
  assert.equal(result.created, true);
  assert.equal(result.trade.idempotencyKey, "signal-1");
  assert.equal(result.trade.quantity, "0.100000000000000019");
  assert.equal(result.trade.entryPrice, "100.000000000000000123");
  assert.equal(result.trade.openedAt, Date.parse(tradeRow.opened_at));
});

test("findById maps missing trade to null", async () => {
  const store = new SupabasePaperTradeStore({
    url: "https://visiontrade.supabase.co",
    serviceRoleKey: "server-secret",
    fetchImpl: async () => response(null)
  });
  assert.equal(await store.findById("missing"), null);
});

test("closeIfOpen calls atomic close RPC and preserves decimal exit values", async () => {
  const closed = {
    ...tradeRow,
    status: "CLOSED",
    closed_at: "2026-11-08T10:01:00.000Z",
    exit_reason: "STOP_LOSS",
    exit_fill_price: "97.500000000000000123",
    exit_net_pnl: "-0.350000000000000019",
    exit_realized_r: "-0.35000000"
  };
  let body;
  const store = new SupabasePaperTradeStore({
    url: "https://visiontrade.supabase.co",
    serviceRoleKey: "server-secret",
    fetchImpl: async (_url, options) => {
      body = JSON.parse(options.body);
      return response({ closed: true, reason: null, trade: closed });
    }
  });
  const result = await store.closeIfOpen("trade-1", {
    status: "CLOSED",
    closedAt: 1794132060000,
    exit: { reason: "STOP_LOSS", fillPrice: 97.5, netPnl: -0.35, realizedR: -0.35 }
  });
  assert.equal(body.p_trade_id, "trade-1");
  assert.equal(body.p_closure.exit.reason, "STOP_LOSS");
  assert.equal(result.closed, true);
  assert.equal(result.trade.exit.fillPrice, "97.500000000000000123");
  assert.equal(result.trade.exit.netPnl, "-0.350000000000000019");
});

test("does not leak credentials and surfaces safe HTTP errors", async () => {
  const store = new SupabasePaperTradeStore({
    url: "https://visiontrade.supabase.co",
    serviceRoleKey: "server-secret",
    fetchImpl: async () => response({ message: "permission denied" }, false, 403)
  });
  await assert.rejects(store.findById("trade-1"), /permission denied/);
});

test("listOpenTrades calls the service-only RPC and maps exact decimal strings", async () => {
  let request;
  const store = new SupabasePaperTradeStore({
    url: "https://visiontrade.supabase.co",
    serviceRoleKey: "server-secret",
    fetchImpl: async (url, options) => {
      request = { url, options };
      return response([tradeRow]);
    }
  });
  const trades = await store.listOpenTrades();
  assert.equal(request.url, "https://visiontrade.supabase.co/rest/v1/rpc/list_open_paper_trades");
  assert.deepEqual(JSON.parse(request.options.body), {});
  assert.equal(trades.length, 1);
  assert.equal(trades[0].status, "OPEN");
  assert.equal(trades[0].quantity, "0.100000000000000019");
});

test("listOpenTrades rejects malformed RPC payloads", async () => {
  const store = new SupabasePaperTradeStore({
    url: "https://visiontrade.supabase.co",
    serviceRoleKey: "server-secret",
    fetchImpl: async () => response({ unexpected: true })
  });
  await assert.rejects(store.listOpenTrades(), /invalid open paper-trade list/);
});
