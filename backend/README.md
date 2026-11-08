# VisionTrade market-data worker

This Node.js 20+ worker is a backend-side market-data ingestion foundation for the Android app.

## Current safety boundary

- Public market data only: Bybit V5 REST and CoinGecko market discovery.
- Spot and USDT perpetual instrument discovery.
- Only closed 1-minute and 5-minute candles are marked eligible for strategy input.
- No exchange API keys, private endpoints, order placement, or live trading.
- The worker currently logs ingestion health only. It does **not** emit trading signals or write paper trades yet.
- An isolated backend `CryptoRiskEngine` now validates candidate sizing, exchange tick/step/minimums, Spot short restrictions, estimated fees/slippage/funding, the 1% equity risk budget, and the 2R net floor. It is not wired into the worker because the strategy does not yet produce validated stop-loss/take-profit levels.

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

1. Complete Kotlin/Node numeric parity for indicators, S/R zones, strategy decisions, and risk sizing.
2. Define and test non-repainting stop-loss/take-profit generation; only then connect the isolated risk gate to candidate signals.
3. Add a backend paper-position simulator and idempotent ledger, including gap-through-stop handling and separate Spot/Perpetual accounting.
4. Add durable Supabase migrations and RLS after a dedicated VisionTrade project is selected.
5. Add authenticated app-to-backend APIs and notification delivery.
6. Deploy the worker to an always-on container/service and run paper-only end-to-end tests.

The worker intentionally fails closed: provider errors are logged and no trade action is attempted.
