# DECISIONS — Apartment Management System

Log of all autonomous decisions made by agents.
Format: Date | Decision | Reasoning | Alternatives

---

## Architecture Decisions

### 2026-05-29 | Modular Monolith over Microservices
**Decision:** Single deployable JAR, feature-package organized internally.
**Reasoning:** 1000 apartments, small team. Distributed services overhead unjustified. Cross-module operations (assign ticket → update contractor rating) benefit from single-transaction semantics.
**Alternatives:** Microservices, domain-driven separate services.

### 2026-05-29 | Spring Boot 3.3 + Java 21 backend
**Decision:** Spring Boot 3.3 with Java 21 LTS.
**Reasoning:** VTIT team standard. Java 21 virtual threads improve I/O efficiency for notification-heavy workloads. Mature security, data, web layers.
**Alternatives:** Node.js/Fastify, Python/FastAPI.

### 2026-05-29 | PostgreSQL 15 single instance
**Decision:** Single PostgreSQL instance, all tables in public schema.
**Reasoning:** Data volume <10 GB for years of operation. Cross-module joins trivial. JSONB for audit log. Native ENUM types for ticket category/status.
**Alternatives:** MySQL 8, MariaDB.

### 2026-05-29 | UUID primary keys throughout
**Decision:** All PKs use gen_random_uuid().
**Reasoning:** Sequential integers allow enumeration attacks. No meaningful performance cost at this scale.
**Alternatives:** BIGSERIAL auto-increment.

### 2026-05-29 | Ticket module replaces maintenance module
**Decision:** Single `tickets` table with `category` ENUM (MAINTENANCE_REPAIR, COMPLAINT, ADMINISTRATIVE, SUGGESTION_FEEDBACK, OTHER) replaces the narrower `maintenance_requests`.
**Reasoning:** Business requirement updated: residents can submit any type of request. Unified table with category-based routing is simpler than separate tables per type.
**Alternatives:** Separate tables per request type, polymorphic inheritance.

### 2026-05-29 | Contractor assignment restricted to MAINTENANCE_REPAIR at DB level
**Decision:** CHECK constraint: `assigned_to_contractor_id IS NULL OR category = 'MAINTENANCE_REPAIR'`. Service layer also validates before DB touch.
**Reasoning:** Business rule: only physical repair work goes to contractors. DB-level enforcement prevents bypass even through direct DB access.
**Alternatives:** Service-layer-only validation (weaker).

### 2026-05-29 | SLA per category, computed at INSERT
**Decision:** `tickets.sla_deadline = created_at + category.sla_hours`. SUGGESTION_FEEDBACK has no SLA (NULL).
**Reasoning:** Scheduler runs simple indexed range query. Partial index on `(sla_deadline) WHERE status NOT IN ('DONE','CANCELLED')` keeps hourly check fast.
**Alternatives:** Dynamic SLA computation on query.

### 2026-05-29 | MinIO for file storage (object keys in DB)
**Decision:** Self-hosted MinIO. DB stores object keys, not full URLs. Backend issues presigned GET URLs with 1-hour expiry.
**Reasoning:** No cloud vendor lock-in. Docker-native. URL never expires in DB; bucket changes only require config update.
**Alternatives:** AWS S3, Cloudflare R2.

### 2026-05-29 | JWT stateless auth with Redis blocklist
**Decision:** 15-min access tokens + 7-day refresh tokens in Redis. Logout adds JTI to Redis blocklist with TTL.
**Reasoning:** Stateless verification; Redis escape hatch for revocation.
**Alternatives:** Keycloak, Spring Session.

### 2026-05-29 | Two separate React apps in pnpm workspace
**Decision:** apps/admin (desktop-first) + apps/resident (mobile-first) sharing packages/ui.
**Reasoning:** Different UX paradigms. Clean separation. Shared components avoid duplication.
**Alternatives:** Single SPA with role-based routing.

### 2026-05-29 | SMS gateway: pluggable no-op interface
**Decision:** SmsGateway interface; real provider injected via @ConditionalOnProperty.
**Reasoning:** Vietnamese telco SMS APIs vary; abstracting avoids forcing a vendor choice.
**Alternatives:** Hardcode a specific provider.

### 2026-05-29 | Notification delivery: fire-and-forget
**Decision:** FCM/SMTP/SMS failures logged at WARN, do not roll back business transaction. In-app record always created.
**Reasoning:** External delivery failures must not block core operations.
**Alternatives:** Outbox pattern (overkill at this scale).

---

## Backend Decisions
_(backend-dev agent fills this)_

## Frontend Decisions
_(frontend-dev agent fills this)_

## CTO Overrides
_(record when CTO overrides agent decision)_
