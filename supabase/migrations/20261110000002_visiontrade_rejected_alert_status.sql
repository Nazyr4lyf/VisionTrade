alter table public.visiontrade_signal_events
  drop constraint if exists visiontrade_signal_events_telegram_status_check;

alter table public.visiontrade_signal_events
  add constraint visiontrade_signal_events_telegram_status_check
  check (telegram_status in ('pending', 'sent', 'failed', 'disabled', 'rejected'));

comment on column public.visiontrade_signal_events.telegram_status is
  'Delivery state; rejected means the strategy setup failed risk validation and was deliberately not sent to Telegram.';
