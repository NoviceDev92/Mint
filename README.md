# Mint

[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://adoptium.net/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.4-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-blue.svg)](https://www.postgresql.org/)
[![Flyway](https://img.shields.io/badge/Flyway-Migrations-red.svg)](https://flywaydb.org/)
[![Build](https://img.shields.io/badge/Build-Passing-success.svg)]()

> A high-integrity, double-entry financial ledger and payment movement engine designed for accuracy, concurrency safety, and idempotent operations.

---

## Overview

**Mint** is a production-grade ledger backend engineered around the core principles used by modern payments infrastructure (such as Stripe and Modern Treasury). It provides an immutable, append-only record of financial transactions where money cannot be created or destroyed out of thin air.

### Core Engineering Principles

* **Double-Entry Bookkeeping:** Every monetary transfer generates exactly two paired, immutable ledger entries: one `DEBIT` and one `CREDIT`. Across the entire ledger, the net sum of all entries is always zero ($\sum \Delta = 0$).
* **Exact Financial Precision:** Uses PostgreSQL `NUMERIC(19,4)` and Java `BigDecimal` throughout the domain to prevent floating-point rounding errors inherent to standard IEEE 754 representations.
* **Deterministic Concurrency & Deadlock Prevention:** Prevents balance corruption from concurrent transfers using PostgreSQL pessimistic row-level write locks (`SELECT ... FOR UPDATE`). Deadlocks from simultaneous cross-transfers ($A \to B$ and $B \to A$) are mathematically prevented by enforcing strict lexical sorting on account UUIDs prior to lock acquisition.
* **Guaranteed Idempotency:** Endpoints accept an `Idempotency-Key` HTTP header backed by a database-level unique constraint. Retried requests due to dropped connections or timeouts return the cached transaction response without double-debiting.
* **Stateless Security:** Built with Spring Security and JJWT. Uses short-lived (15-minute) cryptographic access tokens paired with single-use, database-backed refresh token rotation.

---

## Architecture

```
┌─────────────────┐
│     Client      │
│ (Postman, curl) │
└────────┬────────┘
         │ HTTP / JSON (Bearer JWT + Idempotency-Key)
         ▼
┌────────────────────────────────────────────────────────┐
│ Spring Boot API Gateway / Filter Chain                 │
│ ├── JwtAuthenticationFilter (HMAC-SHA256 validation)  │
│ └── GlobalExceptionHandler (RFC 7807 unified errors)   │
└────────┬───────────────────────────────────────────────┘
         │
         ▼
┌────────────────────────────────────────────────────────┐
│ Service Layer                                          │
│ ├── TransferService (Deadlock-Free Locking & Entries)  │
│ ├── AccountService (Wallet Management & Balances)      │
│ └── AuthService (BCrypt Hashing & Token Rotation)      │
└────────┬───────────────────────────────────────────────┘
         │
         ▼
┌────────────────────────────────────────────────────────┐
│ PostgreSQL 16 (Managed via Flyway Migrations)          │
│ ├── users (Credentials & Profiles)                     │
│ ├── refresh_tokens (Revocation & Single-Use Rotation)  │
│ ├── accounts (Balances with Pessimistic Row Locks)     │
│ ├── transfers (Idempotency Key Tracking & Statuses)    │
│ └── ledger_entries (Immutable Append-Only Audit Trail) │
└────────────────────────────────────────────────────────┘
```

---

## Database Schema & Migrations

Database evolution is version-controlled through Flyway migrations in `src/main/resources/db/migration/`:

* **`V1__create_users_table.sql`**: Identity management (`users`) and refresh token storage with cascading revocation.
* **`V2__create_accounts_table.sql`**: Wallets/ledger accounts with `NUMERIC(19,4)` balances and optimistic locking versions.
* **`V3__create_transfers_and_entries.sql`**: Transfer tracking with unique `idempotency_key` constraints and immutable double-entry `ledger_entries`.

---

## API Specification

### Authentication & Identity

| Method | Endpoint | Description | Auth Required |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/v1/auth/register` | Register new user account with BCrypt password hashing | No |
| `POST` | `/api/v1/auth/login` | Authenticate credentials and receive Access + Refresh tokens | No |
| `POST` | `/api/v1/auth/refresh` | Rotate expired Access Token using valid Refresh Token | No |
| `GET` | `/api/v1/auth/me` | Fetch currently authenticated user profile | Bearer JWT |

### Accounts & Wallets

| Method | Endpoint | Description | Auth Required |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/v1/accounts` | Open a new wallet/account with specified currency | Bearer JWT |
| `GET` | `/api/v1/accounts/me` | List all accounts owned by the authenticated caller | Bearer JWT |
| `GET` | `/api/v1/accounts/{id}` | Retrieve account details and current balance | Bearer JWT |
| `GET` | `/api/v1/accounts/{id}/entries` | Get paginated statement lines for an account | Bearer JWT |

### Transfers (Money Movement)

| Method | Endpoint | Header Required | Description |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/v1/transfers` | `Idempotency-Key: <UUID>` | Execute double-entry transfer between accounts |
| `GET` | `/api/v1/transfers/{id}` | None | Fetch transfer execution status and details |

---

## Getting Started

### Prerequisites

* **Java 21** or later
* **Docker & Docker Compose** (for PostgreSQL and Redis)

### 1. Start Infrastructure

Launch PostgreSQL 16 and Redis containers via Docker Compose:

```bash
docker compose up -d
```

### 2. Configure Environment

Copy the example environment configuration:

```bash
cp .env.example .env
```

Default credentials in `docker-compose.yml` match `application.yml` for local development out of the box.

### 3. Run the Application

Using the provided Maven Wrapper:

```bash
# On Windows (PowerShell / CMD):
.\mvnw.cmd spring-boot:run

# On macOS / Linux:
./mvnw spring-boot:run
```

The server starts on `http://localhost:8080`. Flyway will automatically execute pending database migrations upon startup.

---

## End-to-End Workflow Example

### 1. Register a User
```bash
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "email": "alex@example.com",
    "password": "SecurePassword123!",
    "fullName": "Alex Mercer"
  }'
```

*Response:*
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "refreshToken": "4a28f731-0177-4b71-bdfc-29b350410001",
  "tokenType": "Bearer",
  "expiresIn": 900
}
```

### 2. Create an Account
```bash
curl -X POST http://localhost:8080/api/v1/accounts \
  -H "Authorization: Bearer <ACCESS_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{
    "currency": "USD",
    "initialDeposit": 1000.00
  }'
```

### 3. Execute a Transfer
```bash
curl -X POST http://localhost:8080/api/v1/transfers \
  -H "Authorization: Bearer <ACCESS_TOKEN>" \
  -H "Idempotency-Key: 9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d" \
  -H "Content-Type: application/json" \
  -d '{
    "sourceAccountId": "771e8400-e29b-41d4-a716-446655440001",
    "destinationAccountId": "882e8400-e29b-41d4-a716-446655440002",
    "amount": 250.00,
    "currency": "USD"
  }'
```

*Response:*
```json
{
  "id": "111e8400-e29b-41d4-a716-446655440011",
  "idempotencyKey": "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d",
  "sourceAccountId": "771e8400-e29b-41d4-a716-446655440001",
  "destinationAccountId": "882e8400-e29b-41d4-a716-446655440002",
  "amount": 250.0000,
  "currency": "USD",
  "status": "COMPLETED",
  "createdAt": "2026-10-18T09:30:00Z",
  "completedAt": "2026-10-18T09:30:00Z"
}
```

### 4. Query Audit Statement
```bash
curl -X GET "http://localhost:8080/api/v1/accounts/771e8400-e29b-41d4-a716-446655440001/entries?page=0&size=10" \
  -H "Authorization: Bearer <ACCESS_TOKEN>"
```

---

## Running the Automated Test Suite

Execute unit and integration tests across the domain, controllers, security filters, and ledger engine:

```bash
# On Windows:
.\mvnw.cmd test

# On macOS / Linux:
./mvnw test
```

---

## License

This project is licensed under the [MIT License](LICENSE).
