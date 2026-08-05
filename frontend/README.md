# MetaMask authentication frontend

This frontend contains only the RareTable authentication experience. MetaMask
is the sole supported login method; there are no game, lobby, password, email,
social-login, or alternative-wallet screens in this application.

The login flow is:

1. Request the selected MetaMask account and chain ID.
2. Request a short-lived SIWE challenge from the backend.
3. Ask MetaMask to sign the challenge.
4. Exchange the signature for an HTTP session and CSRF credentials.

## Run locally

Start PostgreSQL and Redis from the repository root, then run the backend with
its development profile:

```bash
docker compose up -d postgres redis
cd backend
SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run
```

In another terminal, start the frontend on `http://localhost:5173`:

```bash
cd frontend
npm install
npm run dev
```

MetaMask must be installed in the browser. The backend currently accepts
externally owned accounts; ERC-1271 contract wallets are not supported.

## Checks

```bash
npm run lint
npm run build
```
