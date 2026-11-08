# VisionTrade Crypto Scalping — Architecture & Strategy v1.0

**Status:** Architecture baseline — approved market/mode/risk decisions; strategy thresholds remain configurable and require backtesting.  
**Repository branch:** `feature/crypto-scalping-foundation`  
**Safety boundary:** Paper trading only. No authenticated trading endpoints, API trading keys, or real-money order placement in this phase.

## 1. Locked product decisions

| Setting | Decision |
|---|---|
| Markets | Bybit Spot and USDT Linear Perpetual |
| Operating mode | Paper trading only |
| Risk per trade | 1.0% of current paper-account equity |
| Structure timeframe | 5 minutes |
| Entry timeframe | 1 minute |
| Minimum net reward/risk | 2.0R |
| Preferred reward/risk | 2.0R–3.0R |
| Signal outcomes | BUY, SELL, NO_TRADE |
| Live order execution | Disabled |

Spot semantics: BUY opens a simulated long position; SELL closes/reduces a simulated Spot holding and must never create a naked short. Perpetual semantics: BUY opens a simulated long; SELL opens a simulated short. Position accounting and PnL must be market-specific.

## 2. Strategy definition

### 2.1 Primary setup: range support/resistance scalping

The initial strategy trades reactions inside a confirmed 5m range, not arbitrary indicator crossovers.

**5m structure gate**
- Require sufficient closed candles and valid OHLCV data.
- Detect swing highs/lows using confirmed pivots; do not use future candles or repaint previously emitted zones.
- Cluster nearby pivots into support/resistance zones using a volatility-aware tolerance (ATR-derived, configurable).
- Require a valid range with support below resistance and enough room for a trade after estimated costs.
- Use ADX(14) as a regime filter. A low/weak ADX supports range conditions; strong ADX or a directional breakout invalidates range-fade entries.
- EMA(20/50) provides directional context; it is not a standalone entry trigger.

**1m entry gate**
- Wait for the 1m candle to close; never trigger from an unfinished candle.
- Long setup: price tests/enters support, then a closed bullish rejection/engulfing or reclaim confirmation appears.
- Short setup: price tests/enters resistance, then a closed bearish rejection/engulfing or rejection confirmation appears.
- Require confirmation from a configurable confluence set: RSI(7), VWAP, relative volume versus SMA(20), EMA(20/50) alignment, and candle structure.
- No signal if the data is stale, spread is excessive, liquidity is insufficient, the zone has already been invalidated, or the entry has moved away from its permitted zone.

### 2.2 Risk/reward gate
- Risk budget = current paper equity × 0.01.
- Stop is placed beyond the invalidation/structure level with an ATR-based safety buffer.
- Target must be based on the next valid opposing zone or a configured 2R–3R target, whichever is reachable without violating the minimum net R:R.
- Calculate net R:R after estimated maker/taker fees, spread, slippage and (for perpetual simulations) funding where applicable.
- Reject the setup if net R:R < 2.0, stop distance is zero/invalid, quantity rounds below exchange minimum, or the available reward is blocked by nearby structure.
- Risk calculation must use instrument metadata (quantity step, minimum quantity/notional, tick size, contract multiplier and settlement currency). Do not reuse Forex pip assumptions as a universal crypto contract model.

### 2.3 Regime and safety rules
- Range-fade entries are disabled during strong directional trend, abnormal volatility, stale/disconnected market data, exchange maintenance, or spread/liquidity failures.
- Use closed-candle confirmation and deterministic, timestamped inputs.
- Deduplicate alerts by market + symbol + direction + setup-zone/version.
- Expire a signal when its entry zone is missed, the setup is invalidated, or its time-to-live elapses.
- Enforce configurable portfolio limits, daily loss stop, cooldown and correlated-exposure controls before paper entry. Initial values must be validated with backtests rather than treated as proven profitable settings.

## 3. Market data responsibilities

### Bybit
Primary source for executable-market context:
- Spot instruments and USDT Linear instruments.
- Instrument metadata and trading filters.
- 1m/5m klines, ticker, bid/ask, order book and trade/volume data.
- Public WebSocket streams for live market updates.
- REST snapshots for initial history, reconnect recovery and reconciliation.

Treat WebSocket candle `confirm=true` as the closed-candle boundary. Validate sequence/time freshness and reconcile gaps after reconnect.

### CoinGecko
Discovery and enrichment only:
- Discover assets and candidate symbols using market/asset metadata, market cap and volume.
- Verify every candidate against Bybit instrument metadata before scanning.
- Do not use CoinGecko as the source for 1m/5m entry prices, bid/ask, order book or paper fills.

### API security
- Public Bybit market data can be consumed without trading credentials.
- CoinGecko credentials, if required by the selected plan, must remain server-side.
- No API secrets in Android source, resources, BuildConfig committed values, logs, notifications or repository history.
- No exchange trade permissions or withdrawal permissions are needed for paper trading.

## 4. Target architecture

```
CoinGecko discovery ─┐
                    ├─> Backend discovery + pair eligibility
Bybit REST/WS ──────┘             |
                                  v
                         Market data normalizer
                                  |
                         Quality / liquidity filters
                                  |
                         5m structure & regime engine
                                  |
                         1m entry confirmation engine
                                  |
                         Strategy + risk validation
                                  |
                    ┌─────────────┴─────────────┐
                    v                           v
             Paper execution engine       Signal event service
                    |                           |
             Trade ledger / journal       Push notification service
                    |                           |
                    └─────────────┬─────────────┘
                                  v
                         Persistent database
                                  |
                         Authenticated app API
                                  |
                         VisionTrade Android app
```

The scanner must run independently of the Android process. The Android app is a dashboard/client and must not be the only place where scanning runs. The backend runtime must support durable workers or a persistent WebSocket consumer; short-lived scheduled functions alone are not sufficient for a continuous scanner.

Backend hosting/provider is intentionally not locked in this document. Select it after reviewing operational cost, persistent-worker support, database, secrets, push delivery and deployment controls.

## 5. Android integration boundary

Keep the existing Kotlin, Jetpack Compose, MVVM and Clean Architecture application. Add feature packages behind interfaces rather than putting exchange logic inside composables or MainActivity.

Proposed feature boundaries:
- `domain/market`: instrument, candle, ticker, order-book and market-type models.
- `domain/scalping`: S/R zones, market regime, confluence, signal and invalidation rules.
- `domain/risk`: market-aware spot/perpetual sizing and net-cost model.
- `domain/paper`: simulated orders, fills, positions, fees, funding, realized/unrealized PnL.
- `data/remote`: authenticated backend API client; exchange credentials remain server-side.
- `data/local`: Room entities/DAOs for cached signals and trade journal.
- `ui/scanner`, `ui/signals`, `ui/paper`, `ui/performance`, `ui/settings`.

The Android app must request Internet permission for remote API access. Push notifications require notification permission handling and a backend push-token registration flow.

## 6. Paper execution model

- Simulate market/limit entry behavior using available bid/ask and conservative slippage assumptions; do not fill every order at candle close by default.
- Apply exchange fee schedules by market and simulated order type.
- Perpetual PnL must account for direction, leverage/margin, contract quantity and funding; leverage changes margin/liquidation exposure, not the 1% planned stop-loss budget.
- Store immutable signal snapshot, strategy version, market-data timestamps, entry/SL/TP, size, fee assumptions, fill model and exit reason for every trade.
- Support partial TP only if the execution model and trade ledger can represent partial quantities accurately.
- Use conservative intrabar ordering when historical candles touch both SL and TP and tick-level ordering is unavailable (SL-first assumption).

## 7. Observability and evaluation

Track separately for Spot and Perpetual:
- scanned pairs, eligible pairs, rejected-pair reasons and scanner latency;
- signals, expired signals, duplicate suppression and invalidations;
- paper fills, rejected fills, fees, slippage and funding;
- win rate, expectancy in R, average net R:R, profit factor, maximum drawdown and time in market;
- performance segmented by symbol, regime, session and strategy version.

Testing sequence:
1. Unit tests for indicators, pivots/zones, regime classification, risk sizing and cost calculations.
2. Deterministic replay tests with recorded market data.
3. Historical backtests with fees/slippage and no look-ahead bias.
4. Out-of-sample validation.
5. Forward paper trading and operational monitoring.
6. Review evidence before considering any future live-trading scope.

No performance target or profitability claim is assumed in advance.

## 8. Phased delivery

1. Repository/build baseline and code audit.
2. Crypto domain models and strategy specification tests.
3. Bybit market-data adapter and CoinGecko discovery adapter.
4. Backend scanner worker, persistence and authenticated API.
5. Spot/Perpetual paper execution and risk ledger.
6. Android scanner dashboard, signal details and notifications.
7. Backtesting, replay, forward paper validation and release hardening.

## 9. Existing repository findings (initial audit)

- Existing app is a single Android `:app` module using Kotlin, Compose, MVVM, Room and local deterministic analysis.
- Existing strategy is Forex-oriented and uses M15/H1/H4, EMA20/50, RSI14, ADX14 and engulfing checks. It is not the new 5m/1m crypto strategy.
- Existing risk calculator uses a Forex-oriented `pipValue` abstraction and does not model exchange lot/tick filters, fees, spread, slippage, perpetual funding or contract-specific PnL.
- Existing Android manifest does not declare Internet permission.
- Existing app dependencies do not include a market-data REST/WebSocket client or a push/backend client.
- Existing project has no backend service or GitHub Actions workflow in the inspected repository state.
- The Gradle wrapper JAR is present in the repository tree; an older verification note saying it is absent is stale. Build/test execution still needs to be run and recorded.
- The previous navigation fix is present on `fix/visiontrade-stability`; this feature branch is based on that fix and is not merged into `main`.

## 10. Acceptance criteria for this phase

- No live exchange orders can be submitted.
- Both Spot and USDT Linear instruments are modeled distinctly.
- Only closed 5m/1m candles can generate confirmed signals.
- No-look-ahead support/resistance and regime tests pass.
- 1% planned risk is respected after quantity rounding and estimated costs.
- Net R:R below 2.0 is rejected.
- Stale data, spread/liquidity failure, missing metadata and invalid zones return NO_TRADE.
- Signals are deduplicated, explainable, timestamped and expire correctly.
- Paper trades are reproducible from stored signal and market-data snapshots.
- Android can display scanner status, signals, paper positions and performance without owning the continuous scanner process.


## Cross-platform strategy parity gate

The Android Kotlin domain engine and the Node.js backend strategy engine are separate implementations and must not silently drift.

The first parity gate is a deterministic 80-candle indicator contract shared by the Android and backend unit-test suites. Both suites assert the same EMA20, EMA50, RSI7, ATR14, ADX14, VWAP, volume SMA20 and relative-volume outputs within a 1e-8 tolerance. The fixture uses closed one-minute candles with deterministic OHLCV values.

Parity requirements before signal generation is enabled:

1. Indicator formulas and warm-up periods must match.
2. Only closed candles may affect calculations.
3. Duplicate timestamps and mixed timeframes must fail closed.
4. Confirmed swing pivots must use the same left/right confirmation windows and tolerance clustering.
5. Strategy decisions must agree for the same 5m/1m market-data fixtures, including stale data, strong trends, incomplete history, Spot short rejection and conflicting confirmations.
6. Risk sizing and net reward-to-risk remain a separate mandatory gate; an indicator or strategy BUY/SELL is not an executable trade instruction.

Golden-value tests are a baseline, not a substitute for shared end-to-end signal fixtures. Do not enable backend signal publication or paper execution until the decision-level parity suite and the risk-engine tests both pass.
