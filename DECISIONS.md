# DECISIONS — Apartment Management System

Log of all autonomous decisions made by agents.
Format: Date | Decision | Reasoning | Alternatives

---

## 2026-06-09 | Cluster 1 form-feedback patch (5 forms)

**Decision:** Login (both apps): error → `getVnErrorMessage(code)`, success → navigate (no toast). Change-password / Book amenity / Rate ticket: success → `toast.success('Vietnamese message')`; error → `getVnErrorMessage(code)`. Inline English success removed from ProfilePage (was `pwSuccess` state + static string).

**Why:** Login navigate-on-success is sufficient UX signal; toast there would be redundant. The other 3 are non-navigation mutations where the user stays on the same page — a toast is the only clear success signal.

**Toast API (locked standard):** `toast.success(message: string)` / `toast.error(message: string)`. Do NOT call `toast(...)` directly — it is an object, not a function. All future clusters must use `.success()` / `.error()` form.

**Interceptor URL guard (locked standard):** Both `apiClient` interceptors skip the 401 refresh+retry for `/auth/login` and `/auth/refresh` — these return 401 as a business signal, not a token-expiry signal. Add any future auth-only endpoint to this guard if it returns 401 for business logic.

**WRONG_CURRENT_PASSWORD (422):** Wrong current password in change-password flow uses `HttpStatus.UNPROCESSABLE_ENTITY` (422) not `UNAUTHORIZED` (401). 422 bypasses the 401 interceptor → error reaches component immediately. `INVALID_CREDENTIALS` (401) is reserved for login only.

**skipSuccessToast pattern (SUPERSEDED — see 2026-06-09 toast-position fix):** The "component path unreliable" diagnosis was wrong — the actual bug was Tailwind purging Toast classes (resident tailwind.config missing packages/ui/src scan). Component-level `toast.success()` is reliable once tailwind scans the package. See canonical pattern below.

---

## 2026-06-09 | Change-password toast + password-policy error (round 2 fixes)

**Decision A — Success toast via meta.successMessage:** `useChangePassword` uses `meta: { successMessage: 'Đổi mật khẩu thành công.' }`. Still valid but reason was wrong — singleton is fine; this just avoids the component needing to import toast. Either path works.

**Decision B — PASSWORD_POLICY_VIOLATION (422) for weak new password:** `@Pattern` removed from `ChangePasswordRequest.newPassword` — Spring's `MethodArgumentNotValidException` maps to generic `VALIDATION_ERROR`. Domain validation (password complexity) moved to `AuthServiceImpl.changePassword()` → throws `PASSWORD_POLICY_VIOLATION` (422). FE maps to "Mật khẩu mới phải có tối thiểu 8 ký tự, gồm chữ hoa, chữ thường, số và ký tự đặc biệt." Pattern: any domain-specific validation that needs its own user-facing VN message must have its own `ErrorCode` enum entry; never rely on `VALIDATION_ERROR` for domain rules.

---

## 2026-06-09 | Toast positioning + canonical pattern (locked)

**Root cause:** Resident `tailwind.config.js` did not include `packages/ui/src` in content scan → all `Toast.tsx` classes purged in production build → toast renders as unstyled white block in document flow. Admin config already had the scan; only resident was missing it.

**Mobile fix (c518623 — partially wrong):** `Toast.tsx` replaced inline style with Tailwind classes. Tailwind purge was real and fixed. However `md:left-auto md:right-4` still anchored to viewport right edge.

**Viewport-anchor fix (c4b3179 — correct):** Root cause was `position:fixed` anchored to viewport right edge. Resident layout is `max-w-md mx-auto` (448px centered) — on wide desktop, `right-4` puts toast far outside the app column. Fix: `left-1/2 -translate-x-1/2 w-[calc(100%-2rem)] max-w-sm` — viewport-centered, always over the column. Prior `md:left-auto md:right-4` removed.

**Tailwind scan rule (locked):** Every app that imports `@gemek/ui` components MUST include `../../packages/ui/src/**/*.{ts,tsx}` in its `tailwind.config.js` content array. Failure = all UI-package classes purged.

**Toast singleton (confirmed):** ONE `listeners[]` instance. `packages/ui/src/components/Toast.tsx` resolves to the same absolute path from all import sites (App.tsx, component files, mutationToast.ts). No module duplication.

**Canonical success-toast pattern (locked for remaining clusters):**
- Component-level `toast.success('VN message')` — use when message depends on response data, or form has its own catch block already importing toast.
- `meta: { successMessage: 'VN message' }` — use for simple fixed messages where the hook can define it; cleaner if component doesn't need toast for other reasons.
- Both are equivalent. `skipSuccessToast: true` only needed when BOTH paths would fire (component calls toast AND meta.successMessage is set → two toasts).

---

## 2026-06-09 | Form-feedback standard + distinct dup-code finding

**Decision:** All forms in both apps follow one standard: validation/server errors → VN inline message below field; success → toast. No mixed patterns.

**Finding:** BE already emits `PHONE_ALREADY_EXISTS` vs `EMAIL_ALREADY_EXISTS` as distinct codes (confirmed in `ErrorCode.java`, `ResidentServiceImpl.java`, `UserServiceImpl.java`). No BE changes needed for dup-key distinction — only FE mapping is missing.

**Why:** FE was hardcoding "Email đã được sử dụng." for all 409 dup errors regardless of which field caused it. The BE already provides the correct signal; FE must surface it as per-field inline error.

**2026-06-09 addendum:** `ResidentServiceImpl` was throwing generic `CONFLICT` for email-dup (one-line fix, e66b86e). Root cause: service-layer email guard was added independently from the phone guard and used the wrong error code. Both paths now symmetric: phone-dup → `PHONE_ALREADY_EXISTS`, email-dup → `EMAIL_ALREADY_EXISTS`. Test updated (902ff48) — old test asserted `CONFLICT` (was testing the bug, not the intent).

**How to apply:** FE catch block reads `err?.response?.data?.error` (the BE error code string). Map known codes to fixed VN strings via a shared util; unknown codes and non-409 errors fall back to "Có lỗi xảy ra, vui lòng thử lại." — never echo `err?.response?.data?.message` (leaks raw server text, may expose phone/email, may be English). Set field-level error (`setPhoneError` / `setEmailError`) where the field state exists; otherwise set form-level `setFormError`.

---

## 2026-06-09 | Form-feedback foundation — shared getVnErrorMessage util

**Decision:** Single `getVnErrorMessage(errorCode?: string): string` pure function in `@gemek/ui/src/lib/errorMessages.ts`, exported from `@gemek/ui`. Both apps import this; no duplicated code in individual forms.

**Mapping:** All 16 BE ErrorCode values mapped to Vietnamese. Generic codes (CONFLICT, NOT_FOUND, VALIDATION_ERROR, UNAUTHORIZED, FORBIDDEN, INTERNAL_ERROR) get broad but correct VN sentences. Specific codes (PHONE_ALREADY_EXISTS, EMAIL_ALREADY_EXISTS, INVALID_CREDENTIALS, etc.) get precise VN sentences. Unknown/undefined → fallback "Có lỗi xảy ra, vui lòng thử lại."

**BE gap list:** 7 CONFLICT throw sites flagged in `reports/error-code-audit.md` where a more specific code would improve UX (highest priority: license-plate dup in VehicleServiceImpl — currently FE can only show generic CONFLICT message). These are NOT fixed now; logged for a future BE patch turn.

**Why:** Centralizing the mapping prevents drift (each form choosing different VN phrasing) and ensures that raw English server messages can never leak to the UI through this path.

**Alternatives:** Inline the mapping per form — rejected (maintenance burden, easy to forget fallback rule).

---

## 2026-06-08 | Dup-phone 500 fix

- Root cause: Docker container ran stale code (pre-step-5 ResidentServiceImpl had no existsByPhone guard); dup phone hit DB constraint → DataIntegrityViolationException → catch-all → 500.
- Fix 1: Added DataIntegrityViolationException → 409 CONFLICT handler in GlobalExceptionHandler (defense-in-depth for race conditions and future constraints).
- Fix 2: Rebuilt backend container to deploy step-5 phone guard in ResidentServiceImpl.
- Fix 3: ResidentsPage 409 inline message now uses `err?.response?.data?.message` (was hardcoded wrong "Email đã được sử dụng.").
- Note: `useCreateResident` has `skipErrorToast: true` — no global toast; errors surface inline via setFormError. Intentional for now; remove skipErrorToast if toast desired.

---

## 2026-06-08 | Demo seed script

- Data: 3 blocks, 10 apartments (4/3/3 per block), 30 residents (3 per apt: 1 OWNER + 2 TENANT), 5 staff (2 ADMIN + 3 TECHNICIAN).
- Password `Demo@1234`, BCrypt strength=12; hash embedded as literal (no `$` env-var interpolation).
- Phones: staff `0901100001–0901100005`, residents `0901200001–0901200030` — all canonical `^0[3-9]\d{8}$`, no collision with default admin `0900000000`.
- Amenities, amenity_bookings, parking intentionally excluded (features hidden/deferred).
- Script replaces prior `scripts/seed-demo-local.sql` (previous version had 30 apts + vehicles + contractors + tickets + bookings — too heavy).

---

## 2026-06-08 | Phone-as-login COMPLETE — canonical decisions

1. Login identifier = phone (was email). Email is informational only — NOT login, NOT required.
2. Canonical stored form: `0xxxxxxxxx` (leading 0, 10 digits, `^0[3-9]\d{8}$`; VN mobile only).
3. All input formats normalized on BE only via `PhoneUtils.normalize()` — FE does no normalization.
4. Email: UNIQUE constraint kept; NOT NULL dropped (nullable-unique; multiple NULLs allowed in Postgres).
5. DB reset locally acceptable — no data migration required (V12 migration handles schema).
6. `CreateResidentRequest.email`: @NotBlank removed (commit 4237cba — mixed into docs commit; flagged for awareness).

---

## 2026-06-08 | API-SPEC v2.1 aligned to phone-as-login as-built (step 8)

POST /api/auth/login: request phone (was email), response user.phone (was email), normalization note added. POST /api/users: phone required, email optional. POST /api/residents: phone required + normalized, dateOfBirth required, email optional; error codes updated (PHONE_ALREADY_EXISTS added). Also fixed CreateResidentRequest.email: removed @NotBlank (email is optional per V12 schema; @Email format validation kept).

---

## 2026-06-08 | Resident creation path: phone normalization + uniqueness (step 5)

`ResidentServiceImpl.createResident()` built User directly, bypassing `UserServiceImpl.createUser()`. Added `PhoneUtils.normalize()` + `existsByPhone` check (→ PHONE_ALREADY_EXISTS 409) before persist. Email null-guard added (email is now optional). Consistent with UserServiceImpl order: normalize → phone-unique → email-unique → persist. Dup-phone was previously a 500 DB constraint violation; now 409.

---

## 2026-06-08 | FE login switched from email to phone (step 6)

Both apps (admin + resident): `AuthUser.email→phone`, `login(email)→login(phone)`, POST body `{email}→{phone}`. `LoginPage.tsx` both apps: label "Số điện thoại", `type="tel"`, loose VN phone regex UX gate only — BE normalizes and validates definitively. `ProfilePage.tsx` (resident): `user?.email→user?.phone` (minimal build-fix, full audit step 7). `ResidentsPage.tsx` `r.user?.email` left as-is (typed `any`, no TS error; display audit step 7). Builds: admin ✅ resident ✅.

---

## 2026-06-05 | Stable id tie-breaker added to all paginated list sorts — makes ordering deterministic (was causing intermittent test failures + unstable pagination across page boundaries)

---

## 2026-06-05 | POST /api/residents — provisions new user + resident in one transaction; old assign-existing-user (userId) flow removed (breaking change)

---

## 2026-06-05 | users.date_of_birth — nullable DATE column via V11 migration
Added nullable `date_of_birth DATE` to users table (V11 migration). Exposed in UserResponse, UserDetailResponse, ResidentResponse.UserRef. No create/update flow yet — additive read-only this turn.

---

## 2026-06-05 | GET /api/residents — search param + apartment.block in response

**Decision:** Added optional `search` query param (Criteria API LIKE on user.fullName/email, same null-safe pattern as UserRepository fix). Added `apartment.block.name` to `ResidentResponse.ApartmentRef` and `ResidentMapper`. Fetch joins for user/apartment/block added to data query (not count) to avoid N+1.

**Why:** Admin vehicle form needs a resident dropdown with server-side search (>100 residents exceed client-side cap). Apartment block needed to derive `apartmentId` after resident selection. Backward-compatible — only adds fields and a new optional param.

---

## 2026-06-05 | SearchableSelect async server-search mode | loadOptions opt-in prop

**Decision:** Added optional `loadOptions?: (query: string) => Promise<SearchableOption[]>` to SearchableSelect. When provided, debounces 300ms calls to the function instead of filtering client-side. Selected label stored in state, persists when not in current search results. Static mode (no prop) unchanged; user dropdown on create-resident uses it.

**Why:** User list has 202 entries; backend max-page-size cap of 100 means client-side filtering can't surface 101–202. Server-search via `GET /api/users?search=<q>&size=20` reaches any user. Apartment and block dropdowns remain client-side (lists small enough).

**Alternatives considered:** Raise backend maxPageSize to 500 (risky, large payloads); `async` boolean flag (forces caller to wire fetch externally). Chosen approach keeps component self-contained.

---

## 2026-06-05 | UserRepository — replace @Query findAllWithFilters with JpaSpecificationExecutor

**Decision:** Removed the `@Query`-based `findAllWithFilters` from `UserRepository`; `UserRepository` now extends `JpaSpecificationExecutor<User>`. `UserServiceImpl.listUsers()` builds a `Specification<User>` programmatically using Criteria API, only adding the LIKE predicate when `search` is non-null.

**Why:** Hibernate 6 + PostgreSQL JDBC driver binds all `String` named parameters in JPQL `LOWER(CONCAT('%', :param, '%'))` expressions as `bytea` regardless of value (null or non-null). PostgreSQL resolves ALL parameter types at query-planning time, so IS NULL short-circuit cannot prevent the `lower(bytea)` error. Multiple JPQL workarounds attempted failed: `COALESCE(:search, '')` (still bytea), `COALESCE(:search, column)` (type conflict error), `cast(:search as string)` (caused `could not determine data type of $1` for the IS NULL check of an unrelated enum param). Specifications use the JPA Criteria API which binds `String` parameters as `varchar` via `cb.like(cb.lower(root.get("fullName")), pattern)`.

**Alternatives considered:** `cast(:search as string)` in JPQL (broken — shifts error to other params); `COALESCE(:search, column)` (broken in tests — `COALESCE types bytea and varchar cannot be matched`); native query (requires projection interface and separate count query, not worth the complexity). Specifications are the Spring-recommended approach for dynamic optional filters.

---

## 2026-06-04 | API-SPEC corrected to match as-built backend — amenity approve/reject and parking routes

Amenity: original spec defined two separate endpoints (`PUT /amenity-bookings/{id}/approve` with `{ notes }` and `PUT /amenity-bookings/{id}/reject` with `{ reason }`). The backend was implemented with a single unified endpoint (`PUT /amenity-bookings/{id}/approve`) accepting `{ status: BookingStatus, rejectionReason }`. The `/reject` endpoint was never built. API-SPEC updated to document the as-built contract. FE aligned to BE.

Parking: original spec defined `POST /parking/assignments` (create) and `PUT /parking/assignments/{id}/end` (unassign). The backend was implemented as `POST /parking/slots/{id}/assign` and `POST /parking/slots/{id}/unassign` (slot-centric routing; `id` in path is slot UUID in both cases). `endDate` on create not supported — assignments are open-ended. API-SPEC updated; FE hooks fixed to call real routes.

## 2026-06-04 | Announcement DTO field rename: `scope`→`targetScope` in CreateAnnouncementRequest, UpdateAnnouncementRequest, AnnouncementResponse — to match API-SPEC.md contract. UpdateAnnouncementRequest renamed in the same pass for consistency (spec uses `targetScope` for both create and update endpoints). Entity field `Announcement.scope` and DB column `target_scope` unchanged.

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

## Deployment Decisions

### 2026-06-03 | Admin portal port 80, resident portal port 81 (single nginx container)
**Decision:** One nginx container builds both React apps (multi-stage Dockerfile) and serves them on separate ports: admin on 80, resident on 81.
**Reasoning:** Both apps share the same API backend and nginx config; running one container avoids duplication. Different ports cleanly separate the two audiences without requiring different domains or subdirectory base URLs (no Vite `base` change needed).
**Alternatives:** Two separate nginx containers; nginx subdirectory routing (requires `base: '/admin/'` in vite configs). Both rejected to minimise config changes.

### 2026-06-03 | MinIO console port 9001 exposed on host for first-run bucket setup
**Decision:** `ports: ["9001:9001"]` in docker-compose.yml so operators can create the `gemek` bucket via the web console on first deploy.
**Reasoning:** No `minio/mc` init container to avoid pulling an extra image. Console access is sufficient for a single-instance local/staging deployment.
**Alternatives:** `minio-init` sidecar running `mc mb`. Deferred — adds image pull requirement.

---

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

### 2026-06-04 | Application-level admin seeding replaces Flyway hash placeholder
**Decision:** `AdminSeeder` (ApplicationRunner) creates the admin user on first boot by hashing a plaintext `ADMIN_PASSWORD` env var with BCrypt-12. V2 migration no longer contains an admin INSERT. `spring.flyway.placeholders.ADMIN_PASSWORD_HASH` removed from application.yml.
**Why:** Docker Compose interpolates `$` characters in env_file values. A BCrypt hash (`$2b$12$<salt>...`) contains `$` followed by valid variable-name characters; compose substitutes them with blank, truncating the hash to 27 chars. Flyway seeds the corrupted hash; login always fails. Plaintext env var has no `$` corruption risk.
**How to apply:** Set `ADMIN_EMAIL` and `ADMIN_PASSWORD` in `.env`. On first boot with an empty DB, AdminSeeder hashes the plaintext and inserts the admin. Idempotent — skips if any ADMIN role user exists. Fail-loud — throws `IllegalStateException` if no admin exists and `ADMIN_PASSWORD` is blank.
**Migration checksum note:** V2 was rewritten (INSERT removed). This is safe ONLY for environments that wipe and re-migrate from scratch (`docker compose down -v`). Any environment that has already applied the original V2 will fail Flyway checksum validation and must use a new migration (V11+) to correct the admin hash instead.

### 2026-06-04 | GET /api/amenity-bookings allows TECHNICIAN and BOARD_MEMBER (spec says ADMIN+RESIDENT only)
**Decision:** Kept TECHNICIAN and BOARD_MEMBER in the @PreAuthorize allowlist for GET /api/amenity-bookings even though API-SPEC.md lists only ADMIN and RESIDENT.
**Why:** These staff roles have an operational need to view bookings (approvals, facility scheduling). Narrowing them would be a breaking change with no security benefit — they already have ADMIN-level read access on other booking endpoints.
**How to apply:** Do not remove TECHNICIAN/BOARD_MEMBER without a deliberate product decision. The spec discrepancy is a documentation gap, not a security issue.

### 2026-06-04 | RESIDENT scoping for GET /api/amenity-bookings is server-side only (IDOR prevention)
**Decision:** When role=RESIDENT, the service ignores the client-supplied `residentId` param and forces-scopes results to the caller's own active resident record (looked up via `residentRepository.findActiveByUserId`).
**Why:** Trusting the client residentId would be an IDOR — a resident could pass another resident's UUID and see their bookings. Server-side derivation from the JWT principal is the only safe approach.
**How to apply:** Any future endpoint that lists user-owned resources must follow the same pattern: derive identity from the principal, never from a client-supplied ID.

### 2026-06-04 | GET /api/residents/me — identity from JWT principal only
**Decision:** Added self-scoped endpoint returning the caller's active resident record. No userId in path or query — the principal UUID is derived from the JWT via `@AuthenticationPrincipal UserPrincipal`. Returns 404 (NOT_FOUND) if user has no active residency.
**Why:** Resident apartment derivation via ticket[0].apartment broke for residents with zero tickets. A dedicated identity endpoint is reliable and eliminates the IDOR surface.
**How to apply:** Any endpoint returning user-owned resources must derive identity from the principal, never from a client-supplied ID.

## 2026-06-04 | GET /api/tickets `status` filter widened to multi-value repeated param
**Decision:** Changed `@RequestParam TicketStatus status` (single enum) to `@RequestParam List<TicketStatus> status` (repeated param). Query uses `IN` when list non-empty, no restriction otherwise. Added `MethodArgumentTypeMismatchException` handler in `GlobalExceptionHandler` returning 400 VALIDATION_ERROR.
**Why:** Resident home screen needs to show "open" tickets (NEW + ASSIGNED + IN_PROGRESS) in a single call. Sending a comma-joined string to a single-enum param caused `IllegalArgumentException` → 500. Repeated params (`?status=NEW&status=ASSIGNED`) bind natively via Spring's `List<Enum>` binding with clean 400 on bad input.
**Alternatives considered:** Comma-separated list with a custom `ConversionService` converter — rejected because repeated params are standard HTTP, require no custom code, and Spring's binding already handles them correctly.

## 2026-06-05 | GET /api/vehicles `search` param — Criteria API, OR on licensePlate/brand/model
**Decision:** Added `search` as case-insensitive substring OR across `licensePlate` (NOT NULL), `brand`, `model` (nullable — coalesced to "" before `lower()`). Criteria API only; no JPQL LIKE. Additive to existing `apartmentId` filter.
**Why:** ParkingPage vehicleId dropdown needs server-search to find vehicles without knowing exact UUID. Criteria API avoids Hibernate-6 null→bytea bug on nullable LIKE parameters. `brand`/`model` included so partial make/model strings resolve vehicles without knowing the plate.
**Alternatives considered:** JPQL with `(:s IS NULL OR LOWER(v.licensePlate) LIKE ...)` — rejected (Hibernate-6 bytea bug on null params, proven failure on this project).

## 2026-06-08 | Password complexity enforced on CreateResidentRequest (@Pattern from CreateUserRequest)
**Decision:** `CreateResidentRequest.password` gains the same `@Pattern` regex as `CreateUserRequest` — min 8 chars, upper+lower+digit+special. Commits: 697edfd (fix), c704a04 (test).
**Why:** POST /api/residents creates a new user; weak password accepted there while rejected on POST /api/users is an inconsistency and security gap.
**How to apply:** Any DTO that accepts a user password must carry the same `@Pattern` constraint.

---

## 2026-06-08 | Add-resident form: inline user creation + generate-password button; phone + dateOfBirth required
**Decision:** Admin add-resident form replaced the obsolete "select existing user" flow (SearchableSelect on users) with inline new-user creation fields. Generate-password button added. `dateOfBirth` field added. `phone` and `dateOfBirth` are now required on both the form (FE validation) and `CreateResidentRequest` (`@NotBlank`/`@NotNull`). Commits: 448bc15 (form), fa47149 (dob + validation), c1e3789 (BE required), 0061e4c (tests).
**Why:** POST /api/residents was changed (cc42a6c session) to provision a new user+resident transactionally — the old "assign existing user" UI was already broken. phone is now a login identifier (phone-as-login initiative); dateOfBirth is needed for resident profiles.
**How to apply:** Do not add an "assign existing user" path back without a deliberate product decision.

---

## 2026-06-08 | Global success-toast bug: Tailwind purging layout classes from @gemek/ui
**Decision:** Root cause of success toasts not appearing was Tailwind CSS content scanning missing `packages/ui/src`. Classes `top-4`, `right-4`, `z-[200]` were purged. Fixed by adding `'../../packages/ui/src/**/*.{ts,tsx}'` to `content` in both apps' `tailwind.config.js`. Commit: 3dc1d6b.
**Why:** TanStack MutationCache `onSuccess` was wiring correctly (d905b56); the toast component rendered but was positioned off-screen due to purged positioning classes.
**How to apply:** Any new shared component in `packages/ui/src` with Tailwind classes is automatically scanned after this fix. If a new package is added under `packages/`, add it to both tailwind configs.

---

## 2026-06-08 | JAVA_HOME prerequisite documented in NEW-SESSION.md
**Decision:** Documented that running Maven tests requires `JAVA_HOME` set to JDK 21 (Eclipse Adoptium path on this machine) and Maven path to IntelliJ's bundled Maven. Commit: 2d49936.
**Why:** `mvn` is not in PATH; IntelliJ's bundled Maven at `C:\Program Files\JetBrains\IntelliJ IDEA 2026.1\plugins\maven\lib\maven3\bin\mvn.cmd` must be invoked directly with `$env:JAVA_HOME` set.
**How to apply:** See `NEW-SESSION.md` for the exact PowerShell snippet before running any Maven commands.

---

## 2026-06-08 | PHONE-AS-LOGIN — CTO decisions (IN PROGRESS)
**Decision:** Login identifier switches from email to phone number. Full implementation plan in `reports/phone-username-survey.md` section D.

CTO decisions (fixed requirements):
1. Login identifier = phone number; email is informational only, NOT used for login, NOT required.
2. Canonical stored form: `0xxxxxxxxx` (leading 0, 10 digits, VN mobile `^0[3-9]\d{8}$`; landlines rejected).
3. Accepted input formats: `0962464748`, `+84962464748`, `+840962464748`, `84962464748`, `840962464748` — all normalize to `0962464748`. Strip `+84`/`84`, ensure leading `0`. `+840...`/`840...` (redundant 0 after country code) handled: after stripping `84`, remainder already starts with `0` — do not double.
4. Normalization MUST happen on backend only via `PhoneUtils.normalize()` (single shared util). FE may validate for UX only.
5. DB reset locally acceptable — no data migration required.
6. email: keep `UNIQUE` constraint but drop `NOT NULL` (nullable-unique allows multiple NULLs in Postgres).

Steps done: step 1 PhoneUtils + tests (4b3f020), step 2 V12 migration (41b90ca). Steps 3–9 remaining — see PROGRESS.md.

**WARNING:** Do NOT boot the app until step 3 (entity) + step 4 (AdminSeeder) both land. V12 makes `phone NOT NULL`; old AdminSeeder still hardcodes phone — it would fail or insert un-normalized value.

---

## 2026-06-08 | Phone-as-login steps 3–4: login identity now phone-keyed end-to-end in BE
**Decision:** Steps 3 (auth/user module) and 4 (AdminSeeder) complete. Login identifier is now phone throughout the backend.

Step 3 changes (3e59bbc feat + 1ccce1b test):
- `LoginRequest`: `email` → `phone` (`@NotBlank`; real validation via `PhoneUtils.normalize()` in service).
- `AuthServiceImpl.login()`: normalizes phone via `PhoneUtils.normalize()` → `findByPhone(normalized)`.
- `UserPrincipal`: `phone` field; `getUsername()` returns phone; `SecurityConfig.UserDetailsService` updated to `findByPhone`.
- `JwtTokenProvider`: `CLAIM_EMAIL` → `CLAIM_PHONE`; embed `principal.getPhone()` in access token (informational claim; filter still uses `sub` UUID).
- `LoginResponse.UserSummary`: `email` → `phone` field.
- `CreateUserRequest`: `phone` now `@NotBlank` (required); `email` now optional (nullable, `@Email` format-check if provided).
- `UserServiceImpl.createUser()`: `existsByPhone` as primary uniqueness guard (409 → `PHONE_ALREADY_EXISTS`); optional `existsByEmail` guard if email provided; `PhoneUtils.normalize()` before persist.
- `ErrorCode`: added `PHONE_ALREADY_EXISTS(HttpStatus.CONFLICT)`.
- `User` entity: `email` `nullable=true`; `phone` `nullable=false, unique=true` (matches V12 migration).

Step 4 changes (e1e2d14 feat + bb4fe47 test):
- `AdminSeeder`: `adminPhone` injected via `${app.admin.phone:0900000000}`; `PhoneUtils.normalize()` applied before `admin.setPhone()`. Non-canonical env values (e.g. `+84900000000`) are silently corrected; invalid values fail loud at startup.

**Why:** Centralize normalization at PhoneUtils; every write path normalizes before DB insert or lookup, ensuring the `uq_users_phone` UNIQUE constraint (V12) is satisfied regardless of input format.
**How to apply:** Any future endpoint that writes a phone field must call `PhoneUtils.normalize()` before persistence. The `ResidentServiceImpl` (step 5) is next.

---

## CTO Overrides
_(record when CTO overrides agent decision)_

### 2026-06-03 | SEC-20 remains deferred post-security-audit
**Decision:** SEC-20 (INFO — refresh token in localStorage) stays deferred after the improvement/security audit.
**Reasoning:** Full remediation requires a backend Set-Cookie change on /auth/login and /auth/refresh, which is a post-G4 hardening sprint item. The prior architectural decision (2026-05-29) to store the refresh token in localStorage was made knowingly, with HttpOnly cookie migration tracked explicitly.
**How to apply:** Do not attempt SEC-20 remediation until the CTO schedules the hardening sprint. The open-items section of G4-testing.html documents the required changes.
- GET /api/blocks now returns PageResponse (data/page/size/total/totalPages); search via Criteria API (case-insensitive name substring); default size=10, sort=name asc; max size=200. FE callers already read .data — no FE changes.
