# VisionTrade market-data worker

This Node.js 20+ worker is a backend-side market-data ingestion foundation for the Android app.

## Current safety boundary

- Public market data only: Bybit V5 REST and CoinGecko market discovery.
- Spot and USDT perpetual instrument discovery.
- Only closed 1-minute and 5-minute candles are marked eligible for strategy input.
- No exchange API keys, private endpoints, order placement, or live trading.
- By default the worker logs market-data ingestion health only. An explicitly enabled, separate paper-position monitor can simulate exits for existing Spot paper positions; it never opens positions or emits trade-entry signals.
- An isolated backend `CryptoRiskEngine` and `PaperTradeApprovalEngine` validate candidate sizing, exchange tick/step/minimums, Spot short restrictions, estimated fees/slippage/funding, the 1% equity risk budget, and the 2R net floor. They are not wired into the worker.
- The paper-trade lifecycle coordinator and server-only Supabase PostgREST adapter are implemented.
- The VisionTrade paper-trade schema has been deployed to the dedicated Supabase project below; the pre-existing HMS and other account projects were not modified.
- The schema stores paper trades in the private `private` schema, enables RLS, grants no client-role access, and exposes service-role-only RPC functions. Atomic unique constraints prevent duplicate idempotency keys and more than one open position per symbol/market.
- The persistence adapter preserves PostgreSQL `NUMERIC` values as decimal strings when reading rows. The open-trades RPC explicitly casts numeric fields to text before JSON serialization; do not coerce persisted crypto quantities/prices to JavaScript `Number` for precision-sensitive accounting.
- The server-only store and ledger can list durable OPEN paper positions through `list_open_paper_trades`.
- An opt-in, disabled-by-default worker monitor can inspect persisted Spot paper positions using post-entry 1m closed candles and simulate exits. It never creates new trades or sends orders.
- The monitor fails closed on stale/incomplete/malformed market data. USDT perpetual positions are skipped until historical funding-cost accounting is implemented; it never assumes zero funding for derivatives.
- Paper monitoring requires explicit Spot fee/slippage assumptions and the server-only Supabase service-role secret. The feature remains disabled unless `PAPER_POSITION_MONITORING_ENABLED=true`.

## Dedicated Supabase project

- Account: `NAXYUS vision Trade`
- Project reference: `jjcpaasizhczyqnozblo`
- Region: `eu-west-1`
- API URL: `https://jjcpaasizhczyqnozblo.supabase.co`
- Database migrations: `visiontrade_paper_trade_ledger` and `002-list-open-paper-trades.sql`

## Run

```bash
cd backend
npm test
npm start
```

## Read-only Supabase connectivity check

After setting `SUPABASE_URL` and `SUPABASE_SERVICE_ROLE_KEY` in the backend host's secret manager, run:

```bash
npm run verify:supabase
```

This calls only `get_paper_trade_by_id` with a reserved sentinel ID. It performs no inserts, updates, or deletes and never prints the key.

## Environment variables

- `SCAN_INTERVAL_MS` (default 60000; minimum 15000)
- `DISCOVERY_INTERVAL_MS` (default 21600000)
- `MIN_TURNOVER_24H_USD` (default 5000000)
- `COINGECKO_DEMO_API_KEY` (optional demo API key)
- `SUPABASE_URL=https://jjcpaasizhczyqnozblo.supabase.co`
- `SUPABASE_SERVICE_ROLE_KEY` (must be stored only in the backend/worker secret manager; never commit it, expose it in a `NEXT_PUBLIC_` variable, or ship it to Android/browser clients)
- `PAPER_POSITION_MONITORING_ENABLED` (default `false`; set `true` only after backend validation and secret configuration)
- `PAPER_SPOT_FEE_RATE` (required and non-negative when monitoring is enabled; decimal fraction, e.g. 0.001 means 0.1%)
- `PAPER_SPOT_SLIPPAGE_BPS` (required and non-negative when monitoring is enabled)
- `PAPER_MAX_CANDLE_AGE_MS` (default 180000)
- `PAPER_MONITOR_CONCURRENCY` (default 3; range 1–10)

No service-role key has been added to this repository or to a client app.

## Next integration gates

1. Configure the project URL and service-role key in the backend hosting secret manager, then run a read-only RPC connectivity check.
2. Validate the opt-in Spot position monitor against paper fixtures and a controlled end-to-end test before setting `PAPER_POSITION_MONITORING_ENABLED=true`.
3. Implement historical funding-fee accounting before enabling USDT perpetual position monitoring; perpetuals are currently skipped safely.
4. Complete Kotlin/Node numeric parity for indicators, S/R zones, strategy decisions, and risk sizing.
5. Define and test non-repainting stop-loss/take-profit generation; only then connect the isolated risk gate to candidate signals.
6. Add authenticated app-to-backend APIs and notification delivery.
7. Deploy the worker to an always-on container/service and run paper-only end-to-end tests.

The worker intentionally remains `MARKET_DATA_ONLY`: provider errors are logged and no trade action is attempted. Live trading is not implemented.
