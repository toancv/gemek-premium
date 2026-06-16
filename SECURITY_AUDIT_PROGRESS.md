# Security Audit Progress — improvement/security branch

Verified against actual source code on 2026-06-02.
Each finding checked by reading the file at the stated location — not from chat history.

| ID     | Severity | Status    | Location                                                                 | Note                                                                  |
|--------|----------|-----------|--------------------------------------------------------------------------|-----------------------------------------------------------------------|
| SEC-01 | HIGH     | FIXED     | FileController.java:66                                                   | `ticketService.assertPresignAccess()` enforces ownership before presign |
| SEC-02 | MEDIUM   | FIXED     | AmenityController.java:241                                               | `@PreAuthorize("hasRole('RESIDENT')")` — ADMIN removed                |
| SEC-03 | HIGH     | FIXED     | TicketServiceImpl.java:190                                               | `includePhone` boolean strips phone for TECHNICIAN and BOARD_MEMBER   |
| SEC-04 | HIGH     | FIXED     | UserServiceImpl.java:121                                                 | `log.warn` on role change with actorId from SecurityContextHolder     |
| SEC-05 | HIGH     | FIXED     | AuthServiceImpl.java:158                                                 | Redis counter rate-limits both login and refresh by IP                |
| SEC-06 | HIGH     | FIXED     | JwtAuthenticationFilter.java:134                                         | Role read from DB entity (`user.getRole()`) not from JWT claim        |
| SEC-07 | MEDIUM   | FIXED     | NotificationServiceImpl.java:97                                          | `markAsRead` query scoped to both notificationId AND userId; NOT_FOUND if mismatch |
| SEC-08 | MEDIUM   | FIXED     | TicketServiceImpl.java:190                                               | Same fix as SEC-03 — `!"BOARD_MEMBER".equals(role)` in includePhone guard |
| SEC-09 | MEDIUM   | FIXED     | application.yml:48                                                       | `password: ${REDIS_PASSWORD}` — empty-string fallback removed         |
| SEC-10 | MEDIUM   | FIXED     | db/migration/V2__seed_admin.sql:18                                       | Hash replaced with `${ADMIN_PASSWORD_HASH}` Flyway placeholder; resolved from env var via `spring.flyway.placeholders` |
| SEC-11 | MEDIUM   | FIXED     | AmenityServiceImpl.java:331                                              | `durationMinutes < MIN_BOOKING_DURATION_MINUTES` (30 min) guard present |
| SEC-12 | MEDIUM   | FIXED     | AmenityServiceImpl.java:311                                              | `isAfter(today.plusDays(MAX_ADVANCE_DAYS))` (14 days) guard present   |
| SEC-13 | MEDIUM   | FIXED     | AuthServiceImpl.java:205                                                 | `if (accessToken == null or isBlank())` null guard in logout          |
| SEC-14 | LOW      | FIXED     | AuthServiceImpl.java:226                                                 | `redisTemplate.scan(...)` used — no KEYS command present              |
| SEC-15 | LOW      | FIXED     | pom.xml:15                                                               | `spring-boot-starter-parent` bumped 3.3.5 → 3.3.8                   |
| SEC-16 | LOW      | FIXED     | pom.xml:29                                                               | `jjwt.version` bumped 0.12.3 → 0.12.6                               |
| SEC-17 | LOW      | FIXED     | SecurityConfig.java:99                                                   | Only /actuator/health permitted; /actuator/info not in permit list    |
| SEC-20 | MEDIUM   | FIXED     | AuthController.java + both FE authStores                                 | ≡ F-04 (one item; MEDIUM governs — INFO/MEDIUM split reconciled). Refresh token moved out of localStorage into an httpOnly, SameSite=Strict cookie (H3 BE + H4 FE); body dual-mode channel removed at close-out (cookie-only). |
| SEC-21 | LOW      | FIXED     | SecurityConfig.java (corsConfigurationSource)                            | Explicit CorsConfigurationSource bean + .cors(...) in filter chain    |
| SEC-22 | MEDIUM   | FIXED     | AmenityServiceImpl.java:308                                              | `isBefore(today)` past-date rejection guard present                   |

## Summary

| Status    | Count |
|-----------|-------|
| FIXED     | 20    |
| NOT-FIXED | 0     |
| **Total** | **20** |

## NOT-FIXED items remaining

- None. All 20 findings resolved.

## Hardening-sprint reconciliation (2026-06-16)

The hardening sprint (`reports/hardening-design.md`) tracked two findings under its own F-numbering that map onto this SEC list:

- **F-04 ≡ SEC-20** — the SAME finding (refresh token in localStorage), double-logged at different severities (F-04 MEDIUM, SEC-20 INFO). Unified as ONE item; the MEDIUM severity governs. **FIXED** via the httpOnly refresh cookie: H3 (BE dual-mode cookie), H4 (FE both apps cookie-based), close-out (body-param channel removed — refresh is cookie-only). See DECISIONS.md "Hardening sprint rulings" + the H3/H4/close-out entries.
- **F-05** — IDOR on file presign — **RESOLVED** in H1 (presign access enforced via `enforcePhotoAccess`, expiry 1h→10min) and H2 (prefix-routing). Reflected in `reports/security-remediation.html`.
