# CryptoCardCasino

The card-game frontend lives in `frontend/`. The standalone paper-trading
interface lives in `paper-trading-frontend/`.

## Paper trading frontend

The paper-trading interface supports MetaMask login, a persistent $10,000
simulated trading account, and a live candlestick chart with every supported
Binance Spot timeframe from one hour through one month. Authenticated users can
open BTCUSDT long or short market positions with 1–100× leverage, shared cross
margin, and optional stop-loss/take-profit controls; monitor executable-close
unrealized PnL; edit risk controls; close positions; and review durable trade
history.

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

On its first start, the backend downloads Binance's closed BTCUSDT candles for
`1h`, `2h`, `4h`, `6h`, `8h`, `12h`, `1d`, `3d`, `1w`, and `1M` into
PostgreSQL. Later starts resume each timeframe after its latest stored candle.
One combined Binance WebSocket connection supplies current candles, aggregate
BTC trades, and the live best bid/ask. Candles are broadcast to connected
browsers, aggregate trades drive server-side risk controls in chronological
order, and the book ticker maintains the executable quote without making an
external HTTP call inside a database transaction. Periodic reconciliation
repairs candle data missed during a disconnect. The history API returns the
latest 2,000 stored candles for the selected timeframe; the full history
remains durable in PostgreSQL. As a user drags the chart left, the frontend
requests older 1,000-candle pages from PostgreSQL with an exclusive timestamp
cursor and stops after reaching Binance's first candle.

The backend paper-trading code is grouped by responsibility:

- `paper_trading/api` contains HTTP endpoints and response records.
- `paper_trading/market_data` contains the candle service and its contracts.
- `paper_trading/market_data/binance` contains Binance ingestion clients and
  lifecycle configuration.
- `paper_trading/market_data/persistence` contains PostgreSQL repositories.
- `paper_trading/market_data/websocket` contains browser WebSocket delivery.
- `paper_trading/price` contains the atomic live bid/ask snapshot.

The market-data pipeline currently assumes one backend instance. When the API
is scaled to multiple instances, run ingestion on one elected worker, disable
it on API-only replicas with `RARETABLE_MARKET_DATA_ENABLED=false`, and fan out
updates through Redis Pub/Sub.

Redis stores HTTP sessions, five-minute SIWE login challenges, and distributed
authentication rate-limit counters. Session inactivity expires after 30
minutes, while the absolute authenticated-session lifetime is eight hours.

## Paper-trading accounting

Each user has one PostgreSQL-backed paper account. Its wallet balance starts at
`$10,000` and changes only when PnL is realized:

- requested notional = margin × leverage
- equity = wallet balance + unrealized PnL across every open position
- available margin = equity − margin committed across every open position
- long positions open at the current ask and close/mark at the current bid
- short positions open at the current bid and close/mark at the current ask

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

Automatic liquidation is not part of this first version because a maintenance
margin schedule has not been defined. Open losses remain account-wide and can
make available margin negative; no further position can be admitted until
available margin recovers.

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
