# User Registration Architecture

- **Purpose:** Define the implementation architecture for registering new user accounts in the Identity and Access Management (IAM) service.
- **For:** Backend engineers implementing and reviewing the registration feature.
- **Date:** 2026-11-25

## Summary

The registration feature exposes `POST /api/v1/auth/register` over HTTPS, validates the `RegisterRequest` Data Transfer Object (DTO), normalizes and checks the email address, hashes the password with BCrypt through `DelegatingPasswordEncoder`, and persists a new `User` row in PostgreSQL with a randomly generated Universally Unique Identifier (UUID).

Successful requests return HTTP `201 Created` with `{"success": true}`. Duplicate emails return HTTP `409 Conflict`, enforced atomically by a PostgreSQL `UNIQUE` index.

## System Architecture

### What each component is responsible for

The codebase follows a three-layer layout matching `note.md`:

- **Controller layer (`AuthController`):** Receives `POST /api/v1/auth/register`, triggers Jakarta Validation on `RegisterRequest`, and returns HTTP `201 Created` with `RegisterResponse`.
- **Service layer (`AuthService`):** Normalizes the email, runs a fast-path existence check, hashes the raw password with `DelegatingPasswordEncoder`, and persists a new `User`.
- **Repository layer (`UserRepository`):** Maps the `User` entity to the PostgreSQL `users` table through Java Persistence API (JPA) and Spring Data.
- **Security and error handling (`SecurityConfig`, `GlobalExceptionHandler`):** Configures stateless HTTP security, provides the `DelegatingPasswordEncoder` bean, and maps validation and duplicate-email exceptions to JSON error responses.

### How a registration request flows through the system

The diagram below shows a client sending an HTTPS `POST /api/v1/auth/register` request through `SecurityFilterChain` into `AuthController` and `AuthService`. `AuthService` checks for an existing email in PostgreSQL, hashes the password with `DelegatingPasswordEncoder` using BCrypt, and saves the new `User` with a randomly generated UUID through `UserRepository`, returning HTTP `201 Created` with `{"success": true}`.

```mermaid
flowchart LR
    Client["Client"] -->|HTTPS POST /api/v1/auth/register| Security["SecurityFilterChain"]
    Security --> Controller["AuthController"]
    Controller --> Service["AuthService"]
    Service -->|existsByEmail| DB["PostgreSQL (users)"]
    Service -->|raw password| Encoder["DelegatingPasswordEncoder (BCrypt)"]
    Encoder -->|hashedPassword| Repo["UserRepository (JPA)"]
    UUID["UUID Generator"] -->|userID| Repo
    Repo -->|INSERT (UNIQUE email)| DB
    Controller -->|201 Created success: true| Client
```

## Data Model

### User entity fields

`app/src/main/java/org/IAM/repositories/user/User.java` maps to the `users` table with these columns:

- **userID:** Randomly generated `UUID` (`GenerationType.UUID`). Serves as the internal database primary key for row uniqueness.
- **username:** Non-null `String`. Captured during registration and trimmed before persistence.
- **email:** Non-null `String` with `@Column(unique = true)`. Normalized to trimmed lowercase. PostgreSQL enforces uniqueness atomically across concurrent threads and service instances through a unique B-tree index.
- **password:** Non-null `String` marked `@JsonProperty(access = WRITE_ONLY)`. Stores the `{bcrypt}`-prefixed hash produced by `DelegatingPasswordEncoder`, never the raw password.
- **createdDate:** Non-null `Instant`. Set automatically on insert by `@CreationTimestamp`.

### How data enters and persists

1. **Receive and validate input:** `AuthController` deserializes `RegisterRequest` (`username`, `email`, `password`) and runs Jakarta Validation (`@NotBlank`, `@Email`, `@Size(min = 8)`).
2. **Normalize and pre-check email:** `AuthService` trims and lowercases `email`, then calls `UserRepository.existsByEmail` to reject obvious duplicates before spending CPU on BCrypt.
3. **Hash password:** `DelegatingPasswordEncoder` encodes the raw password with BCrypt and attaches the `{bcrypt}` prefix.
4. **Assign identifier and persist:** `UserRepository.save` inserts the new `User` with a generated `UUID`. If two concurrent requests pass step 2 together, PostgreSQL's `UNIQUE` index rejects the second insert with a unique-violation error.

## API Design

### Registration endpoint

- **Method:** `POST`
- **Path:** `/api/v1/auth/register`
- **Request DTO (`RegisterRequest`):** JSON containing `username` (non-blank), `email` (valid email format), and `password` (minimum 8 characters). Using a dedicated DTO scopes validation to registration and prevents mass-assignment onto `User`.
- **Success response (`RegisterResponse`):** HTTP `201 Created` with JSON body `{"success": true}`. Returning a minimal response avoids exposing internal user fields during registration.

### Error handling

`GlobalExceptionHandler` maps failures to JSON responses shaped as `{"error": "<message>"}`:

- **Invalid input (`400 Bad Request`):** Handles `MethodArgumentNotValidException` and returns the first field validation message.
- **Duplicate email (`409 Conflict`):** Handles both `DuplicateEmailException` (fast-path check) and `DataIntegrityViolationException` (concurrent PostgreSQL unique-constraint violation) with `{"error": "Email is already registered"}`.

## Security Model

### Password storage

- **Algorithm:** BCrypt through Spring Security's `DelegatingPasswordEncoder`.
- **Reason:** `DelegatingPasswordEncoder` prefixes stored hashes with `{bcrypt}`, allowing future migration to another algorithm (such as Argon2) without invalidating existing hashes.

### Transport and access control

- **Transport:** Registration requests travel over HTTPS to protect raw passwords in transit.
- **Access control:** `SecurityFilterChain` in `app/src/main/java/org/IAM/config/SecurityConfig.java` permits unauthenticated `POST /api/v1/auth/register` requests and requires authentication on all other endpoints.
- **Stateless sessions and Cross-Site Request Forgery (CSRF):** `SecurityFilterChain` uses `SessionCreationPolicy.STATELESS` and disables default session CSRF because clients authenticate with `Authorization: Bearer` headers rather than session cookies. If tokens are stored in `HttpOnly` cookies later, CSRF protection must be re-enabled.
