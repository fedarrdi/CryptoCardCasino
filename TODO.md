# TODO

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

- [x] Bound login challenge storage.
  - Store challenges in Redis with a five-minute TTL.
  - Create challenges with `SET NX` and consume them atomically with `GETDEL`.
  - Bound challenge creation through the global distributed rate limit.
- [x] Add distributed rate limits to the anonymous authentication endpoints.
  - Rate-limit challenge creation by source and apply a global safety limit.
  - Rate-limit signature verification by source and wallet address.
  - Consume each nonce atomically on its first verification attempt.
  - Return `429 Too Many Requests` with `Retry-After`.
  - Use atomic Redis counters shared by every backend instance.
- [x] Make each login challenge single-attempt.
  - Atomically remove the challenge before performing elliptic-curve recovery.
  - Reject every subsequent verification request using the same nonce.
  - Test replay after successful and failed signature verification.
- [x] Enforce an absolute server-side session lifetime.
  - Store the authentication time in the session after successful wallet verification.
  - Add a request filter that invalidates sessions after a configurable absolute lifetime.
  - Keep the 30-minute inactivity timeout as a separate limit.
  - Return `401 Unauthorized` after expiration and make the frontend return to its disconnected state.
- [x] Move HTTP sessions to Spring Session backed by Redis before horizontal scaling.
  - Preserve the 30-minute inactivity timeout.
  - Preserve the absolute session lifetime.
  - Verify logout and expiration delete the server-side session.
  - Verify two backend instances can read the same authenticated session.
- [ ] Add Redis operational monitoring.
  - Alert on connection failures, memory pressure, evictions, and rejected authentication requests.
  - Track active session, login challenge, and rate-limit key counts.

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

- [x] Replace the frontend test login and client-supplied user UUIDs with the SIWE session flow.
  - Request a challenge, sign the returned message, create the session, and fetch the CSRF token.
  - Send the CSRF header on table and game mutations.
  - Use the authenticated endpoints that no longer contain `/users/{userId}`.
  - State the EOA-only limitation in the wallet connection UI.
  - Handle `401`, `403`, session expiration, and logout.
- [ ] Add a browser smoke test against real frontend and backend processes.
  - Use a deterministic test wallet through an injected EIP-1193 provider.
  - Cover challenge, signature, session cookie, CSRF, table creation, one game mutation, and logout.
  - Assert that anonymous mutation requests and replayed signatures fail.

## Table And User Capacity

- [ ] Add table creation quotas.
  - Allow a wallet to own only one waiting table at a time.
  - Add a configurable global limit for active tables.
  - Reject table creation when either limit is reached.
- [ ] Delete abandoned waiting tables.
  - Record table creation and last-activity times.
  - Extend scheduled cleanup to remove waiting tables after a configurable inactivity period.
  - Keep the existing closed-table retention and cleanup behavior.
- [ ] Add filtered and paginated table discovery.
  - Support bounded requests by game type, table status, page, and page size.
  - Enforce a maximum page size in the backend.
  - Make the frontend request only tables for the selected game.
  - Stop lobby polling when the page is hidden or unmounted.
  - Replace polling with WebSockets or server-sent events when real-time lobby updates are implemented.
- [x] Persist users outside the application process.
  - Store users in PostgreSQL with a unique wallet-address constraint.
  - Remove the permanent in-memory user maps.
- [ ] Protect first-time user creation from abuse.
  - Rate-limit authentication and first-time user creation.
