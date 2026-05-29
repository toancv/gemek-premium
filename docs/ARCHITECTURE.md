# Architecture — Apartment Management System

**Version:** 1.0  
**Date:** 2026-05-29  
**Author:** Architect Agent  
**Scale target:** ~1000 apartments, small management team (< 20 staff)

---

## 1. Techstack

### 1.1 Decision Summary

| Layer | Choice | Justification |
|-------|--------|---------------|
| Backend runtime | Java 21 (LTS) | Virtual threads (Project Loom) reduce thread overhead for I/O-heavy workloads; strong type safety; excellent ecosystem |
| Backend framework | Spring Boot 3.3.x | De-facto enterprise standard; mature security, data, and web layers; native Docker image support via Buildpacks; team convention at VTIT |
| Database | PostgreSQL 15 | ACID compliant; JSONB for audit log values; proven at this scale; excellent Docker support; rich index types (GIN, partial) |
| ORM | Spring Data JPA + Hibernate 6 | Reduces boilerplate; native queries available for complex reporting; works well with Flyway migrations |
| DB migrations | Flyway | SQL-first migrations; version-controlled schema changes; Spring Boot auto-apply on startup |
| Auth | Spring Security 6 + JWT (JJWT 0.12) | Stateless; no session affinity needed for single instance; role-based method security via annotations |
| Cache | Redis 7 | JWT blocklist (logout/revoke); rate limiting counters; short-lived availability checks for amenity booking |
| File storage | MinIO (S3-compatible) | Self-hosted; no cloud vendor lock-in; Docker-native; handles maintenance photos and contract PDFs; presigned URLs for secure direct access |
| Frontend framework | React 18 + Vite 5 + TypeScript 5 | Fast HMR for dev; strong type safety; large ecosystem |
| Frontend UI | Tailwind CSS 3 + shadcn/ui | Utility-first; consistent design system; accessible components; desktop-first with responsive breakpoints |
| Frontend state | TanStack Query v5 + Zustand | Server state via TanStack Query (caching, refetch); client state (auth, UI) via Zustand |
| Push notifications | Firebase Cloud Messaging (FCM) | Industry standard; free tier sufficient for 1000 residents; backend uses Firebase Admin SDK |
| Email | JavaMailSender (SMTP) | Standard Spring integration; configurable SMTP relay (SendGrid, SES, or internal server) |
| SMS | Pluggable interface (default: no-op) | Vietnamese telco SMS APIs vary; abstracted behind `SmsGateway` interface; wire in real provider without code change |
| API documentation | SpringDoc OpenAPI 3 (Swagger UI) | Auto-generated from annotations; accessible at /swagger-ui.html in dev |
| Container | Docker + Docker Compose | Required constraint; single-command startup |
| Reverse proxy | Nginx (in compose) | Static file serving for React build; proxy pass to Spring Boot; SSL termination point |

### 1.2 Architecture Style Decision: Monolith

**Decision: Modular Monolith** — single deployable JAR, internally structured by feature packages.

**Rationale:**
- 1000 apartments, small team: operational complexity of microservices (distributed tracing, service mesh, separate CI pipelines) is not justified.
- Single DB transaction across modules (e.g., assigning a maintenance ticket updates contractor work history in the same commit).
- Single Docker Compose file; no service discovery overhead.
- Can be extracted to services later if scale demands — the internal module boundaries are designed to be clean.

**Tradeoff accepted:** All modules scale together. For this problem domain that is acceptable.

### 1.3 Single Database Decision

One PostgreSQL instance, multiple schemas are NOT used — all tables in the public schema, separated by naming convention.

**Rationale:** No inter-service network calls; join queries for reporting are trivial; connection pool shared efficiently. At 1000 apartments the data volume is modest (< 10 GB for years of operation).

---

## 2. System Component Diagram

```
┌─────────────────────────────────────────────────────────────────────┐
│                          Docker Compose Network                      │
│                                                                      │
│  ┌──────────┐    ┌─────────────────────────────────────────────┐    │
│  │  Nginx   │    │              Spring Boot 3 (API)             │    │
│  │  :80/443 │───▶│                  :8080                       │    │
│  │          │    │  ┌───────────┐  ┌──────────┐  ┌──────────┐ │    │
│  │  /       │    │  │   Auth    │  │Residents │  │Maintnce  │ │    │
│  │  (React) │    │  │  Module   │  │ Module   │  │ Module   │ │    │
│  │          │    │  ├───────────┤  ├──────────┤  ├──────────┤ │    │
│  │  /api/*  │    │  │Amenities  │  │Contracts │  │Parking   │ │    │
│  │  (proxy) │    │  │  Module   │  │  Module  │  │ Module   │ │    │
│  └──────────┘    │  ├───────────┤  ├──────────┤  ├──────────┤ │    │
│                  │  │Announce   │  │ Reports  │  │  RBAC /  │ │    │
│                  │  │  Module   │  │  Module  │  │  Audit   │ │    │
│                  │  └───────────┘  └──────────┘  └──────────┘ │    │
│                  │                                              │    │
│                  │  ┌──────────────────────────────────────┐  │    │
│                  │  │      Cross-cutting Infrastructure     │  │    │
│                  │  │  JWT Filter │ Audit AOP │ Rate Limit  │  │    │
│                  │  │  Exception Handler │ MinIO Client     │  │    │
│                  │  └──────────────────────────────────────┘  │    │
│                  └───────┬──────────────┬───────────┬──────────┘    │
│                          │              │           │               │
│                  ┌───────▼──────┐ ┌─────▼───┐ ┌───▼────────┐      │
│                  │  PostgreSQL  │ │  Redis  │ │   MinIO    │      │
│                  │     :5432    │ │  :6379  │ │   :9000    │      │
│                  └──────────────┘ └─────────┘ └────────────┘      │
│                                                                      │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │                    External Services                          │   │
│  │  Firebase FCM (push)  │  SMTP Relay (email)  │  SMS Gateway  │   │
│  └──────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────┘

Client Portals:
  Admin Portal  (desktop-first)  ──▶  Nginx :80 → React SPA → /api/*
  Resident Portal (mobile-first) ──▶  Nginx :80 → React SPA → /api/*
  (Two separate React apps or one SPA with role-based routing — decided at frontend phase)
```

---

## 3. Project Structure (Spring Boot Monolith)

```
gemek-premium-backend/
├── src/main/java/vn/vtit/gemek/
│   ├── GemekApplication.java                    # @SpringBootApplication entry point
│   │
│   ├── config/                                  # Cross-cutting configuration
│   │   ├── SecurityConfig.java                  # Spring Security filter chain
│   │   ├── JwtConfig.java                       # JWT properties + bean
│   │   ├── RedisConfig.java                     # Lettuce connection, cache manager
│   │   ├── MinioConfig.java                     # MinIO client bean
│   │   ├── FirebaseConfig.java                  # FCM admin SDK init
│   │   ├── OpenApiConfig.java                   # SpringDoc configuration
│   │   └── AuditConfig.java                     # Spring Data auditing
│   │
│   ├── common/                                  # Shared utilities, no domain logic
│   │   ├── exception/
│   │   │   ├── AppException.java
│   │   │   ├── ErrorCode.java                   # Enum of all ERROR_CODE constants
│   │   │   └── GlobalExceptionHandler.java      # @RestControllerAdvice
│   │   ├── model/
│   │   │   ├── ApiResponse.java                 # Standard wrapper
│   │   │   └── PageResponse.java                # Paginated response wrapper
│   │   ├── security/
│   │   │   ├── JwtTokenProvider.java
│   │   │   ├── JwtAuthenticationFilter.java
│   │   │   └── UserPrincipal.java
│   │   ├── audit/
│   │   │   └── AuditLogAspect.java              # AOP aspect for audit logging
│   │   ├── storage/
│   │   │   └── FileStorageService.java          # MinIO upload/presigned URL
│   │   └── notification/
│   │       ├── NotificationService.java
│   │       ├── EmailService.java
│   │       ├── SmsGateway.java                  # Interface — pluggable
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
│   │   │   └── dto/
│   │   │
│   │   ├── maintenance/
│   │   │   ├── MaintenanceController.java
│   │   │   ├── MaintenanceService.java
│   │   │   ├── MaintenanceServiceImpl.java
│   │   │   ├── entity/
│   │   │   │   ├── MaintenanceRequest.java
│   │   │   │   ├── MaintenanceCategory.java
│   │   │   │   ├── MaintenancePhoto.java
│   │   │   │   └── MaintenanceStatusHistory.java
│   │   │   ├── repository/
│   │   │   └── dto/
│   │   │
│   │   ├── amenity/
│   │   │   ├── AmenityController.java
│   │   │   ├── AmenityService.java
│   │   │   ├── AmenityServiceImpl.java
│   │   │   ├── entity/
│   │   │   │   ├── Amenity.java
│   │   │   │   └── AmenityBooking.java
│   │   │   ├── repository/
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
│       ├── ContractExpiryScheduler.java         # Nightly check expiring contracts
│       ├── MaintenanceScheduleRunner.java        # Daily check due maintenance schedules
│       └── SlaAlertScheduler.java               # Hourly SLA breach check
│
├── src/main/resources/
│   ├── application.yml                          # Main config (reads from env vars)
│   ├── application-dev.yml                      # Dev overrides
│   └── db/migration/                            # Flyway SQL scripts
│       ├── V1__initial_schema.sql
│       ├── V2__seed_data.sql
│       └── V3__indexes.sql
│
├── src/test/java/vn/vtit/gemek/
│   └── module/                                  # Mirror of main module structure
│
├── Dockerfile
└── pom.xml

gemek-premium-frontend/
├── apps/
│   ├── admin/                                   # Admin portal (desktop-first)
│   └── resident/                                # Resident portal (mobile-first)
│       (or single SPA with role routing — decided in frontend phase)
├── packages/
│   └── ui/                                      # Shared component library
├── docker/
│   └── nginx.conf
└── package.json (pnpm workspace)

docker-compose.yml                               # Root compose file
.env.example                                     # Template — never commit .env
```

---

## 4. Key Architectural Decisions

### 4.1 JWT Token Strategy
- **Access token:** 15-minute expiry; signed with HS256; contains user ID, email, role.
- **Refresh token:** 7-day expiry; stored in Redis (key = `refresh:<userId>:<jti>`); allows single-device revocation.
- **Logout:** Adds access token JTI to Redis blocklist with TTL matching remaining token lifetime.
- **Rationale:** Stateless verification for access tokens with a revocation escape hatch via Redis.

### 4.2 File Upload Strategy
- Resident uploads maintenance photos via `POST /api/maintenance/{id}/photos` with `multipart/form-data`.
- Backend streams bytes to MinIO, stores resulting object key in `maintenance_photos.file_url`.
- Frontend fetches presigned GET URLs from backend (`GET /api/files/{objectKey}/presign`) with 1-hour expiry.
- Contract attachments follow the same pattern.
- **Max file size:** 10 MB per file, 5 files per maintenance request (configurable via env var).

### 4.3 Notification Delivery
- All notification triggers are synchronous within the request but fire-and-forget for external delivery (FCM/SMTP/SMS).
- A `notifications` table record is always created regardless of delivery channel success.
- External delivery failures are logged at WARN level and do not roll back the business transaction.
- SMS is a no-op by default — real provider injected via `@ConditionalOnProperty`.

### 4.4 SLA Tracking
- `maintenance_requests.sla_deadline` is computed at INSERT time as `created_at + category.sla_hours`.
- `SlaAlertScheduler` runs hourly, finds IN_PROGRESS/ASSIGNED requests where `sla_deadline < NOW()`, sends notifications to admin.
- Reporting endpoint exposes SLA breach rate grouped by category and month.

### 4.5 Amenity Booking Conflict Prevention
- Before inserting a booking, a SELECT FOR UPDATE on `amenity_bookings` checks overlapping approved bookings.
- Redis cache holds daily availability counts to reduce DB reads for the calendar view.
- Cache invalidated on any booking status change.

### 4.6 Audit Log Strategy
- An AOP `@Around` aspect intercepts all `@Service` methods annotated with `@Auditable`.
- Records entity type, entity ID, old/new values (serialized as JSONB), user ID from security context, IP from request context.
- Audit log is append-only — no update/delete operations on `audit_logs`.

### 4.7 RBAC Implementation
- Four roles: `ADMIN`, `TECHNICIAN`, `RESIDENT`, `BOARD_MEMBER`.
- Method-level security via `@PreAuthorize("hasRole('ADMIN')")` annotations.
- Residents can only access their own apartment's data (enforced in service layer, not just at controller).

### 4.8 Contractor Rating
- Contractor `rating` field is a computed average, recalculated on each maintenance request rating update.
- Stored as `NUMERIC(3,2)` — not recomputed on every read but updated via a DB trigger or service call.

### 4.9 Docker Compose Services

| Service | Image | Port |
|---------|-------|------|
| nginx | nginx:1.25-alpine | 80, 443 |
| api | gemek/backend:latest (local build) | 8080 |
| postgres | postgres:15-alpine | 5432 |
| redis | redis:7-alpine | 6379 |
| minio | minio/minio:latest | 9000, 9001 |

All inter-service communication uses Docker internal DNS names. No ports exposed to host except Nginx 80/443.

---

## 5. Security Considerations

- All secrets injected via environment variables — no hardcoded values in source.
- Input validation via Jakarta Bean Validation on all request DTOs (`@NotBlank`, `@Size`, `@Email`, etc.).
- Rate limiting via Redis: 20 requests/minute on auth endpoints; 100 requests/minute on general API.
- File upload: MIME type validation; filename sanitized before storage in MinIO.
- SQL injection: all queries via JPA/Hibernate with parameterized binding; no native string concatenation.
- CORS: configured to allow only the known frontend origin(s), not `*`.
- Passwords: BCrypt with strength 12.
- Sensitive data (password hash, phone) never logged.

---

## 6. Risks and Mitigations

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|------------|
| MinIO disk fills up (photos, PDFs) | Medium | High | Configure MinIO lifecycle policy to archive old objects; monitor disk usage |
| FCM token expiry for residents who reinstall app | Medium | Low | Token refresh on app startup; dead tokens cleaned periodically |
| SLA scheduler misses breaches during downtime | Low | Medium | Scheduler is idempotent; catches up on restart |
| Amenity double-booking under concurrent load | Low | High | SELECT FOR UPDATE pattern at DB level |
| Redis unavailable — auth blocklist inaccessible | Low | Medium | Fail-open with short token lifetime (15 min) limits exposure window |
| Flyway migration fails on startup | Low | High | Migration scripts tested in CI; V-versioned, never edited after deploy |
