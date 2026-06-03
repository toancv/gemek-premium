# Gemek Premium — Apartment Management System

Full-stack apartment management platform: Spring Boot 3.3 backend, dual React SPAs (admin + resident), PostgreSQL 15, Redis 7, MinIO object storage, nginx reverse proxy. Containerised via Docker Compose.

---

## Quick Start (local deployment)

### Prerequisites

| Tool | Version |
|------|---------|
| Docker Desktop | 4.x+ |
| JDK (for local dev only) | 21 (Eclipse Temurin recommended) |
| Node.js + pnpm (for local dev only) | Node 20, pnpm 9 |

---

### 1. Environment setup

```bash
cp .env.example .env
```

Edit `.env` and fill every `CHANGE_ME_*` value:

| Variable | Purpose |
|----------|---------|
| `DB_USER` / `DB_PASSWORD` | PostgreSQL credentials |
| `REDIS_PASSWORD` | Redis auth password |
| `JWT_SECRET` | HS512 signing key — generate: `openssl rand -hex 64` |
| `MINIO_ACCESS_KEY` / `MINIO_SECRET_KEY` | MinIO root credentials (min 8 chars each) |
| `ADMIN_PASSWORD_HASH` | BCrypt-12 hash of the admin account password |

**Generate `ADMIN_PASSWORD_HASH`** (Python):
```bash
python -c "import bcrypt; print(bcrypt.hashpw(b'YourPassword', bcrypt.gensalt(12)).decode())"
```
Or use any BCrypt-12 hash generator.

---

### 2. Pull required images (once)

```bash
docker pull postgres:15-alpine
docker pull redis:7-alpine
docker pull minio/minio:RELEASE.2024-11-07T00-52-20Z
docker pull nginx:1.25-alpine
docker pull node:20-alpine
```

---

### 3. First-time MinIO bucket setup

MinIO does not auto-create buckets. Start MinIO alone, create the bucket, then start the rest:

```bash
# Start only MinIO
docker compose up -d minio

# Open MinIO console in browser: http://localhost:9001
# Login with MINIO_ACCESS_KEY / MINIO_SECRET_KEY from .env
# Create a bucket named exactly: gemek
# Set bucket access to private (default)

# Once bucket exists, start remaining services
docker compose up -d --build
```

---

### 4. Full stack start

```bash
docker compose up -d --build
```

This builds:
- `gemek-backend` — Spring Boot JAR (multi-stage Maven + JRE 21)
- `gemek-nginx` — React admin + resident apps built by Vite, served by nginx

First startup applies Flyway migrations V1–V10 automatically.

---

### 5. Verify

```bash
# Backend health
curl http://localhost/actuator/health

# Admin portal
open http://localhost        # port 80

# Resident portal
open http://localhost:81     # port 81

# API docs
open http://localhost/swagger-ui/index.html
```

Default admin login: `admin@gemek.vn` / the password whose hash you put in `ADMIN_PASSWORD_HASH`.

---

## Service map

| Service | Internal host | Host port | Purpose |
|---------|--------------|-----------|---------|
| postgres | `postgres:5432` | (not exposed) | PostgreSQL 15 |
| redis | `redis:6379` | (not exposed) | Token cache + rate limit |
| minio | `minio:9000` | 9001 (console only) | Object storage |
| backend | `backend:8080` | (via nginx) | Spring Boot API |
| nginx | — | 80 (admin), 81 (resident) | Reverse proxy + SPA |

---

## Local development (without Docker)

### Backend

```bash
cd backend
# Requires JDK 21 and a running PostgreSQL + Redis (use docker-compose.dev.yml for infra only)
docker compose -f docker-compose.dev.yml up -d   # starts postgres on 5434

export DB_URL=jdbc:postgresql://localhost:5434/gemek
export DB_USER=gemek
export DB_PASSWORD=LocalDev@2026
export REDIS_HOST=localhost
export REDIS_PORT=6379
export REDIS_PASSWORD=<your-redis-password>
export JWT_SECRET=<hex-64-bytes>
export MINIO_ENDPOINT=http://localhost:9000
export MINIO_ACCESS_KEY=minioadmin
export MINIO_SECRET_KEY=minioadmin123
export MINIO_BUCKET=gemek
export ADMIN_PASSWORD_HASH='<bcrypt-12-hash>'

./mvnw spring-boot:run
```

### Frontend

```bash
cd frontend
pnpm install

# Admin portal (proxies /api to localhost:8080)
pnpm dev:admin      # http://localhost:3000

# Resident portal
pnpm dev:resident   # http://localhost:3001
```

---

## Run tests

```bash
cd backend
JAVA_HOME=/path/to/jdk-21 ./mvnw clean verify
```

Requires Docker for Testcontainers (PostgreSQL + Redis integration tests).

**Expected:** 149 tests, 0 failures.

---

## Common operations

```bash
# View backend logs
docker compose logs -f backend

# Stop all services (keep data)
docker compose stop

# Reset database (re-runs Flyway migrations)
docker compose down
docker volume rm gemek-premium_postgres_data
docker compose up -d --build

# Rebuild after code change
docker compose up -d --build backend nginx
```

---

## Architecture

```
Browser (Admin)          Browser (Resident)
     │ port 80                │ port 81
     ▼                        ▼
  nginx ──────── /api/* ──► backend (Spring Boot 3.3, Java 21)
                                │
                    ┌───────────┼───────────┐
                    ▼           ▼           ▼
                PostgreSQL    Redis      MinIO
                (schema +    (JWT       (ticket
                 data)       cache)      photos)
```

See `docs/ARCHITECTURE.md` for full design, `docs/API-SPEC.md` for API contracts, `docs/DB-SCHEMA.sql` for database schema.

---

## Security notes

- All secrets via `.env` — never committed
- Admin password hash stored in Flyway placeholder (`ADMIN_PASSWORD_HASH`), not in source
- JWT: 15-min access tokens (in-memory), 7-day refresh tokens (Redis + client localStorage)
- Rate limiting: 10 login attempts / 5 refresh attempts per minute per IP
- MinIO: presigned URLs with 1-hour TTL, per-user ownership enforced
- CORS: explicit allowlist, no wildcard origins
- SEC-20 (refresh token HttpOnly cookie migration): tracked post-G4 hardening item
