# Architecture — Apartment Management System

**Version:** 2.0
**Date:** 2026-05-29
**Author:** Architect Agent
**Scale target:** ~1000 apartments, small management team (< 20 staff)

---

## 1. Techstack

### 1.1 Decision Summary

| Layer | Choice | Justification |
|-------|--------|---------------|
| Backend runtime | Java 21 (LTS) | Virtual threads (Project Loom) reduce thread overhead for I/O-heavy notification dispatching; strong type safety; LTS lifecycle matches project horizon |
| Backend framework | Spring Boot 3.3.x | VTIT team standard; mature security, data, and web layers; native Docker image via Buildpacks; method-security annotations map cleanly to RBAC requirements |
| Database | PostgreSQL 15 | ACID compliant; native ENUM types for status/category columns; JSONB for audit log payloads; partial indexes for SLA queries; proven at this scale |
| ORM / migrations | Spring Data JPA + Hibernate 6 + Flyway | JPA reduces boilerplate; native queries available for complex reporting aggregations; Flyway SQL-first migrations are version-controlled and auto-apply on startup |
| Auth | Spring Security 6 + JWT (JJWT 0.12) | Stateless; no session affinity needed for single-instance deployment; role-based method security via `@PreAuthorize` |
| Token store | Redis 7 | JWT blocklist (logout/revoke) with TTL matching remaining token life; rate-limiting counters; short-lived availability cache for amenity booking calendar |
| File storage | MinIO (S3-compatible, self-hosted) | No cloud vendor lock-in; Docker-native; handles ticket photos (BEFORE/PROGRESS/AFTER) and contract PDF attachments; presigned GET URLs for secure direct browser access |
| Frontend framework | React 18 + Vite 5 + TypeScript 5 | Fast HMR in development; strong type safety catches API contract drift early; large ecosystem |
| Frontend UI | Tailwind CSS 3 + shadcn/ui | Utility-first; consistent accessible component primitives; desktop-first breakpoints for admin, mobile-first overrides for resident portal |
| Frontend state | TanStack Query v5 + Zustand | TanStack Query owns all server state (caching, background refetch, optimistic updates); Zustand owns ephemeral client state (auth tokens, UI state) |
| Frontend workspace | pnpm workspace with two React apps | `apps/admin` (desktop-first) + `apps/resident` (mobile-first) share a `packages/ui` component library; independent build and deployment artifacts |
| Push notifications | Firebase Cloud Messaging (FCM) | Industry standard; free tier covers 1000 residents; Firebase Admin SDK integrates cleanly with Spring Boot |
| Email | JavaMailSender (SMTP) | Standard Spring Boot integration; configurable SMTP relay (internal relay, SendGrid, or SES) via environment variables |
| SMS | Pluggable `SmsGateway` interface (default: no-op) | Vietnamese telco SMS APIs vary by provider; abstracted so a real provider can be wired via `@ConditionalOnProperty` without code changes |
| API documentation | SpringDoc OpenAPI 3 | Auto-generated from controller annotations; accessible at `/swagger-ui.html` in dev profile |
| Container / infra | Docker + Docker Compose | Required constraint; single-command startup; all inter-service communication over Docker internal DNS |
| Reverse proxy | Nginx 1.25 | Serves React build artifacts; proxies `/api/*` to Spring Boot; SSL termination point for production |

### 1.2 Architecture Style: Modular Monolith

**Decision: Modular Monolith** — single deployable JAR, internally structured by feature packages with clean module boundaries.

**Rationale:**
- 1000 apartments, small operations team: the operational overhead of microservices (distributed tracing, service mesh, per-service CI pipelines, network latency between services) is not justified at this scale.
- Cross-module operations benefit from single-transaction semantics — for example, assigning a ticket to a contractor updates ticket status and contractor work history atomically.
- Single `docker-compose.yml` satisfies the stated constraint without service discovery complexity.
- Module boundaries are designed to be clean enough that extraction to separate services is tractable if scale demands it later.

**Tradeoff accepted:** All modules scale together on a single JVM. For 1000 apartments with a small team this is acceptable. If the resident portal were to reach tens of thousands of concurrent users a separate read-optimized service would be warranted; that is not the stated scale.

### 1.3 Single Database

One PostgreSQL instance, all tables in the `public` schema, separated by naming convention.

**Rationale:** No inter-service network calls; join queries for reporting are trivial and efficient; connection pool is shared. At 1000 apartments data volume remains under 10 GB for many years of operation.

---

## 2. System Component Diagram

```
┌─────────────────────────────────────────────────────────────────────────┐
│                          Docker Compose Network                          │
│                                                                          │
│  ┌──────────────┐    ┌───────────────────────────────────────────────┐  │
│  │    Nginx     │    │            Spring Boot 3 (API)  :8080          │  │
│  │  :80 / :443  │───▶│                                                │  │
│  │              │    │  ┌──────────┐  ┌───────────┐  ┌────────────┐ │  │
│  │  /           │    │  │   Auth   │  │   Users   │  │ Apartments │ │  │
│  │  (React SPA) │    │  │  Module  │  │  Module   │  │  & Blocks  │ │  │
│  │              │    │  ├──────────┤  ├───────────┤  ├────────────┤ │  │
│  │  /api/*      │    │  │Residents │  │  Tickets  │  │  Parking   │ │  │
│  │  (proxy)     │    │  │  Module  │  │  Module   │  │  Module    │ │  │
│  └──────────────┘    │  ├──────────┤  ├───────────┤  ├────────────┤ │  │
│                      │  │Amenities │  │Contractors│  │Announce-   │ │  │
│                      │  │  Module  │  │& Contracts│  │ments Module│ │  │
│                      │  ├──────────┤  ├───────────┤  ├────────────┤ │  │
│                      │  │ Reports  │  │   RBAC /  │  │   Files /  │ │  │
│                      │  │  Module  │  │   Audit   │  │    MinIO   │ │  │
│                      │  └──────────┘  └───────────┘  └────────────┘ │  │
│                      │                                                │  │
│                      │  ┌──────────────────────────────────────────┐ │  │
│                      │  │        Cross-cutting Infrastructure       │ │  │
│                      │  │  JWT Filter │ Audit AOP │ Rate Limiter   │ │  │
│                      │  │  Global Exception Handler │ MinIO Client  │ │  │
│                      │  │  Notification Dispatcher (fire-&-forget)  │ │  │
│                      │  └──────────────────────────────────────────┘ │  │
│                      │                                                │  │
│                      │  ┌──────────────────────────────────────────┐ │  │
│                      │  │     Scheduled Jobs (Spring @Scheduled)    │ │  │
│                      │  │  ContractExpiryScheduler (nightly)        │ │  │
│                      │  │  SlaAlertScheduler (hourly)               │ │  │
│                      │  │  MaintenanceScheduleRunner (daily)        │ │  │
│                      │  │  BookingCompletionScheduler (hourly)      │ │  │
│                      │  └──────────────────────────────────────────┘ │  │
│                      └───────┬──────────────┬───────────┬────────────┘  │
│                              │              │           │               │
│                    ┌─────────▼──────┐ ┌─────▼───┐ ┌───▼────────┐      │
│                    │  PostgreSQL 15  │ │ Redis 7 │ │   MinIO    │      │
│                    │     :5432       │ │  :6379  │ │ :9000/:9001│      │
│                    └────────────────┘ └─────────┘ └────────────┘      │
│                                                                          │
│  ┌────────────────────────────────────────────────────────────────────┐ │
│  │                       External Services                             │ │
│  │   Firebase FCM (push)  │  SMTP Relay (email)  │  SMS Gateway (opt) │ │
│  └────────────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────────┘

Client Portals:
  Admin Portal    (desktop-first)  ──▶  Nginx :80 → React SPA (apps/admin)
  Resident Portal (mobile-first)   ──▶  Nginx :80 → React SPA (apps/resident)
  Both portals call:  /api/*  →  Spring Boot API
```

---

## 3. Project Structure

### 3.1 Spring Boot Backend

```
gemek-premium-backend/
├── src/main/java/vn/vtit/gemek/
│   ├── GemekApplication.java                      # @SpringBootApplication entry point
│   │
│   ├── config/                                    # Cross-cutting configuration beans
│   │   ├── SecurityConfig.java                    # Spring Security filter chain + CORS
│   │   ├── JwtConfig.java                         # JWT properties + bean
│   │   ├── RedisConfig.java                       # Lettuce connection, cache manager
│   │   ├── MinioConfig.java                       # MinIO client bean
│   │   ├── FirebaseConfig.java                    # FCM Admin SDK initialisation
│   │   ├── OpenApiConfig.java                     # SpringDoc global configuration
│   │   └── AuditConfig.java                       # Spring Data auditing bean
│   │
│   ├── common/                                    # Shared utilities — no domain logic
│   │   ├── exception/
│   │   │   ├── AppException.java                  # Runtime exception with ErrorCode
│   │   │   ├── ErrorCode.java                     # Enum of all ERROR_CODE constants
│   │   │   └── GlobalExceptionHandler.java        # @RestControllerAdvice
│   │   ├── model/
│   │   │   ├── ApiResponse.java                   # Standard single-object wrapper
│   │   │   └── PageResponse.java                  # Paginated response wrapper
│   │   ├── security/
│   │   │   ├── JwtTokenProvider.java
│   │   │   ├── JwtAuthenticationFilter.java
│   │   │   └── UserPrincipal.java
│   │   ├── audit/
│   │   │   └── AuditLogAspect.java                # @Around AOP for @Auditable methods
│   │   ├── storage/
│   │   │   └── FileStorageService.java            # MinIO upload + presigned URL
│   │   └── notification/
│   │       ├── NotificationDispatcher.java        # Orchestrates FCM / email / SMS
│   │       ├── EmailService.java
│   │       ├── SmsGateway.java                    # Interface — pluggable
│   │       └── FcmService.java
│   │
│   ├── module/
│   │   ├── auth/
│   │   │   ├── AuthController.java
│   │   │   ├── AuthService.java
│   │   │   ├── AuthServiceImpl.java
│   │   │   └── dto/
│   │   │       ├── LoginRequest.java
│   │   │       ├── LoginResponse.java
│   │   │       └── RefreshTokenRequest.java
│   │   │
│   │   ├── user/
│   │   │   ├── UserController.java
│   │   │   ├── UserService.java
│   │   │   ├── UserServiceImpl.java
│   │   │   ├── entity/User.java
│   │   │   ├── repository/UserRepository.java
│   │   │   └── dto/
│   │   │
│   │   ├── apartment/
│   │   │   ├── ApartmentController.java
│   │   │   ├── ApartmentService.java
│   │   │   ├── ApartmentServiceImpl.java
│   │   │   ├── BlockController.java
│   │   │   ├── BlockService.java
│   │   │   ├── BlockServiceImpl.java
│   │   │   ├── entity/
│   │   │   │   ├── Apartment.java
│   │   │   │   └── Block.java
│   │   │   ├── repository/
│   │   │   │   ├── ApartmentRepository.java
│   │   │   │   └── BlockRepository.java
│   │   │   └── dto/
│   │   │
│   │   ├── resident/
│   │   │   ├── ResidentController.java
│   │   │   ├── ResidentService.java
│   │   │   ├── ResidentServiceImpl.java
│   │   │   ├── entity/
│   │   │   │   ├── Resident.java
│   │   │   │   └── ResidentHistory.java
│   │   │   ├── repository/
│   │   │   │   ├── ResidentRepository.java
│   │   │   │   └── ResidentHistoryRepository.java
│   │   │   └── dto/
│   │   │
│   │   ├── vehicle/
│   │   │   ├── VehicleController.java
│   │   │   ├── VehicleService.java
│   │   │   ├── VehicleServiceImpl.java
│   │   │   ├── entity/Vehicle.java
│   │   │   ├── repository/VehicleRepository.java
│   │   │   └── dto/
│   │   │
│   │   ├── parking/
│   │   │   ├── ParkingController.java
│   │   │   ├── ParkingService.java
│   │   │   ├── ParkingServiceImpl.java
│   │   │   ├── entity/
│   │   │   │   ├── ParkingSlot.java
│   │   │   │   ├── ParkingAssignment.java
│   │   │   │   └── GuestVehicle.java
│   │   │   ├── repository/
│   │   │   │   ├── ParkingSlotRepository.java
│   │   │   │   ├── ParkingAssignmentRepository.java
│   │   │   │   └── GuestVehicleRepository.java
│   │   │   └── dto/
│   │   │
│   │   ├── ticket/                                # Replaces the old maintenance module
│   │   │   ├── TicketController.java
│   │   │   ├── TicketService.java
│   │   │   ├── TicketServiceImpl.java
│   │   │   ├── entity/
│   │   │   │   ├── Ticket.java                    # Core ticket entity
│   │   │   │   ├── TicketPhoto.java               # BEFORE / PROGRESS / AFTER photos
│   │   │   │   └── TicketStatusHistory.java       # Immutable status transition log
│   │   │   ├── repository/
│   │   │   │   ├── TicketRepository.java
│   │   │   │   ├── TicketPhotoRepository.java
│   │   │   │   └── TicketStatusHistoryRepository.java
│   │   │   └── dto/
│   │   │       ├── TicketCreateRequest.java
│   │   │       ├── TicketAssignRequest.java
│   │   │       ├── TicketStatusUpdateRequest.java
│   │   │       ├── TicketRatingRequest.java
│   │   │       ├── TicketSummaryResponse.java
│   │   │       └── TicketDetailResponse.java
│   │   │
│   │   ├── amenity/
│   │   │   ├── AmenityController.java
│   │   │   ├── AmenityService.java
│   │   │   ├── AmenityServiceImpl.java
│   │   │   ├── entity/
│   │   │   │   ├── Amenity.java
│   │   │   │   └── AmenityBooking.java
│   │   │   ├── repository/
│   │   │   │   ├── AmenityRepository.java
│   │   │   │   └── AmenityBookingRepository.java
│   │   │   └── dto/
│   │   │
│   │   ├── contractor/
│   │   │   ├── ContractorController.java
│   │   │   ├── ContractorService.java
│   │   │   ├── ContractorServiceImpl.java
│   │   │   ├── ContractController.java
│   │   │   ├── ContractService.java
│   │   │   ├── ContractServiceImpl.java
│   │   │   ├── entity/
│   │   │   │   ├── Contractor.java
│   │   │   │   ├── Contract.java
│   │   │   │   ├── ContractPayment.java
│   │   │   │   └── MaintenanceSchedule.java
│   │   │   ├── repository/
│   │   │   │   ├── ContractorRepository.java
│   │   │   │   ├── ContractRepository.java
│   │   │   │   ├── ContractPaymentRepository.java
│   │   │   │   └── MaintenanceScheduleRepository.java
│   │   │   └── dto/
│   │   │
│   │   ├── announcement/
│   │   │   ├── AnnouncementController.java
│   │   │   ├── AnnouncementService.java
│   │   │   ├── AnnouncementServiceImpl.java
│   │   │   ├── entity/
│   │   │   │   ├── Announcement.java
│   │   │   │   └── AnnouncementRead.java
│   │   │   ├── repository/
│   │   │   │   ├── AnnouncementRepository.java
│   │   │   │   └── AnnouncementReadRepository.java
│   │   │   └── dto/
│   │   │
│   │   ├── report/
│   │   │   ├── ReportController.java
│   │   │   ├── ReportService.java
│   │   │   ├── ReportServiceImpl.java
│   │   │   └── dto/
│   │   │
│   │   └── notification/
│   │       ├── NotificationController.java
│   │       ├── entity/Notification.java
│   │       └── repository/NotificationRepository.java
│   │
│   └── scheduler/
│       ├── ContractExpiryScheduler.java           # Nightly — alerts for contracts expiring within 30/60/90 days
│       ├── MaintenanceScheduleRunner.java          # Daily — tickets for due recurring schedules
│       ├── SlaAlertScheduler.java                 # Hourly — detect SLA breaches, notify admin
│       └── BookingCompletionScheduler.java        # Hourly — mark past approved bookings as COMPLETED
│
├── src/main/resources/
│   ├── application.yml                            # Main config (reads from env vars)
│   ├── application-dev.yml                        # Dev profile overrides
│   └── db/migration/
│       ├── V1__enums_and_tables.sql               # All ENUM types and tables
│       ├── V2__indexes.sql                        # All indexes (separate for readability)
│       └── V3__seed_data.sql                      # Reference data and default admin
│
├── src/test/java/vn/vtit/gemek/
│   └── module/                                    # Mirror of main module structure
│
├── Dockerfile
└── pom.xml

gemek-premium-frontend/
├── apps/
│   ├── admin/                                     # Admin portal — desktop-first
│   │   ├── src/
│   │   │   ├── pages/
│   │   │   ├── components/
│   │   │   ├── hooks/
│   │   │   └── main.tsx
│   │   └── vite.config.ts
│   └── resident/                                  # Resident portal — mobile-first
│       ├── src/
│       │   ├── pages/
│       │   ├── components/
│       │   ├── hooks/
│       │   └── main.tsx
│       └── vite.config.ts
├── packages/
│   └── ui/                                        # Shared shadcn/ui components
├── docker/
│   └── nginx.conf
└── package.json                                   # pnpm workspace root

docker-compose.yml                                 # Root compose — single-command startup
.env.example                                       # Template — never commit .env
```

---

## 4. Key Architectural Decisions

### 4.1 Ticket Module — Replaces Maintenance Requests

The domain concept is called **Ticket** (not maintenance request) to reflect that residents submit any type of request. The `ticket_category` column is a PostgreSQL ENUM with five values that govern routing rules.

**Ticket Category Routing Rules:**

| Category | Assignable to internal staff | Assignable to contractor | Notes |
|----------|------------------------------|--------------------------|-------|
| `MAINTENANCE_REPAIR` | Yes (TECHNICIAN or ADMIN) | Yes | Only category where contractor assignment is valid |
| `COMPLAINT` | Yes (ADMIN or TECHNICIAN) | No | Noise, neighbor disputes, cleanliness |
| `ADMINISTRATIVE` | Yes (ADMIN only) | No | Document requests, key card, info update |
| `SUGGESTION_FEEDBACK` | Routes to admin review queue; no explicit assignee | No | Ideas and feedback — admin reviews and may act |
| `OTHER` | Yes (TECHNICIAN or ADMIN) | No | Catch-all for unclassified requests |

**Database enforcement:** A CHECK constraint on the `tickets` table ensures `assigned_to_contractor_id IS NULL OR category = 'MAINTENANCE_REPAIR'`. Service layer also validates before persisting.

**SLA defaults per category:**

| Category | Default SLA (hours) | Default Priority |
|----------|---------------------|-----------------|
| `MAINTENANCE_REPAIR` | 24 | MEDIUM |
| `COMPLAINT` | 48 | MEDIUM |
| `ADMINISTRATIVE` | 72 | LOW |
| `SUGGESTION_FEEDBACK` | 168 (7 days) | LOW |
| `OTHER` | 48 | LOW |

SLA deadline is computed at INSERT time as `created_at + interval '<sla_hours> hours'` and stored in `tickets.sla_deadline`. The `SlaAlertScheduler` runs hourly and notifies admin when tickets breach their deadline.

### 4.2 JWT Token Strategy

- **Access token:** 15-minute expiry; signed HS256; payload contains `userId`, `email`, `role`.
- **Refresh token:** 7-day expiry; stored in Redis under key `refresh:<userId>:<jti>`; single-device revocation by deleting the Redis key.
- **Logout:** adds access token JTI to Redis blocklist key `blocklist:<jti>` with TTL matching the token's remaining valid time.
- **Rationale:** Stateless verification for 99% of requests; Redis escape hatch for logout/revocation without introducing sessions.

### 4.3 File Upload Strategy

- Clients upload ticket photos via `POST /api/tickets/{id}/photos` with `multipart/form-data`.
- Backend streams bytes to MinIO, stores the resulting object key (not full URL) in `ticket_photos.file_url`.
- Frontend fetches presigned GET URLs from `GET /api/files/presign?objectKey=...` — 1-hour expiry, validated against the requesting user's permission.
- Contract PDF attachments follow the same pattern.
- Max file size: 10 MB per photo, 5 photos per upload batch (configurable via environment variable).

### 4.4 Notification Delivery — Fire-and-Forget

- A `notifications` table record is created synchronously inside the business transaction, guaranteeing the in-app notification is never lost.
- FCM push, SMTP email, and SMS dispatch are performed in a `@Async` thread pool after the transaction commits.
- External delivery failures are logged at WARN level and do not roll back the business transaction.
- SMS is a no-op by default; a real provider is injected via `@ConditionalOnProperty("app.sms.provider")`.

### 4.5 Amenity Booking Conflict Prevention

- Before inserting a booking, a `SELECT ... FOR UPDATE` on `amenity_bookings` checks for overlapping approved/pending bookings for the same amenity and date.
- Redis caches daily booking counts per amenity (key `amenity:availability:<id>:<date>`) for fast calendar rendering. Cache invalidated on any booking status change.

### 4.6 Audit Log Strategy

- An AOP `@Around` aspect intercepts all `@Service` methods annotated with `@Auditable`.
- Records: entity type, entity ID, serialized old/new values (JSONB), user ID from security context, IP from `HttpServletRequest`.
- Audit log is append-only — no UPDATE or DELETE on `audit_logs`.
- Old/new JSONB values are indexed with a GIN index to support change searches.

### 4.7 RBAC Implementation

- Four roles: `ADMIN`, `TECHNICIAN`, `RESIDENT`, `BOARD_MEMBER`.
- Method-level security via `@PreAuthorize("hasRole('ADMIN')")` annotations on service methods.
- Resource ownership enforced in service layer: residents may only read/write resources belonging to their own apartment; technicians may only update tickets assigned to them.
- `BOARD_MEMBER` has read-only access to reports and dashboard — enforced by allowing only GET methods on report endpoints.

### 4.8 Contractor Rating Computation

- `contractors.rating` is a `NUMERIC(3,2)` column holding the computed average.
- Recalculated in a service call whenever a ticket assigned to that contractor receives a resident rating.
- Not recomputed on every read — stored value is good enough given low write frequency.

### 4.9 UUID Primary Keys

All tables use `UUID DEFAULT gen_random_uuid()` as primary key.

**Rationale:** Prevents sequential ID enumeration attacks on REST endpoints; enables safe client-side ID pre-generation if needed in future; consistent across all entities.

### 4.10 MinIO Object Key Strategy

Object key format: `<entity-type>/<entityId>/<phase>/<uuid>.<ext>`

Examples:
- `tickets/3f2a.../before/img-001.jpg`
- `contracts/7b1c.../attachment/contract.pdf`
- `announcements/9d4e.../attachment/notice.pdf`

This structure allows prefix-based listing and deletion when a parent entity is removed (future cleanup job).

### 4.11 Docker Compose Services

| Service | Image | Internal Port | Exposed to host |
|---------|-------|---------------|----------------|
| `nginx` | `nginx:1.25-alpine` | 80, 443 | 80, 443 |
| `api` | `gemek/backend:latest` (local build) | 8080 | No |
| `postgres` | `postgres:15-alpine` | 5432 | No |
| `redis` | `redis:7-alpine` | 6379 | No |
| `minio` | `minio/minio:RELEASE.2024-...` (pinned) | 9000, 9001 | No (9001 admin UI optional in dev) |

All inter-service communication uses Docker internal DNS service names. Only Nginx ports are exposed to the host. No `latest` tags — all images pinned to specific versions in `docker-compose.yml`.

---

## 5. Security Considerations

- All secrets (DB password, Redis password, JWT secret, MinIO credentials, FCM key, SMTP password) injected exclusively via environment variables — never in source.
- Input validation via Jakarta Bean Validation (`@NotBlank`, `@Size`, `@Email`, `@Min`, `@Max`) on all request DTOs; `GlobalExceptionHandler` returns `400 VALIDATION_ERROR` on failure.
- Rate limiting via Redis: 10 req/min per IP on `POST /api/auth/login`; 20 req/min per user on refresh; 120 req/min per authenticated user for all other endpoints.
- File upload: MIME type validated against allowed list (image/jpeg, image/png, application/pdf); filename sanitized before storage.
- SQL injection prevented: all DB access via JPA/Hibernate parameterized binding; no native string concatenation with user input.
- CORS configured to allow only the known frontend origin(s) — not `*` — via `SecurityConfig`.
- Passwords: BCrypt with cost factor 12.
- Sensitive fields (password hash, phone numbers) never appear in log output.
- Presigned MinIO URLs validated: backend checks the requesting user's permission to the object's parent entity before issuing the URL.

---

## 6. Risks and Mitigations

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|------------|
| MinIO disk exhaustion from ticket photos and contract PDFs | Medium | High | MinIO lifecycle policy to move old objects to cold storage or archive; disk usage alert via Docker healthcheck or external monitoring |
| FCM device token expiry when resident reinstalls app | Medium | Low | FCM token refreshed on each app startup via `PUT /api/auth/me/fcm-token`; stale tokens silently dropped by FCM |
| SLA scheduler missing breaches during restart/downtime | Low | Medium | Scheduler is idempotent — catches up on restart; `sla_deadline` is a stored column so no state is lost |
| Amenity double-booking under concurrent request load | Low | High | `SELECT ... FOR UPDATE` at DB level; single instance removes distributed concurrency concern |
| Redis unavailable — JWT blocklist inaccessible | Low | Medium | Fail-open: short 15-min access token lifetime limits exposure window; monitoring alert on Redis downtime |
| Flyway migration fails on startup blocking the API | Low | High | Migration scripts tested in CI pipeline; scripts are versioned and never edited post-deployment; rollback plan documented in migration comments |
| Ticket routed to contractor for a non-MAINTENANCE_REPAIR category | Low | Medium | Dual enforcement: CHECK constraint in DB (cannot be bypassed) + service-layer validation returning `400 VALIDATION_ERROR` |
| Contractor rating skewed by malicious resident | Low | Low | Rating is only accepted when ticket status = DONE and submitted by the apartment's own resident; one rating per ticket |
