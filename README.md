# CryptoCardCasino

The card-game frontend lives in `frontend/`. The standalone paper-trading
interface lives in `paper-trading-frontend/`.

## Paper trading frontend

The paper-trading interface supports MetaMask login, authenticated BTC/USDT
mid-price requests, and a live candlestick chart with every supported Binance
Spot timeframe from one hour through one month.

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
One combined Binance WebSocket connection supplies current candles to all
connected browser clients, while periodic reconciliation repairs data missed
during a disconnect. The history API returns the latest 2,000 stored candles
for the selected timeframe; the full history remains durable in PostgreSQL. As
a user drags the chart left, the frontend requests older 1,000-candle pages
from PostgreSQL with an exclusive timestamp cursor and stops after reaching
Binance's first candle.

The market-data pipeline currently assumes one backend instance. When the API
is scaled to multiple instances, run ingestion on one elected worker, disable
it on API-only replicas with `RARETABLE_MARKET_DATA_ENABLED=false`, and fan out
updates through Redis Pub/Sub.

Redis stores HTTP sessions, five-minute SIWE login challenges, and distributed
authentication rate-limit counters. Session inactivity expires after 30
minutes, while the absolute authenticated-session lifetime is eight hours.

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
