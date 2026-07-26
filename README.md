# CryptoCardCasino

React frontend lives in `frontend/`.

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
