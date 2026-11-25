# DTO and Request Validation Conventions

- **Purpose:** Explain how the Identity and Access Management (IAM) service structures Data Transfer Objects (DTOs) with Java records and validates incoming HTTP requests through Jakarta Validation.
- **For:** Backend engineers adding or reviewing API endpoints and DTOs.
- **Date:** 2026-11-26

## Summary

Incoming and outgoing HTTP JSON payloads use immutable Java `record` DTOs under `app/src/main/java/org/IAM/dto/` rather than exposing Java Persistence API (JPA) entities directly. Validation constraints live as Jakarta Validation annotations on request `record` components and run automatically via `@Valid` before controller logic executes.

## Why DTOs use Java records instead of JPA entities

- **DTOs (`record`):** A Java `record` generates `private final` fields, a canonical constructor, component accessors, `equals`, `hashCode`, and `toString` from its header parameters. Leaving the `{}` body empty is standard when no custom methods are needed. Jackson serializes and deserializes `record` components automatically.
- **JPA entities (`class`):** `app/src/main/java/org/IAM/repositories/user/User.java` remains a regular mutable `class` because Hibernate requires a non-`final` class with a no-argument constructor and mutable fields for column hydration and lazy proxies.
- **Boundary isolation:** Keeping request and response DTOs separate from `User` prevents mass-assignment onto database columns, keeps password hashes out of responses, and scopes validation rules to each endpoint.

## How Spring runs request validation

Every validated endpoint follows a four-step pipeline (for example, `AuthController.register` with `RegisterRequest`):

1. **Deserialize JSON:** Jackson parses the HTTP body and constructs the `record` instance (such as `new RegisterRequest(username, email, password)`).
2. **Trigger validator (`@Valid`):** Placing `@Valid` on `@Valid @RequestBody` instructs Spring MVC to invoke Hibernate Validator (provided by `spring-boot-starter-validation`) before entering the controller method body.
3. **Evaluate annotations:** Hibernate Validator inspects Jakarta Validation annotations (`@NotBlank`, `@Email`, `@Size`) declared on each `record` component via reflection.
4. **Return error response:** On any violation, Spring throws `MethodArgumentNotValidException` and skips the service layer. `GlobalExceptionHandler` catches the exception and returns HTTP `400 Bad Request` with `{"error": "<first field message>"}`.

## When a record body is empty vs non-empty

- **Request DTOs with single-field rules (`RegisterRequest`, `LoginRequest`, `RefreshRequest`):** Constraints belong on the header parameters; the `{}` body stays empty.
- **Optional-input DTOs (`LogoutRequest`):** Omit `@NotBlank` when a field is intentionally optional.
- **Response DTOs (`RegisterResponse`, `LogoutResponse`, `AuthTokenResponse`):** Carry server-generated output only, so no validation annotations are applied.
- **Multi-field validation rules:** Add logic inside `{}` (a compact constructor or custom validation method) only when a rule depends on multiple fields together, such as ensuring a new password differs from the old password.
