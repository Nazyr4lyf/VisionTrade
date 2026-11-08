# VisionTrade live monitoring connection

The Android app reads the latest market-monitoring snapshot from the dedicated VisionTrade Supabase project. The market worker publishes snapshots after each scan. The public client can read only this market-status row; it cannot insert, update, or delete it.

## Database

The additive migration `visiontrade_monitor_snapshot` creates `public.visiontrade_monitor_snapshot`, enables RLS, grants only SELECT to `anon` and `authenticated`, and allows server-side writes through the Supabase `service_role`. Market signals in this row are public market-analysis output, not account or user data.

## Worker environment

Configure these as private environment variables on the background-worker host. Never put the service-role key in Android, GitHub source, or a chat message.

| Variable | Required | Purpose |
| --- | --- | --- |
| `SUPABASE_URL` | Yes for app sync | Project API URL |
| `SUPABASE_SERVICE_ROLE_KEY` | Yes for app sync | Server-only key used by the worker to upsert the latest snapshot |
| `TELEGRAM_BOT_TOKEN` | Yes for Telegram | BotFather token; rotate any token previously pasted into chat |
| `TELEGRAM_CHAT_ID` | Yes for Telegram | Your private Telegram chat ID |
| `SCAN_INTERVAL_MS` | Optional | Defaults to 60000 |
| `MAX_MARKET_PAIRS` | Optional | Defaults to 30 |
| `COINGECKO_DEMO_API_KEY` | Optional | CoinGecko demo key |

The worker starts without snapshot publishing if the two Supabase variables are absent. It continues scanning but the Android screen will show that no worker snapshot has arrived. A successful worker deployment plus the private variables above is required before the app can show live status.

## Android

The app uses the Supabase publishable key (safe for a public client when paired with RLS) and polls the single public snapshot every 15 seconds. It does not contain the service-role key. The UI reports an empty snapshot as “Waiting for the monitoring worker”; it does not invent live status.

## Safety

This system is market-data monitoring and signal notification only. Live exchange orders remain disabled. Paper approval is informational and does not open positions automatically.
