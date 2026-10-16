# Phase 2: Accounts & Ledger Core — Developer Guide

This guide explains how Phase 2 works, how the double-entry bookkeeping engine operates, how concurrency and deadlocks are prevented, and how to test the endpoints locally.

---

## 1. How Phase 2 Works (The Core Concepts)

### 1.1 Double-Entry Bookkeeping
Every transfer between accounts creates **two balanced, immutable entries**:
1. **DEBIT**: Deducts amount from the source account.
2. **CREDIT**: Adds amount to the destination account.
$$\text{Net Ledger Change} = -\text{Amount} + \text{Amount} = 0$$

All balances and amounts are stored as `NUMERIC(19,4)` in PostgreSQL and mapped to `BigDecimal` in Java, preventing IEEE 754 floating-point rounding issues.

### 1.2 Pessimistic Row Locking (`SELECT ... FOR UPDATE`)
To prevent race conditions where concurrent transfers read stale balances and cause lost updates, the transfer engine uses `@Lock(LockModeType.PESSIMISTIC_WRITE)`. PostgreSQL locks the affected account rows until the transaction commits.

### 1.3 Deadlock Prevention via Deterministic Lock Ordering
When two cross-transfers happen simultaneously (e.g., Transfer 1: Account A $\to$ Account B, Transfer 2: Account B $\to$ Account A), threads can deadlock waiting for each other's account lock.
**Solution:** Locks are always acquired in sorted lexical order of account UUIDs (`min(idA, idB)` first, `max(idA, idB)` second). Both threads request locks in the exact same sequence, making deadlocks mathematically impossible.

### 1.4 Idempotent API Processing
Clients provide an `Idempotency-Key: <UUID>` header with each transfer request.
- If a network failure occurs and the client retries, the server detects the existing key in the `transfers` table and returns the original transaction result immediately without double-spending.
- A database-level `UNIQUE` constraint guarantees zero duplicate executions even under concurrent requests with the same key.

---

## 2. API Endpoints Overview

| Method | Endpoint | Header / Auth | Description |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/v1/accounts` | `Authorization: Bearer <JWT>` | Create a new wallet / account |
| `GET` | `/api/v1/accounts/me` | `Authorization: Bearer <JWT>` | Get all accounts belonging to caller |
| `GET` | `/api/v1/accounts/{id}` | `Authorization: Bearer <JWT>` | Get account details & balance |
| `GET` | `/api/v1/accounts/{id}/entries` | `Authorization: Bearer <JWT>` | Paginated ledger audit statement |
| `POST` | `/api/v1/transfers` | `Authorization: Bearer <JWT>`<br>`Idempotency-Key: <UUID>` | Move funds between two accounts |
| `GET` | `/api/v1/transfers/{id}` | `Authorization: Bearer <JWT>` | Get transfer status & details |

---

## 3. How to Test Endpoints Locally

### Prerequisites
1. Start PostgreSQL & Redis:
   ```bash
   docker compose up -d
   ```
2. Start the application:
   ```bash
   # On Windows:
   .\mvnw.cmd spring-boot:run

   # On Linux / macOS:
   ./mvnw spring-boot:run
   ```

---

### Step 1: Create an Account
```bash
curl -X POST http://localhost:8080/api/v1/accounts \
  -H "Authorization: Bearer <ACCESS_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{"currency":"INR","initialDeposit":1000.00}'
```

**Response (HTTP 201 Created):**
```json
{
  "id": "771e8400-e29b-41d4-a716-446655440001",
  "userId": "550e8400-e29b-41d4-a716-446655440000",
  "ownerName": "Alice Doe",
  "currency": "INR",
  "balance": 1000.0000,
  "frozen": false,
  "createdAt": "2026-10-15T10:30:00Z"
}
```

---

### Step 2: List My Accounts
```bash
curl -X GET http://localhost:8080/api/v1/accounts/me \
  -H "Authorization: Bearer <ACCESS_TOKEN>"
```

---

### Step 3: Execute a Transfer (with Idempotency Key)
```bash
curl -X POST http://localhost:8080/api/v1/transfers \
  -H "Authorization: Bearer <ACCESS_TOKEN>" \
  -H "Idempotency-Key: 999e8400-e29b-41d4-a716-446655440099" \
  -H "Content-Type: application/json" \
  -d '{
    "sourceAccountId": "<SOURCE_ACCOUNT_UUID>",
    "destinationAccountId": "<DESTINATION_ACCOUNT_UUID>",
    "amount": 250.00,
    "currency": "INR"
  }'
```

**Response (HTTP 201 Created):**
```json
{
  "id": "111e8400-e29b-41d4-a716-446655440011",
  "idempotencyKey": "999e8400-e29b-41d4-a716-446655440099",
  "sourceAccountId": "771e8400-e29b-41d4-a716-446655440001",
  "destinationAccountId": "882e8400-e29b-41d4-a716-446655440002",
  "amount": 250.0000,
  "currency": "INR",
  "status": "COMPLETED",
  "failureReason": null,
  "createdAt": "2026-10-15T10:35:00Z",
  "completedAt": "2026-10-15T10:35:00Z"
}
```

---

### Step 4: Check Account Ledger Statement (Audit Log)
```bash
curl -X GET "http://localhost:8080/api/v1/accounts/<ACCOUNT_UUID>/entries?page=0&size=10" \
  -H "Authorization: Bearer <ACCESS_TOKEN>"
```

---

## 4. Running Automated Tests

Run the full test suite anytime:
```bash
# On Windows:
.\mvnw.cmd test

# On Linux / macOS:
./mvnw test
```
