# CryptoCardCasino

The card-game frontend lives in `frontend/`. The standalone paper-trading
interface lives in `paper-trading-frontend/`.

## Paper trading frontend

The paper-trading interface supports MetaMask login, a persistent $10,000
simulated trading account, and a live candlestick chart with every supported
Binance USDⓈ-M BTCUSDT perpetual timeframe from one hour through one month.
Authenticated users can open BTCUSDT long or short market positions with
1–100× leverage, shared cross margin, and optional stop-loss/take-profit
controls; preview maintenance margin, fees, break-even, bankruptcy, and
cross-account liquidation before opening; monitor gross mark-price PnL and
estimated executable-close net PnL; edit risk controls; close positions; and
review durable trade, fee, funding, and liquidation history.

```bash
cd paper-trading-frontend
npm install
npm run dev
```

It runs on `http://localhost:5173` and proxies `/api` requests and `/ws`
WebSocket connections to the backend at `http://localhost:8080`. Run this
frontend separately from `frontend/`, because both development servers
intentionally use port `5173` to match the development SIWE domain.

## Frontend

This project uses Vite + React + TypeScript.

```bash
cd frontend
npm install
npm run dev
```

Useful scripts:

```bash
npm run dev
npm run build
npm run lint
```

## Backend

Start the local PostgreSQL and Redis services:

```bash
docker compose up -d postgres redis
```

Then run the backend with the explicit development profile:

```bash
cd backend
SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run
```

Flyway applies the database migrations automatically when the backend starts.
The development profile connects to PostgreSQL at `localhost:5433` and Redis
at `localhost:6379` using the settings declared in `compose.yaml`.

On its first start, the backend downloads Binance USDⓈ-M Futures' closed
BTCUSDT perpetual candles for `1h`, `2h`, `4h`, `6h`, `8h`, `12h`, `1d`,
`3d`, `1w`, and `1M` into PostgreSQL. Spot and perpetual candles are explicitly
partitioned by product type, so an existing Spot history cannot be presented
as perpetual history. Later starts resume each timeframe after its latest
stored perpetual candle.

Binance's routed market and public WebSocket endpoints supply perpetual
candles, aggregate trades, mark/index/funding updates, and best bid/ask. The
backend keeps these price roles separate:

- last price drives the displayed market price and optional SL/TP triggers;
- mark price drives unrealized PnL, maintenance margin, and liquidation;
- index price is retained and displayed as the fair-value reference;
- best bid/ask supplies simulated market fills and closing estimates.

Candles are broadcast to connected browsers, while last- and mark-price events
drive their respective server-side risk controls in chronological order. The
in-memory market snapshot is accepted only when all required components are
present and fresh; order execution never makes an external HTTP call while a
database transaction is open. Periodic reconciliation repairs candle data
missed during a disconnect and imports durable funding settlements. The
history API returns the latest 2,000 stored candles for the selected timeframe;
the full history remains durable in PostgreSQL. As a user drags the chart left,
the frontend requests older 1,000-candle pages from PostgreSQL with an
exclusive timestamp cursor and stops after reaching Binance Futures' first
candle.

The backend paper-trading code is grouped by responsibility:

- `paper_trading/api` contains HTTP endpoints and response records.
- `paper_trading/market_data` contains the candle service and its contracts.
- `paper_trading/market_data/binance` contains Binance ingestion clients and
  lifecycle configuration.
- `paper_trading/market_data/persistence` contains PostgreSQL repositories.
- `paper_trading/market_data/websocket` contains browser WebSocket delivery.
- `paper_trading/price` contains the atomic last, mark, index, funding, and
  bid/ask perpetual snapshot.

The market-data pipeline currently assumes one backend instance. When the API
is scaled to multiple instances, run ingestion on one elected worker, disable
it on API-only replicas with `RARETABLE_MARKET_DATA_ENABLED=false`, and fan out
updates through Redis Pub/Sub.

Redis stores HTTP sessions, five-minute SIWE login challenges, and distributed
authentication rate-limit counters. Session inactivity expires after 30
minutes, while the absolute authenticated-session lifetime is eight hours.

## Paper-trading accounting

Each user has one PostgreSQL-backed paper account. Its wallet balance starts at
`$10,000`; every balance mutation is recorded in an append-only account ledger:

- requested notional = margin × leverage
- gross unrealized PnL = side × quantity × (mark price − entry price)
- equity = wallet balance + gross unrealized PnL across open positions
- initial margin = current mark notional ÷ selected leverage
- available margin = equity − initial margin − estimated closing taker fees
- long positions open at ask and close at bid; shorts open at bid and close at
  ask
- net trade PnL = gross price PnL − entry fee − exit fee − liquidation fee
  + funding
- positive funding rates debit longs and credit shorts using settlement mark
  notional; negative rates reverse that flow

The immutable `RARETABLE_BTCUSDT_V1` paper-product policy defines the taker fee,
liquidation fee, full-liquidation mode, negative-balance policy, and continuous
maintenance-margin/leverage tiers. This local version is explicit because
Binance commission and leverage-bracket endpoints are signed and
account-specific; it is not presented as a user's live Binance fee tier.
Trades retain their policy version for auditability.

Maintenance margin is calculated from aggregate same-side mark notional using
the versioned tier's rate and cumulative maintenance amount. The order-preview
endpoint applies the same server-side admission calculation used by opening,
including aggregate tier leverage, entry and reserved closing fees,
post-order MMR, maximum admissible margin, break-even, bankruptcy, and
account-level liquidation estimates.

A mark-price event liquidates when account equity is less than or equal to
account maintenance margin. Version 1 deliberately performs full cross-account
liquidation: all open positions are closed at current executable book prices,
each receives close reason `LIQUIDATION`, and both the normal market-exit taker
fee and configured liquidation fee are charged. If fills and fees leave a
negative wallet, an explicit insurance-credit ledger entry floors the paper
balance at zero. Liquidation events retain the triggering last, mark, and index
prices, pre-liquidation equity and maintenance margin, close result, and
insurance credit.

Funding history is imported from Binance Futures and stored before settlement.
The `(funding event, trade)` primary key makes settlement exact once, including
events recovered after a restart. A position participates only when it was open
at the settlement timestamp. Gross PnL, entry/exit/liquidation fees, funding
PnL, and net PnL remain separate in PostgreSQL and in the API.

All positions are explicitly stored as `MARKET` and `CROSS`. Account-row
locking serializes margin admission and close settlement, so concurrent
requests cannot allocate the same margin or realize one position twice.
Every opening request carries a durable client order UUID, making a repeated
request idempotent instead of creating a duplicate leveraged position.
Stop-loss and take-profit evaluation runs on the backend from chronological
aggregate-trade updates and therefore does not require the browser to remain
open. Immutable risk-control revisions associate delayed market events with
the controls that were effective at the event time; the latest live bid or ask
is then used as the simulated market fill.

Run the backend tests with:

```bash
cd backend
./mvnw test
```

The tests start isolated PostgreSQL and Redis containers and require Docker to
be running.

The default backend configuration is production-oriented. It requires
`RARETABLE_AUTH_DOMAIN`, `RARETABLE_AUTH_URI`, `RARETABLE_DATABASE_URL`,
`RARETABLE_DATABASE_USERNAME`, `RARETABLE_DATABASE_PASSWORD`, and
`RARETABLE_REDIS_URL`. Use a TLS Redis URL (`rediss://`) in production.
Session cookies are marked `Secure`.

The current wallet authentication API supports EIP-191 signatures from
externally owned accounts only. ERC-1271 contract wallets, including Safe
wallets, are not supported yet.
