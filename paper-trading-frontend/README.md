# RareTable Paper Frontend

Standalone React interface for wallet-authenticated BTC paper trading.

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
mainnet. Vite proxies `/api` to the backend at `http://localhost:8080`.
