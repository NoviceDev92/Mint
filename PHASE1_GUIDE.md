# Phase 1: Skeleton & Auth — Developer Guide

This guide explains how Phase 1 works, how each piece connects, and how to test the endpoints.

---

## 1. How the Architecture Works (Intuitive & Simple)

1. **User Registers (`POST /api/v1/auth/register`)**:
   - The user submits `email`, `password`, and `fullName`.
   - The server hashes the password with **BCrypt** (a standard one-way mathematical function so passwords are never stored in plain text).
   - The user record is saved to the PostgreSQL `users` table.
   - An **Access Token (JWT)** and a **Refresh Token** are returned immediately.

2. **User Logs In (`POST /api/v1/auth/login`)**:
   - The user sends `email` and `password`.
   - The server verifies `passwordEncoder.matches(rawPassword, storedHash)`.
   - If valid, a new JWT access token and refresh token are issued.

3. **User Makes Protected Requests (`GET /api/v1/auth/me`)**:
   - The client sends the header: `Authorization: Bearer <accessToken>`.
   - `JwtAuthenticationFilter` intercepts the request:
     - Verifies the signature using our secret key.
     - Checks that the token hasn't expired.
     - Extracts the user email and role.
     - Injects the authenticated user into Spring Security's context.
   - The controller can safely read `@AuthenticationPrincipal String email`.

4. **Token Refresh (`POST /api/v1/auth/refresh`)**:
   - When the 15-minute access token expires, the client sends `{ "refreshToken": "..." }`.
   - The server verifies the refresh token in the `refresh_tokens` database table, deletes the old one (preventing replay attacks), and issues a fresh pair.

---

## 2. How to Run Locally

### Prerequisites
- Java 21 (installed at `C:\Program Files\Eclipse Adoptium\jdk-21.0.8.9-hotspot`)
- Docker Desktop (for Postgres & Redis)

### Step 1: Start PostgreSQL and Redis
Open PowerShell and start the database containers:
```powershell
docker compose up -d
```

### Step 2: Set JAVA_HOME and Run the App
```powershell
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21.0.8.9-hotspot"
$env:Path = "$env:JAVA_HOME\bin;" + $env:Path

.\mvnw.cmd spring-boot:run
```

---

## 3. How to Test Endpoints

### 1. Register a new user
```bash
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"alice@mint.internal","password":"securePassword123","fullName":"Alice Doe"}'
```

**Response (HTTP 201 Created):**
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "refreshToken": "d8e3fc7e-8c65-4f3b-...",
  "tokenType": "Bearer",
  "expiresIn": 900,
  "user": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "email": "alice@mint.internal",
    "fullName": "Alice Doe",
    "role": "ROLE_USER",
    "createdAt": "2026-10-09T17:30:00Z"
  }
}
```

### 2. Login
```bash
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"alice@mint.internal","password":"securePassword123"}'
```

### 3. Access Protected Profile (`/me`)
```bash
curl -X GET http://localhost:8080/api/v1/auth/me \
  -H "Authorization: Bearer <PASTE_ACCESS_TOKEN_HERE>"
```

### 4. Refresh Token
```bash
curl -X POST http://localhost:8080/api/v1/auth/refresh \
  -H "Content-Type: application/json" \
  -d '{"refreshToken":"<PASTE_REFRESH_TOKEN_HERE>"}'
```

---

## 4. Running the Tests
To run the automated test suite anytime:
```powershell
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21.0.8.9-hotspot"
$env:Path = "$env:JAVA_HOME\bin;" + $env:Path

.\mvnw.cmd test
```
