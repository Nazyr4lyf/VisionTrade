# VisionTrade market-data worker

This Node.js 20+ worker is a backend-side market-data ingestion foundation for the Android app.

## Current safety boundary

- Public market data only: Bybit V5 REST and CoinGecko market discovery.
- Spot and USDT perpetual instrument discovery.
- Only closed 1-minute and 5-minute candles are marked eligible for strategy input.
- No exchange API keys, private endpoints, order placement, or live trading.
- The worker currently logs ingestion health only. It does **not** emit trading signals or write paper trades yet.

## Run

```bash
cd backend
npm test
npm start
```

Optional environment variables:

- `SCAN_INTERVAL_MS` (default 60000; minimum 15000)
- `DISCOVERY_INTERVAL_MS` (default 21600000)
- `MIN_TURNOVER_24H_USD` (default 5000000)
- `COINGECKO_DEMO_API_KEY` (optional demo API key)

## Next integration gates

1. Add backend parity tests for the Kotlin indicator, S/R, strategy, and net-RR engines before enabling signal decisions.
2. Add durable Supabase migrations, RLS, and idempotent paper-ledger writes after a dedicated VisionTrade project is selected.
3. Add authenticated app-to-backend APIs and notification delivery.
4. Deploy the worker to an always-on container/service. A normal serverless request function is not a substitute for a continuously running scanner.
5. Run paper-only end-to-end tests and verify separate Spot and Perpetual reporting.

The worker intentionally fails closed: provider errors are logged and no trade action is attempted.
