# Frontend

React client for RareTable.

## Stack

- Vite
- React
- TypeScript

## Run locally

Start the backend with its local HTTP profile:

```bash
cd ../backend
SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run
```

Then start the frontend on `http://localhost:5173`:

```bash
npm install
npm run dev
```

MetaMask is required to authenticate. The current backend accepts externally
owned accounts (EOAs); ERC-1271 contract wallets are not supported yet.

## Build

```bash
npm run build
```

The main app entry is `src/App.tsx`.
