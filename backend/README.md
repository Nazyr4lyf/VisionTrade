# VisionTrade market-data worker

This Node.js 20+ worker is a backend-side market-data ingestion foundation for the Android app.

## Current safety boundary

- Public market data only: Bybit V5 REST and CoinGecko market discovery.
- Spot and USDT perpetual instrument discovery.
- Only closed 1-minute and 5-minute candles are marked eligible for strategy input.
- No exchange API keys, private endpoints, order placement, or live trading.
- The worker continuously scans eligible USDT Spot and USDT perpetual markets with the 5m range-regime filter and 1m entry confirmation. It evaluates closed candles only. It can send deduplicated BUY/SELL strategy-signal alerts to the configured Telegram chat. These are strategy signals only, not paper-trade approvals; no exchange order is placed. An explicitly enabled, separate paper-position monitor can simulate exits for existing Spot paper positions; it never opens positions.
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



## Telegram bot integration

The worker can connect to the Telegram Bot API using long polling. It is enabled only when both `TELEGRAM_BOT_TOKEN` and `TELEGRAM_CHAT_ID` are present.

- `/start` and `/help` confirm the bot connection and show available commands.
- `/status` reports worker state, latest scan time, number of market pairs requested, and actionable signal count.
- `/signals` lists the latest actionable strategy signals and their gross risk/reward validation status.
- Commands are accepted only from the configured `TELEGRAM_CHAT_ID`; messages are sent only to that chat.
- A startup notification confirms the worker connection.
- Telegram is a notification/control interface only. It does not place exchange orders.
- The worker runs in `LIVE_MARKET_MONITORING` mode. It evaluates the backend `ScalpingStrategyEngine` against each scanned market pair and sends only actionable BUY/SELL alerts. Repeated alerts for the same action on the same closed 1m candle are suppressed; failed Telegram deliveries remain eligible for retry on the next scan.
- Actionable signals are passed through the confirmed-zone `StopTargetEngine` for candidate stop-loss, take-profit, and gross 2R validation. The alert explicitly marks account risk sizing as pending until paper equity and verified fee/slippage assumptions are configured. A valid gross R multiple is not a net-risk approval.

Set these values in the backend host's private environment/secret settings. Never commit a bot token or put it in source code, workflow YAML, screenshots, or chat messages. If a token is exposed, revoke it with BotFather and replace it.

For local testing, set `TELEGRAM_BOT_TOKEN` and `TELEGRAM_CHAT_ID` in the process environment before running `npm start`. The bot uses long polling; do not run multiple pollers for the same bot token at once.
\n## Render background-worker deployment

The repository includes a root `Dockerfile` and `render.yaml` Blueprint for a dedicated, always-on Render Background Worker.

1. In Render, choose **New → Blueprint** and connect this repository.
2. Select branch `feature/crypto-scalping-foundation` for the review deployment; do not merge this branch into `main`.
3. Review the proposed service before creating it. The Blueprint uses the Dockerfile at the repository root, disables automatic deploys, and sets `PAPER_POSITION_MONITORING_ENABLED=false`.
4. The worker runs as the non-root `node` user. The Docker image build runs `npm test`; a failed test prevents a successful image build.
5. Do not add exchange API credentials. Live trading and exchange order placement are not part of this worker.
6. Do not add the Supabase service-role key yet. With monitoring disabled, the worker needs only public market-data access. Add the Supabase URL and service-role key to Render's private environment settings only after the read-only database check and a separate monitoring safety review.
7. Keep `PAPER_POSITION_MONITORING_ENABLED=false` for the first deployment. Do not enable Spot monitoring or paper trade entry until the controlled end-to-end validation is complete.

A Render Background Worker is a continuously running service and may incur hosting charges. Review the selected instance plan and its price in Render before confirming service creation. No Render service is created by committing these deployment files.

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
- `PAPER_MONITOR_CONCURRENCY` (default 3; range 1–10)\n- `TELEGRAM_BOT_TOKEN` (optional; private Telegram bot token)\n- `TELEGRAM_CHAT_ID` (optional; allowlisted private chat ID; required with bot token)

No service-role key has been added to this repository or to a client app.

## Next integration gates

1. Configure the project URL and service-role key in the backend hosting secret manager, then run a read-only RPC connectivity check.
2. Validate the opt-in Spot position monitor against paper fixtures and a controlled end-to-end test before setting `PAPER_POSITION_MONITORING_ENABLED=true`.
3. Implement historical funding-fee accounting before enabling USDT perpetual position monitoring; perpetuals are currently skipped safely.
4. Complete Kotlin/Node numeric parity for indicators, S/R zones, strategy decisions, and risk sizing.
5. Wire the isolated stop/target and 1% net-risk gate into strategy candidates, using explicitly configured paper-account equity and verified fee/slippage assumptions, before marking any signal as paper-trade approved.
6. Add authenticated app-to-backend APIs and Android notification delivery.
7. Deploy the worker to an always-on container/service and run paper-only end-to-end tests.

The worker runs `LIVE_MARKET_MONITORING`: it evaluates strategy signals, validates candidate stop/target levels and gross R, and can send Telegram alerts. The isolated `PaperTradeApprovalEngine` / 1% net-risk sizing gate is still not wired into the worker, so alerts are not approved paper-trade candidates and do not contain risk-sized quantities. No paper entry is automatically created. Live trading and exchange order placement are not implemented.
