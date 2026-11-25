# User Logout Architecture

- **Purpose:** Define the implementation architecture for revoking user sessions and invalidating access and refresh tokens in the Identity and Access Management (IAM) service.
- **For:** Backend engineers implementing and reviewing the logout feature.
- **Date:** 2026-11-26

## Summary

The logout feature exposes an authenticated `POST /api/v1/auth/logout` endpoint over HTTPS. It verifies the caller's `Authorization: Bearer <accessToken>`, revokes the caller's refresh token in Redis only after confirming ownership, writes the access token's `jti` to the Redis blocklist for its remaining lifetime, and returns HTTP `200 OK` with `{"success": true}`.

## System Architecture

### What each component is responsible for

- **Security filter (`JwtAuthenticationFilter`):** Authenticates the incoming `Authorization: Bearer <accessToken>` header and rejects missing, expired, or already-blocklisted tokens with HTTP `401 Unauthorized`.
- **Controller layer (`AuthController`):** Exposes `POST /api/v1/auth/logout`, extracts the Bearer token and optional `LogoutRequest`, delegates to `AuthService.logout`, and returns HTTP `200 OK` with `{"success": true}`.
- **Service layer (`AuthService`):** Parses the caller's `userId`, `jti`, and `expiresAt` via `JwtService`, verifies refresh-token ownership in Redis before deleting `refresh:<tokenId>`, and blocklists `jti` for its remaining TTL.
- **Redis token store (`TokenStoreService`):** Looks up `refresh:<tokenId>`, deletes it when owned by the caller, and sets `blacklist:access:<jti>` -> `"revoked"` with `TTL = expiresAt - now`.

### How a logout request flows through the system

The diagram below shows an authenticated client calling `POST /api/v1/auth/logout` with its Bearer access token and optional refresh token. After `JwtAuthenticationFilter` validates the caller, `AuthService` confirms refresh-token ownership in Redis, deletes `refresh:<tokenId>`, writes `blacklist:access:<jti>` with remaining TTL, and returns HTTP `200 OK` with `{"success": true}`.

```mermaid
flowchart LR
    Client["Client"] -->|POST /api/v1/auth/logout + Bearer JWT| Filter["JwtAuthenticationFilter"]
    Filter --> Controller["AuthController"]
    Controller --> Service["AuthService.logout"]
    Service -->|parse userId, jti, exp| Jwt["JwtService"]
    Service -->|verify ownerUserId == callerUserId & DEL refresh:tokenId| Redis["Redis"]
    Service -->|SET blacklist:access:jti TTL exp-now| Redis
    Controller -->|200 OK {"success": true}| Client
```

## API Design

### Logout endpoint

- **Method and path:** `POST /api/v1/auth/logout` (requires valid `Authorization: Bearer <accessToken>`).
- **Request body (`LogoutRequest`, optional):** JSON containing `refreshToken`. Making the body optional allows clients to blocklist an access token even when the refresh token is no longer held.
- **Success response (`LogoutResponse`):** HTTP `200 OK` with JSON body `{"success": true}`.

### Error and edge-case handling

- **Missing, invalid, or already-revoked access token (`401 Unauthorized`):** Rejected by `JwtAuthenticationFilter` or `SecurityConfig` before reaching `AuthController`.
- **Missing or already-expired refresh token in Redis:** Treated as idempotent; logout still blocklists the access token's `jti` and returns `200 OK`.
- **Refresh token owned by another user:** `AuthService` leaves the other user's `refresh:<tokenId>` untouched in Redis so one user cannot terminate another user's session.

## Security Model

- **Ownership enforcement:** `AuthService` checks `ownerUserId.equals(callerUserId)` against Redis before deleting `refresh:<tokenId>`, preventing cross-user forced logout.
- **Immediate access revocation:** Writing `blacklist:access:<jti>` with `TTL = expiresAt - now` (at most 15 minutes) stops replayed access tokens immediately while ensuring Redis keys expire automatically once the token's own `exp` timestamp passes.
- **Per-token scope:** Keying the blocklist by `jti` rather than `userId` revokes only the logged-out session without disconnecting the user's other devices.
