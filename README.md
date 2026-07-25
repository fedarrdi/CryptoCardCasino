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

Start the local PostgreSQL database:

```bash
docker compose up -d postgres
```

Then run the backend with the explicit development profile:

```bash
cd backend
SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run
```

Flyway applies the database migrations automatically when the backend starts.
The development profile connects to `localhost:5433/raretable` using the
credentials declared in `compose.yaml`.

Run the backend tests with:

```bash
cd backend
./mvnw test
```

The tests start an isolated PostgreSQL container and require Docker to be
running.

The default backend configuration is production-oriented. It requires
`RARETABLE_AUTH_DOMAIN`, `RARETABLE_AUTH_URI`, `RARETABLE_DATABASE_URL`,
`RARETABLE_DATABASE_USERNAME`, and `RARETABLE_DATABASE_PASSWORD`. Session
cookies are marked `Secure`.

The current wallet authentication API supports EIP-191 signatures from
externally owned accounts only. ERC-1271 contract wallets, including Safe
wallets, are not supported yet.
