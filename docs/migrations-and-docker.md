# Database Migrations and Local Docker Architecture

- **Purpose:** Define how the Identity and Access Management (IAM) service manages PostgreSQL schema versions with Flyway and runs PostgreSQL and Redis locally with Docker Compose.
- **For:** Backend engineers running the service locally or adding schema changes.
- **Date:** 2026-11-26

## Summary

PostgreSQL schema changes are versioned with Flyway SQL migrations under `app/src/main/resources/db/migration/`, and Hibernate runs with `spring.jpa.hibernate.ddl-auto=validate` so the application fails fast on entity-to-table mismatches. Local development runs PostgreSQL 16 and Redis 7 via `docker-compose.yml` with persistent volumes and healthchecks while Spring Boot runs locally through Gradle.

## Migration Architecture

### How Flyway runs on startup

1. **Read history:** On application startup (before Hibernate validates entities), Flyway reads `flyway_schema_history` in PostgreSQL.
2. **Verify checksums:** Flyway checks that previously applied migration files have not been modified.
3. **Apply pending versions:** Flyway executes any new `V<number>__<description>.sql` files (or `BaseJavaMigration` classes when Java logic is needed) in version order inside a transaction and records them in `flyway_schema_history`.
4. **Validate JPA entities:** Hibernate runs `ddl-auto=validate` against the migrated PostgreSQL schema and verifies every `@Entity` column matches the database table.

### Initial schema (`V1__create_users_table.sql`)

The `V1` migration creates the `users` table matching explicit `@Column(name = ...)` mappings in `app/src/main/java/org/IAM/repositories/user/User.java`:

- **user_id:** `UUID PRIMARY KEY`
- **username:** `VARCHAR(255) NOT NULL`
- **email:** `VARCHAR(255) NOT NULL UNIQUE` (enforces duplicate-email protection atomically via a unique B-tree index)
- **password:** `VARCHAR(255) NOT NULL`
- **full_name:** `VARCHAR(100)`
- **phone:** `VARCHAR(20)`
- **address:** `VARCHAR(255)`
- **date_of_birth:** `DATE`
- **avatar_url:** `VARCHAR(500)`
- **created_date:** `TIMESTAMPTZ NOT NULL`

## Local Docker Compose Architecture

### Services

`docker-compose.yml` runs the two backing data stores locally so the Spring Boot application connects over `localhost`:

- **postgres (`postgres:16-alpine`):** Exposes port `5432`, stores data in named volume `iam_postgres_data`, and runs `pg_isready` healthchecks.
- **redis (`redis:7-alpine`):** Exposes port `6379`, enables append-only persistence (`--appendonly yes`) on named volume `iam_redis_data`, and runs `redis-cli ping` healthchecks.

### Running locally

1. **Copy environment template:** `cp .env.example .env` (optional; defaults work for local development).
2. **Start PostgreSQL and Redis:** `docker compose up -d`
3. **Run the Spring Boot service:** `./gradlew bootRun` (Flyway applies `V1__create_users_table.sql` automatically on first boot).
4. **Stop containers:** `docker compose down` (add `-v` only when resetting local database volumes).
