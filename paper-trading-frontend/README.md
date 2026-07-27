# RareTable Paper Frontend

Standalone React interface for wallet-authenticated BTC paper trading. It
includes:

- MetaMask wallet login with session restoration
- A persistent $10,000 paper account with server-authoritative balance, equity,
  available margin, and realized/unrealized PnL
- Cross-margin BTC market positions in either direction with 1–100× leverage
- Optional stop-loss and take-profit controls that can also be edited while a
  position is open
- An executable-quote PnL preview before a manual market close, plus open and
  closed trade views
- A responsive BTC/USDT candlestick chart with selectable 1h, 2h, 4h, 6h,
  8h, 12h, 1d, 3d, 1w, and 1M timeframes, built with TradingView Lightweight
  Charts 5.2
- Paginated historical candle loading followed by live WebSocket updates

The chart and trading workspace are only initialized after the wallet session
is authenticated.

## Trading contract

The workspace polls the authoritative portfolio every four seconds:

```text
GET /api/paper-trading/portfolio?closedTradeLimit=50
```

This keeps account values and open positions marked to the current executable
quote and makes server-triggered stop-loss or take-profit closures appear
without a page reload.

All trading controls are explicit: the order type is fixed to `MARKET`, margin
mode is fixed to `CROSS`, direction is `LONG` or `SHORT`, and leverage must be
between 1× and 100×. Positions are opened with:

```text
POST /api/paper-trading/positions
```

Each opening payload includes a client-generated `clientOrderId`. The backend
stores it with a per-user uniqueness constraint, so retrying the same intended
order cannot create a second position.

Risk controls and manual closes use:

```text
PATCH /api/paper-trading/positions/{id}/risk-controls
POST /api/paper-trading/positions/{id}/close
```

Every mutation returns a complete portfolio snapshot, which the UI applies
immediately. Authenticated mutations use the session CSRF token from
`GET /api/auth/csrf`; that token is acquired after session restoration or
wallet login, reused for the current session, and cleared on authentication
expiry or logout. Trading mutations also send the wallet ID displayed by the
workspace in `X-Paper-Trading-User-Id`; the backend rejects the request if
another tab has rotated the shared session to a different wallet. Portfolio
snapshots carry the authoritative user ID and are rejected by the UI on the
same mismatch. If a same-wallet session rotation invalidates CSRF, the UI
revalidates identity and refreshes the token but asks the user to resubmit
instead of blindly repeating a trading action.

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
GET /api/paper-trading/btc-candles?interval=1h
```

The authenticated response contains ordered BTC/USDT candles for the requested
timeframe:

```json
{
  "symbol": "BTCUSDT",
  "interval": "1h",
  "hasMore": true,
  "nextBefore": 1785106800,
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

The initial request returns the latest configured page. When the user pans
within 100 candles of the chart's left edge, the frontend requests the next
older page:

```text
GET /api/paper-trading/btc-candles?interval=1h&before=1785106800&limit=1000
```

`before` is an exclusive epoch-seconds cursor. When `hasMore` is true,
`nextBefore` is the oldest candle's timestamp in that response. Each response
is merged by timestamp in ascending order and prepended without moving the
user's current viewport. Only one historical request can be active at a time,
and loading stops when `hasMore` is false. Already-loaded history remains in
the chart through live updates and WebSocket reconciliation.

Changing the timeframe cancels both active history requests, closes the prior
WebSocket, clears its candles, and creates a new interval-scoped chart session.
Responses and socket messages from an old interval cannot update the newly
selected chart.

After history is loaded, the chart connects to the matching interval stream:

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
