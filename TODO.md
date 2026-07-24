# TODO

## Tien Len

- [ ] Choose the exact regional rules the platform will support, then implement:
  - Chopping a `2` with four of a kind or valid consecutive-pair combinations.
  - Consecutive-pair bombs.
  - Instant-win starting hands.
  - The rule governing whether a `2` may be played as a player's final card.

## Authentication And Sessions

### Immediate Code Corrections

- [x] Require authentication for `GET /api/auth/csrf`.
  - The frontend only needs a CSRF token after `POST /api/auth/sessions` succeeds.
  - Verify that anonymous requests receive `401` and do not create `JSESSIONID`.
- [x] Separate production and local-development transport configuration.
  - Make HTTPS and `Secure` session cookies mandatory in the default configuration.
  - Put `http://localhost:5173` and `Secure=false` in an explicit `dev` profile.
  - Make production startup fail when the SIWE domain or URI is not configured.
- [x] Make the SIWE message origin match the configured URI exactly.
  - Include the configured scheme in the SIWE authority line.
  - Add tests for the explicit HTTP development origin and HTTPS production origin.
- [x] Explicitly document that the first authentication version supports EOA wallets only.
  - State the limitation in the authentication API contract.
  - Do not infer that an address is an EOA or contract without an authoritative chain RPC lookup.
  - Do not claim Safe or other contract-wallet support until ERC-1271 verification exists.
- [x] Remove unused authentication scaffolding.
  - Remove `spring-security-test` while no test imports it.
  - Remove unused `chainId` and `issuedAt` fields from `LoginChallenge`.
  - Store only `userId` in `WalletPrincipal`.
  - Remove `ROLE_USER` until an authorization rule uses it.
  - Replace test-only `UserService.login()` calls with wallet-user fixtures, then remove it.
  - Remove the unreachable signature byte-length check after exact-length validation.

### Abuse And Capacity Controls

- [ ] Move login challenges from the unbounded in-memory map to Redis.
  - Store each challenge under an unpredictable nonce with a five-minute Redis TTL.
  - Create and consume challenges atomically so a nonce can succeed only once.
  - Configure Redis memory limits, eviction behavior, key-count metrics, and alerts.
- [ ] Add distributed rate limits to the anonymous authentication endpoints.
  - Rate-limit challenge creation by source and apply a global safety limit.
  - Rate-limit signature verification by source and nonce.
  - Return `429 Too Many Requests` with `Retry-After`.
  - Use a Redis-backed limiter so limits remain correct across backend instances.
- [ ] Limit signature verification attempts per nonce.
  - Atomically reserve an attempt before performing elliptic-curve recovery.
  - Invalidate the challenge after the configured maximum number of failed attempts.
  - Test sequential and concurrent failed attempts.
- [ ] Move HTTP sessions to Spring Session backed by Redis before horizontal scaling.
  - Preserve the 30-minute inactivity timeout.
  - Verify logout and expiration delete the server-side session.
  - Verify two backend instances can read the same authenticated session.

### Wallet Compatibility And Dependencies

- [ ] Decide whether to support ERC-1271 contract wallets.
  - If supported, verify contract signatures against the contract on the SIWE `chain-id`.
  - Define how sessions are invalidated when contract authorization changes.
- [ ] Reduce the Ethereum signature verifier's runtime dependency surface.
  - Record the current Maven runtime dependency tree.
  - Evaluate a focused, maintained EIP-191/secp256k1 verification component.
  - Otherwise exclude only proven-unused Web3j transitives and protect the exclusions with signature tests.
  - Do not implement elliptic-curve cryptography manually.

### Frontend Contract And End-To-End Testing

- [ ] Replace the frontend test login and client-supplied user UUIDs with the SIWE session flow.
  - Request a challenge, sign the returned message, create the session, and fetch the CSRF token.
  - Send the CSRF header on table and game mutations.
  - Use the authenticated endpoints that no longer contain `/users/{userId}`.
  - State the EOA-only limitation in the wallet connection UI.
  - Handle `401`, `403`, session expiration, and logout.
- [ ] Add a browser smoke test against real frontend and backend processes.
  - Use a deterministic test wallet through an injected EIP-1193 provider.
  - Cover challenge, signature, session cookie, CSRF, table creation, one game mutation, and logout.
  - Assert that anonymous mutation requests and replayed signatures fail.
