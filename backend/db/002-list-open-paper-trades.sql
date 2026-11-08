-- Incremental migration: service-only retrieval of active paper positions.
-- Apply to the dedicated VisionTrade Supabase project only.
-- SECURITY INVOKER ensures table privileges and RLS remain effective.
create or replace function public.list_open_paper_trades()
returns jsonb
language sql
stable
security invoker
set search_path = pg_catalog, private
as $$
  select coalesce(
    jsonb_agg(
      to_jsonb(t) || jsonb_build_object(
        'quantity', t.quantity::text,
        'entry_price', t.entry_price::text,
        'stop_loss_price', t.stop_loss_price::text,
        'take_profit_price', t.take_profit_price::text,
        'risk_budget', t.risk_budget::text,
        'estimated_loss_at_stop', t.estimated_loss_at_stop::text,
        'net_risk_reward', t.net_risk_reward::text,
        'exit_fill_price', t.exit_fill_price::text,
        'exit_net_pnl', t.exit_net_pnl::text,
        'exit_realized_r', t.exit_realized_r::text
      )
      order by t.opened_at asc, t.id asc
    ),
    '[]'::jsonb
  )
  from private.paper_trades as t
  where t.status = 'OPEN';
$$;

revoke all on function public.list_open_paper_trades() from public, anon, authenticated;
grant execute on function public.list_open_paper_trades() to service_role;
