-- VisionTrade paper-trade ledger storage.
-- Apply only to a dedicated VisionTrade Supabase project after project selection.
-- All writes are server-only through service_role-authenticated RPC functions.
create schema if not exists private;

create table if not exists private.paper_trades (
  id text primary key,
  idempotency_key text not null unique,
  status text not null check (status in ('OPEN', 'CLOSED')),
  symbol text not null check (length(symbol) between 1 and 40),
  market_type text not null check (market_type in ('SPOT', 'USDT_PERPETUAL')),
  side text not null check (side in ('LONG', 'SHORT')),
  quantity numeric(38, 18) not null check (quantity > 0),
  entry_price numeric(38, 18) not null check (entry_price > 0),
  stop_loss_price numeric(38, 18) not null check (stop_loss_price > 0),
  take_profit_price numeric(38, 18) not null check (take_profit_price > 0),
  risk_budget numeric(38, 18) not null check (risk_budget > 0),
  estimated_loss_at_stop numeric(38, 18) not null check (estimated_loss_at_stop >= 0),
  net_risk_reward numeric(18, 8) not null check (net_risk_reward >= 2),
  opened_at timestamptz not null,
  closed_at timestamptz,
  exit_reason text check (exit_reason in ('STOP_LOSS', 'TAKE_PROFIT')),
  exit_fill_price numeric(38, 18),
  exit_net_pnl numeric(38, 18),
  exit_realized_r numeric(18, 8),
  created_at timestamptz not null default now(),
  constraint paper_trades_spot_no_short check (market_type <> 'SPOT' or side = 'LONG'),
  constraint paper_trades_risk_budget check (estimated_loss_at_stop <= risk_budget + 0.00000001),
  constraint paper_trades_lifecycle check (
    (status = 'OPEN' and closed_at is null and exit_reason is null
      and exit_fill_price is null and exit_net_pnl is null and exit_realized_r is null)
    or
    (status = 'CLOSED' and closed_at is not null and exit_reason is not null
      and exit_fill_price > 0 and exit_net_pnl is not null and exit_realized_r is not null)
  )
);

-- A single active position per exchange instrument, regardless of direction.
create unique index if not exists paper_trades_one_open_per_instrument
  on private.paper_trades (symbol, market_type)
  where status = 'OPEN';

create index if not exists paper_trades_symbol_opened_at
  on private.paper_trades (symbol, opened_at desc);

alter table private.paper_trades enable row level security;
revoke all on schema private from public, anon, authenticated;
revoke all on private.paper_trades from public, anon, authenticated;
grant usage on schema private to service_role;
grant select, insert, update on private.paper_trades to service_role;

create or replace function public.create_open_paper_trade(p_trade jsonb)
returns jsonb
language plpgsql
security invoker
set search_path = pg_catalog, private
as $$
declare
  v_trade private.paper_trades%rowtype;
begin
  insert into private.paper_trades (
    id, idempotency_key, status, symbol, market_type, side, quantity,
    entry_price, stop_loss_price, take_profit_price, risk_budget,
    estimated_loss_at_stop, net_risk_reward, opened_at
  ) values (
    p_trade->>'id',
    p_trade->>'idempotencyKey',
    'OPEN',
    p_trade->>'symbol',
    p_trade->>'marketType',
    p_trade->>'side',
    (p_trade->>'quantity')::numeric,
    (p_trade->>'entryPrice')::numeric,
    (p_trade->>'stopLossPrice')::numeric,
    (p_trade->>'takeProfitPrice')::numeric,
    (p_trade->>'riskBudget')::numeric,
    (p_trade->>'estimatedLossAtStop')::numeric,
    (p_trade->>'netRiskReward')::numeric,
    to_timestamp((p_trade->>'openedAt')::double precision / 1000.0)
  )
  on conflict do nothing
  returning * into v_trade;

  if found then
    return jsonb_build_object('created', true, 'reason', null, 'trade', to_jsonb(v_trade));
  end if;

  select * into v_trade
  from private.paper_trades
  where idempotency_key = p_trade->>'idempotencyKey';

  if found then
    return jsonb_build_object('created', false, 'reason', 'DUPLICATE_IDEMPOTENCY_KEY', 'trade', to_jsonb(v_trade));
  end if;

  select * into v_trade
  from private.paper_trades
  where symbol = p_trade->>'symbol'
    and market_type = p_trade->>'marketType'
    and status = 'OPEN'
  limit 1;

  if found then
    return jsonb_build_object('created', false, 'reason', 'OPEN_POSITION_EXISTS', 'trade', to_jsonb(v_trade));
  end if;

  raise exception 'Paper trade insert conflicted with an unknown unique constraint';
end;
$$;

create or replace function public.get_paper_trade_by_id(p_trade_id text)
returns jsonb
language sql
stable
security invoker
set search_path = pg_catalog, private
as $$
  select to_jsonb(t)
  from private.paper_trades as t
  where t.id = p_trade_id;
$$;

create or replace function public.close_paper_trade(p_trade_id text, p_closure jsonb)
returns jsonb
language plpgsql
security invoker
set search_path = pg_catalog, private
as $$
declare
  v_trade private.paper_trades%rowtype;
begin
  update private.paper_trades
  set status = 'CLOSED',
      closed_at = to_timestamp((p_closure->>'closedAt')::double precision / 1000.0),
      exit_reason = p_closure#>>'{exit,reason}',
      exit_fill_price = (p_closure#>>'{exit,fillPrice}')::numeric,
      exit_net_pnl = (p_closure#>>'{exit,netPnl}')::numeric,
      exit_realized_r = (p_closure#>>'{exit,realizedR}')::numeric
  where id = p_trade_id and status = 'OPEN'
  returning * into v_trade;

  if found then
    return jsonb_build_object('closed', true, 'reason', null, 'trade', to_jsonb(v_trade));
  end if;

  select * into v_trade from private.paper_trades where id = p_trade_id;
  if not found then
    return jsonb_build_object('closed', false, 'reason', 'TRADE_NOT_FOUND', 'trade', null);
  end if;

  return jsonb_build_object('closed', false, 'reason', 'ALREADY_CLOSED', 'trade', to_jsonb(v_trade));
end;
$$;

revoke all on function public.create_open_paper_trade(jsonb) from public, anon, authenticated;
revoke all on function public.get_paper_trade_by_id(text) from public, anon, authenticated;
revoke all on function public.close_paper_trade(text, jsonb) from public, anon, authenticated;
grant execute on function public.create_open_paper_trade(jsonb) to service_role;
grant execute on function public.get_paper_trade_by_id(text) to service_role;
grant execute on function public.close_paper_trade(text, jsonb) to service_role;
