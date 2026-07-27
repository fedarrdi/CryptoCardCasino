# RareTable Paper Frontend

Standalone React interface for wallet-authenticated BTC paper trading. It
includes:

- MetaMask wallet login with session restoration
- An on-demand BTC/USDT midpoint
- A responsive 1-hour candlestick chart built with TradingView Lightweight
  Charts 5.2
- Historical candle loading followed by live WebSocket updates

The chart is only initialized after the wallet session is authenticated.

## Run locally

Start PostgreSQL and Redis from the repository root:

```bash
docker compose up -d postgres redis
```

Start the backend:

```bash
cd backend
SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run
```

Then start this frontend in a second terminal:

```bash
cd paper-trading-frontend
npm install
npm run dev
```

Open `http://localhost:5173` with MetaMask installed and connected to Ethereum
mainnet. Vite proxies both HTTP API requests and WebSocket connections to the
backend at `http://localhost:8080`.

## Market-data contract

The frontend loads the initial chart with:

```text
GET /api/paper-trading/btc-candles
```

The authenticated response contains ordered 1-hour BTC/USDT candles:

```json
{
  "symbol": "BTCUSDT",
  "interval": "1h",
  "candles": [
    {
      "time": 1785106800,
      "open": 65420.1,
      "high": 65680.0,
      "low": 65390.25,
      "close": 65590.5,
      "volume": 142.82
    }
  ]
}
```

After history is loaded, the chart connects to:

```text
/ws/market-data/btcusdt/1h
```

Each message contains one candle plus `symbol`, `interval`, and `closed`.
Messages update the current bar or append the next bar. The socket opens while
history loads, and its messages are buffered by timestamp until that history is
on the chart. This avoids losing a candle at the REST/WebSocket handoff.

If the socket closes, the frontend reconnects with a capped exponential delay.
Every reconnect also reloads the authoritative history before applying its
buffered live updates, so a disconnect across an hour boundary cannot leave a
gap. Transient network and server-side synchronization errors use the same
retry policy; authentication failures stop the chart immediately.

The live feed has connection and inactivity timers. A socket that cannot
connect is replaced, while an open socket with no accepted candle update is
shown as waiting rather than live. Updates older than the latest chart candle
are ignored, and an open update cannot overwrite a candle that is already
closed.

While the chart is active, the wallet session is revalidated with the backend
every five minutes. This check does not contact Binance.

## Production build

```bash
npm run build
```

The production server or reverse proxy must route both `/api` and `/ws` to the
Spring backend. The `/ws` route must support the WebSocket upgrade.
