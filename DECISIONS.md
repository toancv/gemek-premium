# DECISIONS — Apartment Management System

Log of all autonomous decisions made by agents.
Format: Date | Decision | Reasoning | Alternatives

---

## Architecture Decisions

### 2026-05-29 | Modular Monolith over Microservices
**Decision:** Single deployable JAR, feature-package organized internally.
**Reasoning:** 1000 apartments, small team. Distributed services overhead (tracing, service mesh, separate CI pipelines) unjustified. Single DB transaction across modules.
**Alternatives:** Microservices, Domain-driven separate services.

### 2026-05-29 | Spring Boot 3.3 + Java 21 backend
**Decision:** Spring Boot 3.3 with Java 21 LTS.
**Reasoning:** De-facto VTIT team standard. Java 21 virtual threads improve I/O efficiency. Mature security, data, web layers. Native Docker image support.
**Alternatives:** Node.js/Fastify, Python/FastAPI.

### 2026-05-29 | PostgreSQL 15 single instance
**Decision:** Single PostgreSQL instance, all tables in public schema.
**Reasoning:** Data volume <10 GB for years of operation. Cross-module joins trivial without network hops. JSONB support for audit log values.
**Alternatives:** MySQL 8, MariaDB, separate schemas per module.

### 2026-05-29 | UUID primary keys throughout
**Decision:** All PKs use gen_random_uuid().
**Reasoning:** Sequential integers expose row counts and allow enumeration attacks. No meaningful performance cost at this scale.
**Alternatives:** BIGSERIAL auto-increment.

### 2026-05-29 | MinIO for file storage
**Decision:** Self-hosted MinIO (S3-compatible) for maintenance photos and contract attachments.
**Reasoning:** No cloud vendor lock-in. Docker-native. Backend stores object keys, issues presigned URLs with 1-hour expiry.
**Alternatives:** AWS S3, Cloudflare R2.

### 2026-05-29 | JWT stateless auth with Redis blocklist
**Decision:** 15-min access tokens + 7-day refresh tokens stored in Redis. Logout adds JTI to Redis blocklist.
**Reasoning:** Stateless verification for most requests; Redis escape hatch for revocation. No session affinity needed.
**Alternatives:** Keycloak, Spring Session (stateful).

### 2026-05-29 | Two separate React apps in pnpm workspace
**Decision:** admin/ and resident/ as separate apps sharing packages/ui component library.
**Reasoning:** Different UX paradigms (desktop-first vs mobile-first). Clean separation.
**Alternatives:** Single SPA with role-based routing.

### 2026-05-29 | SMS gateway: pluggable no-op interface
**Decision:** SmsGateway interface with no-op default; real provider injected via @ConditionalOnProperty.
**Reasoning:** Vietnamese telco SMS APIs vary; abstracting avoids forcing a specific vendor choice.
**Alternatives:** Hardcode a specific Vietnamese SMS provider.

### 2026-05-29 | SLA deadline computed at INSERT time
**Decision:** maintenance_requests.sla_deadline stored as created_at + category.sla_hours.
**Reasoning:** SLA scheduler runs simple indexed range query without joining categories every check cycle.
**Alternatives:** Compute SLA breach dynamically on each query.

### 2026-05-29 | Notification delivery: fire-and-forget
**Decision:** FCM/SMTP/SMS failures logged at WARN, do not roll back business transaction.
**Reasoning:** External delivery failures should not block core operations.
**Alternatives:** Two-phase commit, outbox pattern (overkill at this scale).

---

## Backend Decisions
_(backend-dev agent fills this)_

## Frontend Decisions
_(frontend-dev agent fills this)_

## CTO Overrides
_(record when CTO overrides agent decision)_
