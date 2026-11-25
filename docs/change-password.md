# Change Password Architecture

- **Purpose:** Define the implementation architecture for authenticated password changes in the Identity and Access Management (IAM) service.
- **For:** Backend engineers implementing and reviewing the change-password feature.
- **Date:** 2026-11-26

## Summary

The change-password feature exposes an authenticated `PUT /api/v1/users/me/password` endpoint over HTTPS. It identifies the caller from the signed JSON Web Token (JWT), verifies `currentPassword` against the stored `{bcrypt}` hash, cancels the update if `newPassword` matches `currentPassword`, hashes and stores `newPassword` in PostgreSQL, and blocklists the current access JWT's `jti` in Redis.

## System Architecture

### What each component is responsible for

- **Security filter (`JwtAuthenticationFilter`):** Validates `Authorization: Bearer <accessToken>` and rejects missing, expired, or blocklisted tokens with HTTP `401 Unauthorized`.
- **Controller layer (`UserController`):** Exposes `PUT /api/v1/users/me/password`, validates `ChangePasswordRequest`, extracts the Bearer token, and delegates to `UserService.changePassword`.
- **Service layer (`UserService`):** Verifies `currentPassword` via `DelegatingPasswordEncoder.matches`, rejects reuse of the current password, hashes `newPassword`, persists the updated `User`, and blocklists the current access JWT's `jti` via `TokenStoreService`.
- **Repository layer (`UserRepository`):** Loads and updates the `User` row in PostgreSQL by primary key `userID`.

### How a change-password request flows

The diagram below shows an authenticated client sending `currentPassword` and `newPassword` to `PUT /api/v1/users/me/password`. `UserService` verifies `currentPassword`, rejects the request if `newPassword` matches `currentPassword`, saves the newly hashed password to PostgreSQL, and blocklists the current access token `jti` in Redis.

```mermaid
flowchart LR
    Client["Client"] -->|PUT /api/v1/users/me/password + Bearer JWT| Filter["JwtAuthenticationFilter"]
    Filter --> Controller["UserController"]
    Controller --> Service["UserService.changePassword"]
    Service -->|findById| DB["PostgreSQL (users)"]
    Service -->|matches currentPassword| Encoder["DelegatingPasswordEncoder (BCrypt)"]
    Service -->|encode newPassword & UPDATE| DB
    Service -->|SET blacklist:access:jti TTL exp-now| Redis["Redis"]
    Controller -->|200 OK {"success": true}| Client
```

## API Design

### Change Password endpoint

- **Method and path:** `PUT /api/v1/users/me/password` (requires valid `Authorization: Bearer <accessToken>`).
- **Request DTO (`ChangePasswordRequest`):** JSON containing `currentPassword` (`@NotBlank`) and `newPassword` (`@NotBlank`, `@Size(min = 8)`).
- **Success response (`ChangePasswordResponse`):** HTTP `200 OK` with JSON body `{"success": true}`.

### Error handling

- **Wrong current password (`401 Unauthorized`):** Returns `{"error": "Current password is incorrect"}`.
- **Same password reuse (`400 Bad Request`):** Cancels the change without writing to PostgreSQL and returns `{"error": "New password must differ from current password"}`.
- **Validation failure (`400 Bad Request`):** Handled by `GlobalExceptionHandler` when `currentPassword` or `newPassword` fails Jakarta Validation.

## Security Model

- **Current password verification:** Prevents an attacker with a temporarily unattended browser session from changing the password without knowing the current password.
- **No password reuse:** Rejects `newPassword` when it matches the stored hash (checked both directly and via `passwordEncoder.matches`) before persisting.
- **Session revocation:** Blocklists the current access JWT's `jti` in Redis for its remaining TTL so the client must authenticate again with the new password.
