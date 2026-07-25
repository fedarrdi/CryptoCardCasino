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

Run the backend locally with the explicit development profile:

```bash
cd backend
SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run
```

The default backend configuration is production-oriented. It requires
`RARETABLE_AUTH_DOMAIN` and `RARETABLE_AUTH_URI`, and session cookies are
marked `Secure`.

The current wallet authentication API supports EIP-191 signatures from
externally owned accounts only. ERC-1271 contract wallets, including Safe
wallets, are not supported yet.
