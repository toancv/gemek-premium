# (e) BE — Self-Service Profile Update Endpoint

**Date:** 2026-06-18 · **Branch:** deploy/local · **Phase:** (e) BACKEND (TDD)
**Endpoint added:** `PUT /api/auth/me/profile` — authenticated (any role), principal-derived identity.
**Investigation it closes:** `reports/e-self-profile-investigation.md` (§B — the one missing gap: self profile-update of fullName/phone/email).

---

## What was added

| File | Change |
|---|---|
| `module/auth/dto/UpdateOwnProfileRequest.java` | NEW record — ONLY `fullName`, `phone`, `email`. No `role`/`isActive`/`password`/`id`. Validation mirrors `CreateUserRequest`: `@NotBlank @Size(255)` fullName, `@NotBlank @Size(20)` phone, `@Email @Size(255)` email. |
| `module/auth/AuthService.java` | Interface method `updateOwnProfile(UserPrincipal, UpdateOwnProfileRequest) : UserDetailResponse`. |
| `module/auth/AuthServiceImpl.java` | Impl — `findById(principal.getId())`, `PhoneUtils.normalize`, phone+email uniqueness pre-checks **excluding the caller's own row**, mutates ONLY fullName/phone/email, returns `userMapper.toUserDetailResponse`. |
| `module/auth/AuthController.java` | `@PutMapping("/me/profile")` — no `@PreAuthorize` (authenticated-only via `anyRequest().authenticated()`), `@AuthenticationPrincipal`, `@Valid`. Returns 200 + updated profile. |
| `docs/API-SPEC.md` | Endpoint spec added between `/me/password` and `/me/fcm-token`. |

## Security boundary (held)

- **Identity is server-derived** from `principal.getId()` (same as `getMe` / `changePassword`). The client supplies NO id → no IDOR surface.
- **role / isActive / password are immutable on this path.** The DTO is a record with only the three allowed components, so a crafted JSON carrying `role`/`isActive`/`password` has those keys ignored at bind time (Jackson record binding). The service never reads or sets them.
- **Phone/email uniqueness excludes self:** `!normalized.equals(user.getPhone()) && existsByPhone(...)` → `PHONE_ALREADY_EXISTS`; symmetric for email → `EMAIL_ALREADY_EXISTS`. Submitting the unchanged value is a no-op, not a conflict.
- **Token survives the change:** token subject = user UUID (`JwtTokenProvider`), so a phone/email change does not invalidate the access token — confirmed by smoke (same token used for `GET /me` after the change).

---

## Tests (TDD — written before impl)

`backend/src/test/java/vn/vtit/gemek/integration/SelfProfileUpdateIntegrationTest.java` — 8 tests, full Spring HTTP stack (DispatcherServlet + Security filter chain + real Testcontainers Postgres), each on its own freshly-created users:

1. happy path → update persists; `GET /api/auth/me` reflects it.
2. IDOR/identity → only the caller's row changes; a second user is byte-for-byte untouched.
3. **privilege-escalation guard** → body with `role=ADMIN` + `isActive=false` + `id` + `password` → role stays `TECHNICIAN`, active stays `true` (asserted in the response AND in the persisted row).
4. phone uniqueness → collide with another user's phone → `PHONE_ALREADY_EXISTS` (409).
5. phone self-exclusion → set phone to caller's OWN current value → 200 success (no false conflict).
6. email uniqueness → collide with another user's email → `EMAIL_ALREADY_EXISTS` (409).
7. malformed phone → 400 `VALIDATION_ERROR`.
8. malformed email → 400 `VALIDATION_ERROR`.

### Verification
- New class: **8/8 green** (`mvnw.cmd -Dtest=SelfProfileUpdateIntegrationTest test`).
- Full backend suite: **327/327 green, BUILD SUCCESS** (`mvnw.cmd test`).

---

## HTTP smoke (over the wire, port 80 via nginx → backend rebuilt with new code)

Raw: `reports/e-be-profile-smoke.raw.txt`. Smoke user `0981754701` / role `TECHNICIAN`.

| Step | Request | Result |
|---|---|---|
| Happy update | `PUT /api/auth/me/profile {fullName:"Smoke Renamed", phone:same, email:new}` | `200` — fullName + email updated, role `TECHNICIAN`, isActive `true` |
| **Escalation attempt** | `PUT … {…, role:"ADMIN", isActive:false, password:"Hacked@…"}` | `200` — **role still `TECHNICIAN`, isActive still `true`** → escalation rejected at HTTP layer |
| Token survival | `GET /api/auth/me` with the **same token** after the change | `200` — reflects updated fullName/email; session not invalidated |

Escalation guard proven over the real wire: the crafted `role=ADMIN`/`isActive=false` body changed neither field.

---

## Status

**(e) BE done.** FE profile page (admin portal `/profile`, all-roles, wiring read + change-password + this new endpoint) is the **next** phase — NOT started. Awaiting CTO go.
