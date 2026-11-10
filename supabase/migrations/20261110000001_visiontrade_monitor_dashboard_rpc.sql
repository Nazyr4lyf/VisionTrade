create or replace function public.visiontrade_monitor_dashboard()
returns jsonb
language sql
stable
security definer
set search_path = pg_catalog, public
as $function$
  select jsonb_build_object(
    'generated_at', now(),
    'snapshot', (
      select jsonb_build_object(
        'worker_running', s.worker_running,
        'last_scan_at', s.last_scan_at,
        'last_scan_pairs', s.last_scan_pairs,
        'actionable_signals', s.actionable_signals,
        'updated_at', s.updated_at,
        'telegram_connected_at', s.telegram_connected_at
      )
      from public.visiontrade_monitor_snapshot s
      where s.id = 'latest'
      limit 1
    ),
    'latest_scan', (
      select jsonb_build_object(
        'scanned_at', d.scanned_at,
        'market_pairs_discovered', d.market_pairs_discovered,
        'eligible_pairs', d.eligible_pairs,
        'scanned_pairs', d.scanned_pairs,
        'succeeded_pairs', d.succeeded_pairs,
        'failed_pairs', d.failed_pairs,
        'actionable_signals', d.actionable_signals,
        'stale_pairs', d.stale_pairs,
        'rejection_reasons', d.rejection_reasons
      )
      from public.visiontrade_scan_diagnostics d
      order by d.scanned_at desc
      limit 1
    ),
    'signal_history', coalesce((
      select jsonb_agg(
        jsonb_build_object(
          'symbol', e.symbol,
          'market_type', e.market_type,
          'action', e.action,
          'signal_candle_at', e.signal_candle_at,
          'signal_payload', e.signal_payload,
          'telegram_status', e.telegram_status,
          'telegram_attempts', e.telegram_attempts,
          'telegram_sent_at', e.telegram_sent_at
        ) order by e.signal_candle_at desc
      )
      from (
        select *
        from public.visiontrade_signal_events
        order by signal_candle_at desc
        limit 25
      ) e
    ), '[]'::jsonb)
  );
$function$;

comment on function public.visiontrade_monitor_dashboard() is
  'Read-only public market-monitor dashboard payload. Exposes no secrets or account data.';

revoke all on function public.visiontrade_monitor_dashboard() from public;
grant execute on function public.visiontrade_monitor_dashboard() to anon, authenticated;
