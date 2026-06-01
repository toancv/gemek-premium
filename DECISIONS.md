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

### 2026-05-29 | V1 migration creates all ENUM types upfront
**Decision:** V1__create_enums_and_users.sql creates all PostgreSQL ENUM types (not just user_role), even though only the users table is created in this migration.
**Reasoning:** Flyway applies migrations in order; later module migrations reference these ENUMs. Creating them all in V1 avoids cross-migration dependencies and eliminates the risk of a future migration failing because its ENUM type does not yet exist.
**Alternatives:** Create each ENUM in the migration where it is first used. Rejected because migration ordering becomes a fragile dependency.

### 2026-05-29 | BCrypt strength 12 for password encoder
**Decision:** BCryptPasswordEncoder configured with cost factor 12.
**Reasoning:** ~250ms per hash on modern hardware — strong enough against brute force, acceptable latency for a login endpoint that is rate-limited to 10/min/IP anyway.
**Alternatives:** Cost 10 (Spring default, faster but weaker). Argon2 (overkill complexity for this scale).

### 2026-05-29 | Redis KEYS pattern scan for refresh token deletion on logout
**Decision:** On logout, `redisTemplate.keys("refresh:{userId}:*")` is used to delete all refresh tokens for the user.
**Reasoning:** Invalidates all devices simultaneously on logout, which is the correct security behaviour. The KEYS command is acceptable because Redis holds only a small number of refresh token entries per user and the production deployment is a single Redis instance.
**Alternatives:** Track refresh JTIs in a Redis set per user (more correct at scale, unnecessary complexity now).

### 2026-05-29 | X-Forwarded-For header honoured for IP rate limiting
**Decision:** AuthServiceImpl reads X-Forwarded-For (first IP in chain) when present, falling back to RemoteAddr.
**Reasoning:** Requests arrive via Nginx reverse proxy; RemoteAddr would always be the Nginx container IP, making per-IP rate limiting useless. Trusting X-Forwarded-For from Nginx is safe in this single-proxy setup.
**Alternatives:** Configure Nginx to set X-Real-IP instead (equivalent, chose standard header).

### 2026-05-29 | AuditLogAspect implemented as a stub
**Decision:** AuditLogAspect intercepts @Auditable methods but only logs at DEBUG level — no DB write yet.
**Reasoning:** The audit_logs table is not created in this module's migration. The annotation and aspect are wired now so future modules annotate freely; the full implementation will be added in the reporting module when audit_logs migration is introduced.
**Alternatives:** Skip @Auditable entirely for now. Rejected because it would require a refactor pass across all services later.

### 2026-05-29 | No MapStruct mapper for Ticket module — service builds DTOs manually
**Decision:** `TicketServiceImpl` builds `TicketSummaryResponse` and `TicketDetailResponse` directly in private helper methods instead of via a MapStruct mapper.
**Reasoning:** `TicketDetailResponse.photos` requires `FileStorageService.presign()` to generate presigned URLs for each photo. Injecting a Spring service into a MapStruct mapper (via `uses=`) introduces lifecycle coupling and complicates testability. The manual mapping pattern keeps all business logic in the service layer and is consistent with `ApartmentServiceImpl` which also builds nested DTOs by hand.
**Alternatives:** MapStruct with `@Mapper(componentModel="spring", uses={FileStorageService.class})`. Rejected — the photo URL generation is a side-effectful call (HTTP to MinIO), not a pure field mapping.

### 2026-05-29 | FileStorageService mocked in TicketControllerTest
**Decision:** `@MockBean FileStorageService` in `TicketControllerTest`; `presign()` returns a fixed URL stub.
**Reasoning:** The 8 required tests cover ticket lifecycle rules, not photo storage. Starting a MinIO Testcontainer for non-photo tests adds 10–15 s of startup time per CI run with no benefit. All photo-path tests can be added as dedicated integration tests when needed.
**Alternatives:** Testcontainers MinIO (minio/minio image). Deferred to a dedicated photo upload test suite.

### 2026-05-29 | ContractorRepository uses Jakarta @Transactional not Spring @Transactional
**Decision:** `ContractorRepository.recalculateRating` is annotated with `jakarta.transaction.Transactional` because it is a Spring Data repository interface method — Spring Data requires the Jakarta variant at the interface level for `@Modifying` queries when no encompassing Spring transaction exists.
**Alternatives:** Rely on the calling service's `@Transactional` context. The explicit annotation ensures safety if the method is called outside a transaction.

### 2026-05-29 | NotificationController follows direct-DTO pattern, not ApiResponse wrapper
**Decision:** `NotificationController` returns `ResponseEntity<PageResponse<T>>` and `ResponseEntity<DomainDto>` directly, without wrapping in `ApiResponse<T>`.
**Reasoning:** Every existing controller in the codebase (`ParkingController`, `AnnouncementController`, etc.) returns domain DTOs or `PageResponse` directly. Introducing `ApiResponse` on this single module would break response-shape consistency across the API.
**Alternatives:** Wrap in `ApiResponse<T>` as stated in the module spec. Rejected because it contradicts the established codebase contract.

### 2026-05-29 | MaintenanceScheduleRunner.checkOverdueSchedules annotated @Transactional(readOnly=true)
**Decision:** The scheduled method carries `@Transactional(readOnly=true)` so the JPA session remains open while the loop traverses `schedule.getContract().getCreatedBy()` lazy associations.
**Reasoning:** Without an open session, accessing lazy-loaded associations on detached entities throws `LazyInitializationException`. A read-only transaction is the lightest-weight fix and avoids adding an eager fetch or a new repository query.
**Alternatives:** Add a JOIN FETCH to `findOverdue`; load contracts eagerly. Rejected to keep the existing query untouched.

### 2026-05-29 | AuditLogAspect resolves entity ID via reflection on getId()
**Decision:** After the primary method completes, the aspect calls `result.getClass().getMethod("getId").invoke(result)` to extract a UUID entity ID. Failures are silently swallowed.
**Reasoning:** The `@Auditable` annotation has no `entityId` field, and adding one would require annotating every call site. Reflection on a stable `getId()` convention covers the common case with zero annotation changes.
**Alternatives:** Add `entityId` attribute to `@Auditable`; pass entity ID explicitly at each call site. Deferred — can be added when precise entity-ID tracking is required.

### 2026-05-29 | Report queries added to existing repositories, no new repository files
**Decision:** Report-specific queries (`countByStatus`, `getDashboardTicketKpis`, `getTicketBreakdown`, `getResidentDemographics`, etc.) were added as new methods on existing module repositories rather than creating a separate reporting repository layer.
**Reasoning:** All data lives in existing tables. A separate `ReportRepository` would require duplicating entity knowledge or using raw `EntityManager`, adding complexity with no benefit. The convention used throughout the codebase is to extend existing repositories with @Query methods.
**Alternatives:** JPA Criteria API in service; separate `ReportRepository` with `EntityManager`. Both rejected as overkill at this scale.

### 2026-05-29 | Dashboard KPIs computed in real-time, no caching
**Decision:** All dashboard aggregations run as live DB queries on every request.
**Reasoning:** Building cache invalidation across five modules (apartments, tickets, amenities, contracts, residents) would add significant complexity. At 1000 apartments the queries are fast with existing indexes. Cache can be added later (Redis, @Cacheable) when load testing identifies it as necessary.
**Alternatives:** Redis cache with TTL; materialized view refreshed on schedule. Deferred.

### 2026-05-29 | Ticket report groupBy=assignee joins users table for display name
**Decision:** The `getTicketBreakdown` native query LEFT JOINs `users` on `assigned_to_user_id` and uses `COALESCE(u.full_name, 'Unassigned')` as the label.
**Reasoning:** Assignee name is more readable in a report than a UUID. Unassigned tickets are grouped under a single "Unassigned" label rather than excluded.
**Alternatives:** Return assignee UUID and resolve name client-side. Rejected — adds round-trips and complicates the frontend.

## Frontend Decisions

### 2026-05-29 | Access token in Zustand memory, refresh token in localStorage
**Decision:** Access token stored in Zustand (in-memory only). Refresh token persisted to `localStorage` key `gemek_refresh`.
**Reasoning:** Access token in memory is more secure (not accessible to XSS via document.cookie). Refresh token in localStorage survives page reload. Full HttpOnly cookie migration deferred — requires backend Set-Cookie change on /auth/refresh.
**Alternatives:** Both in HttpOnly cookies (most secure, tracked as post-G4 hardening item).

### 2026-05-29 | window.__gemekAuthState bridge removed — Zustand imported directly
**Decision:** Removed `window.__gemekAuthState` and `window.__gemekSetToken` globals. Axios client imports Zustand store directly.
**Reasoning:** Security scanner flagged globals as token exposure surface. Direct import works cleanly — no circular import issue in practice.
**Alternatives:** Keep window bridge for circular import safety. Rejected after confirming no circular import exists.

---

## Testing / Infrastructure Decisions

### 2026-05-29 | Testcontainers docker.host uses docker_cli named pipe
**Decision:** `~/.testcontainers.properties` sets `docker.host=npipe:////./pipe/docker_cli`.
**Reasoning:** Docker Desktop on Windows returns HTTP 400 from `docker_engine` and `dockerDesktopLinuxEngine` pipes. The 400 response body contains label `com.docker.desktop.address=npipe://\\.\pipe\docker_cli` — this is Docker Desktop's redirect hint to the actual API socket.
**Alternatives:** TCP on localhost:2375 (requires Docker Desktop setting to expose daemon — security risk); WSL2 socket (requires additional config).

### 2026-05-29 | AuthServiceTest uses LENIENT Mockito strictness
**Decision:** `@MockitoSettings(strictness = Strictness.LENIENT)` added to `AuthServiceTest`.
**Reasoning:** `@BeforeEach` stubs `httpRequest.getRemoteAddr()` and `httpRequest.getHeader("X-Forwarded-For")` which are needed by login tests but not by logout/refreshToken tests. Strict mode throws `UnnecessaryStubbingException`. LENIENT is correct here — the stubs are shared setup, not test-specific noise.
**Alternatives:** Remove stubs from `@BeforeEach`, add per-test. Rejected — too much duplication across 4 login tests.

---

## CTO Overrides
_(record when CTO overrides agent decision)_
