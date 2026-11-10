create table if not exists public.visiontrade_signal_events (
  id bigint generated always as identity primary key,
  market_type text not null check (market_type in ('SPOT','USDT_PERPETUAL')),
  symbol text not null,
  action text not null check (action in ('BUY','SELL')),
  signal_candle_at timestamptz not null,
  signal_payload jsonb not null default '{}'::jsonb,
  telegram_status text not null default 'pending' check (telegram_status in ('pending','sent','failed','disabled')),
  telegram_attempts integer not null default 0 check (telegram_attempts >= 0),
  telegram_next_attempt_at timestamptz not null default now(),
  telegram_last_error text,
  telegram_message_id bigint,
  telegram_sent_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (market_type, symbol, action, signal_candle_at)
);
create index if not exists visiontrade_signal_events_recent_idx on public.visiontrade_signal_events (signal_candle_at desc);
create index if not exists visiontrade_signal_events_retry_idx on public.visiontrade_signal_events (telegram_status, telegram_next_attempt_at) where telegram_status in ('pending','failed');
create table if not exists public.visiontrade_scan_diagnostics (
  id bigint generated always as identity primary key,
  scanned_at timestamptz not null,
  market_pairs_discovered integer not null default 0,
  eligible_pairs integer not null default 0,
  scanned_pairs integer not null default 0,
  succeeded_pairs integer not null default 0,
  failed_pairs integer not null default 0,
  actionable_signals integer not null default 0,
  stale_pairs integer not null default 0,
  rejection_reasons jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now()
);
create index if not exists visiontrade_scan_diagnostics_recent_idx on public.visiontrade_scan_diagnostics (scanned_at desc);
alter table public.visiontrade_signal_events enable row level security;
alter table public.visiontrade_scan_diagnostics enable row level security;
revoke all on public.visiontrade_signal_events from anon, authenticated;
revoke all on public.visiontrade_scan_diagnostics from anon, authenticated;
grant all on public.visiontrade_signal_events to service_role;
grant all on public.visiontrade_scan_diagnostics to service_role;
grant usage, select on sequence public.visiontrade_signal_events_id_seq to service_role;
grant usage, select on sequence public.visiontrade_scan_diagnostics_id_seq to service_role;
drop policy if exists "service role manages VisionTrade signal events" on public.visiontrade_signal_events;
create policy "service role manages VisionTrade signal events" on public.visiontrade_signal_events for all to service_role using (true) with check (true);
drop policy if exists "service role manages VisionTrade scan diagnostics" on public.visiontrade_scan_diagnostics;
create policy "service role manages VisionTrade scan diagnostics" on public.visiontrade_scan_diagnostics for all to service_role using (true) with check (true);
