# VisionTrade market-data worker

This Node.js 20+ worker is a backend-side market-data ingestion foundation for the Android app.

## Current safety boundary

- Public market data only: Bybit V5 REST and CoinGecko market discovery.
- Spot and USDT perpetual instrument discovery.
- Only closed 1-minute and 5-minute candles are marked eligible for strategy input.
- No exchange API keys, private endpoints, order placement, or live trading.
- The worker currently logs ingestion health only. It does **not** emit trading signals or write paper trades yet.
- An isolated backend `CryptoRiskEngine` and `PaperTradeApprovalEngine` validate candidate sizing, exchange tick/step/minimums, Spot short restrictions, estimated fees/slippage/funding, the 1% equity risk budget, and the 2R net floor. They are not wired into the worker.
- The paper-trade lifecycle coordinator and server-only Supabase PostgREST adapter are implemented.
- The VisionTrade paper-trade schema has been deployed to the dedicated Supabase project below; the pre-existing HMS and other account projects were not modified.
- The schema stores paper trades in the private `private` schema, enables RLS, grants no client-role access, and exposes service-role-only RPC functions. Atomic unique constraints prevent duplicate idempotency keys and more than one open position per symbol/market.
- The persistence adapter preserves PostgreSQL `NUMERIC` values as decimal strings when reading rows. Do not coerce persisted crypto quantities/prices to JavaScript `Number` for precision-sensitive accounting.

## Dedicated Supabase project

- Account: `NAXYUS vision Trade`
- Project reference: `jjcpaasizhczyqnozblo`
- Region: `eu-west-1`
- API URL: `https://jjcpaasizhczyqnozblo.supabase.co`
- Database migration: `visiontrade_paper_trade_ledger`

## Run

```bash
cd backend
npm test
npm start
```

## Environment variables

- `SCAN_INTERVAL_MS` (default 60000; minimum 15000)
- `DISCOVERY_INTERVAL_MS` (default 21600000)
- `MIN_TURNOVER_24H_USD` (default 5000000)
- `COINGECKO_DEMO_API_KEY` (optional demo API key)
- `SUPABASE_URL=https://jjcpaasizhczyqnozblo.supabase.co`
- `SUPABASE_SERVICE_ROLE_KEY` (must be stored only in the backend/worker secret manager; never commit it, expose it in a `NEXT_PUBLIC_` variable, or ship it to Android/browser clients)

No service-role key has been added to this repository or to a client app.

## Next integration gates

1. Configure the project URL and service-role key in the backend hosting secret manager, then run a read-only RPC connectivity check.
2. Complete Kotlin/Node numeric parity for indicators, S/R zones, strategy decisions, and risk sizing.
3. Define and test non-repainting stop-loss/take-profit generation; only then connect the isolated risk gate to candidate signals.
4. Complete separate Spot/Perpetual paper accounting and integrate the simulator with the approved-candidate lifecycle.
5. Add authenticated app-to-backend APIs and notification delivery.
6. Deploy the worker to an always-on container/service and run paper-only end-to-end tests.

The worker intentionally remains `MARKET_DATA_ONLY`: provider errors are logged and no trade action is attempted. Live trading is not implemented.
