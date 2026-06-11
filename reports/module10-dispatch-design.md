# Module 10 — Announcement → Notification Dispatch: Design Proposal

**Date:** 2026-06-11
**Status:** PROPOSAL ONLY — every architectural choice below is **PENDING CTO APPROVAL**. No code changed.
**Verified against:** HEAD `84b780b` (deploy/local). Prior trace `reports/publish-notification-trace.md` (2026-06-10) re-verified — all claims still hold; line refs updated below where they drifted.

---

## A. Verified current state (from code, this session)

| Claim | Verified | Evidence |
|---|---|---|
| `publishAnnouncement()` is a stub — sets `publishedAt`, saves, logs INFO, creates **zero** notification rows | ✅ | `AnnouncementServiceImpl.publishAnnouncement()` lines 257–279; stub log at 273–276 |
| `NotificationService` NOT injected into `AnnouncementServiceImpl` | ✅ | Constructor lines 66–76 injects only AnnouncementRepository, AnnouncementReadRepository, BlockRepository, UserRepository, ResidentRepository |
| `NotificationServiceImpl.createNotification()` fully implemented (per-recipient: loads User by id, builds row, saves) | ✅ | `NotificationServiceImpl` lines 60–78. Signature: `(UUID userId, String title, String body, NotificationType type, UUID referenceId, String referenceType)` |
| `NotificationType.ANNOUNCEMENT_PUBLISHED` already exists | ✅ | `NotificationType.java` line 37 |
| `GET /api/notifications` returns `PageResponse` with **no** `unreadCount` field | ✅ | `NotificationController.getMyNotifications()` lines 74–89 → `PageResponse.of(...)`. **API-SPEC.md (~line 2001) says the response includes `unreadCount: 3` — spec/code divergence.** |
| `GET /api/notifications/unread-count` exists and works | ✅ | `NotificationController` lines 143–150 → `UnreadCountResponse{unreadCount}` |
| FE never calls unread-count; bell badge reads `notifData?.unreadCount` = always undefined → badge never shows | ✅ | resident `Layout.tsx:12,45–46`; resident `hooks.ts:91–92` (`get('/notifications',{size:20})`); same pattern in admin `Layout.tsx:27,61` + admin `hooks.ts:230–231`. No `useUnreadCount()` hook in either app |
| Resident `AnnouncementsPage` renders title only, no `content`, no detail route | ✅ | re-confirmed; `markRead.mutate(a.id)` on click, no navigation/expand |
| `AnnouncementResponse` has aggregate `readByCount`, no per-user `isRead` | ✅ | `toResponse()` lines 393–430; `readByCount` from `countByAnnouncementId` at 412 |
| `publishNow` in `POST /api/announcements` | ⚠️ **BE does NOT implement it.** `CreateAnnouncementRequest` has no `publishNow` field; create always saves draft (line 166 comment). Admin FE compensates: creates, then calls `POST /{id}/publish` as a second request (`AnnouncementsPage.tsx:30,48`). Net effect: **`publishAnnouncement()` is the single publish entry point** — dispatch wired there covers both flows. API-SPEC "side effects when publishNow:true" (~line 1755) describes FE-composed behavior, not a BE code path |

**New latent bug found (not in trace):** both FE apps' `useMarkAllRead` call `PUT /notifications/read-all` (resident `hooks.ts:97`, admin `hooks.ts:236`), but BE is `@PostMapping("/read-all")` (`NotificationController:124`) → **405 Method Not Allowed**. Never surfaced because the notification list is always empty today. Same family: spec says `PUT /notifications/{id}/read → 204`, code is `POST /{id}/read → 200`. Must be fixed in the same sprint or the bell's "mark all read" breaks the moment rows exist. (Direction — change FE to POST or BE to PUT — PENDING CTO APPROVAL; recommendation: FE→POST, smaller diff, no BE contract change.)

**Infrastructure facts relevant to design:**
- `spring.threads.virtual.enabled` is on (`application.yml:9–10`) — virtual threads available.
- NO `hibernate.jdbc.batch_size` configured → today every `save()` is an individual INSERT round-trip.
- `Notification` entity: `@GeneratedValue(strategy = GenerationType.UUID)` → ID generated app-side (batch-friendly). No unique constraint on (user_id, reference_id, type) — nothing in the DB prevents duplicate dispatch rows.

---

## B. Audience resolution — RESIDENTS only, reusing the feed rule

**Audience definition (proposed, PENDING CTO APPROVAL):** recipients = the **user accounts of active residents** matching announcement scope. Admin/staff (ADMIN, TECHNICIAN, BOARD_MEMBER) excluded — they see all announcements in the admin list anyway.

**The existing rule** (`AnnouncementRepository.findPublishedForApartment`, lines 47–54) — announcement is visible to apartment (blockId, floor) iff:

```
scope = ALL
OR (scope = BLOCK AND targetBlock.id = blockId)
OR (scope = FLOOR AND targetBlock.id = blockId AND targetFloor = floor)
```

…and the resident-side join is `ResidentRepository.findActiveByUserId` (moveOutDate IS NULL) → apartment → block/floor (`AnnouncementServiceImpl.listAnnouncements` lines 95–110).

**Why literal query reuse is impossible:** the feed query is the *inverse direction* (apartment → announcements) of dispatch (announcement → users). Same predicate, opposite free variable. One JPQL cannot serve both.

**Proposed approach — mirror the predicate in ONE new query + a consistency contract test:**

New method on `ResidentRepository` (no second resolution "path" — one query whose WHERE clause is the textual mirror of the feed predicate):

```sql
SELECT DISTINCT r.user.id
FROM Resident r
JOIN r.apartment a
JOIN r.user u
WHERE r.moveOutDate IS NULL
  AND u.active = true
  AND (
       :scope = 'ALL'
    OR (:scope = 'BLOCK' AND a.block.id = :blockId)
    OR (:scope = 'FLOOR' AND a.block.id = :blockId AND a.floor = :floor)
  )
```

**Divergence guard (the real protection):** a dedicated test class asserting the two queries are inverse-consistent — for each scope (ALL/BLOCK/FLOOR) and a fixture of residents across 2 blocks × 2 floors: *user ∈ recipient set ⟺ the announcement appears in that user's `findPublishedForApartment` feed*. Any future edit to either predicate that breaks symmetry fails this test. This is stronger than code-sharing tricks (e.g. a shared `Specification`) and far simpler.

**Edge cases:**

| Case | Behavior under proposed query | Note |
|---|---|---|
| Inactive resident (moveOutDate ≠ null) | excluded | matches feed: `findActiveByUserId` also filters moveOutDate |
| Deactivated user account (`is_active = false`) | excluded | feed query does NOT check `u.active` — but deactivated users cannot log in (`User.java:75–77`), so they can never see the feed either; no observable divergence. Including the filter avoids dead rows |
| Resident user with no apartment | impossible as modeled — `Resident` requires apartment; a USER with role RESIDENT but no Resident row simply isn't in the `residents` table → excluded. Matches feed (empty page, lines 98–101) |
| FLOOR scope | requires blockId+floor — both guaranteed non-null by `validateScopeConstraints` (lines 373–385) before any publish | |
| Same user active in 2 apartments | `DISTINCT` → one notification row. [TODO: kiểm tra] whether schema/business rules actually permit multiple active residencies per user — `findActiveByUserId` returns `Optional`, implying single, but no DB unique constraint was verified on (user_id, move_out_date IS NULL) |

**Volume estimate @ ~1000 apartments:** occupancy ~80–90%, 1–2 account-holding residents per occupied apartment → **ALL scope ≈ 1,000–2,000 rows** per publish. BLOCK ≈ 1/N of that; FLOOR ≈ tens. Sizing in §C assumes worst case 2,000.

---

## C. Transaction model — PENDING CTO APPROVAL

**First, the governing decision:** DECISIONS.md 2026-05-29 "Notification delivery: fire-and-forget" says — *"FCM/SMTP/SMS failures logged at WARN, do not roll back business transaction. **In-app record always created.**"* Read strictly: fire-and-forget governs **external channels only**; the in-app DB row is explicitly carved out as "always created". That decision therefore **supports** putting the in-app insert inside (or atomically tied to) the publish transaction — it does NOT license dropping in-app rows on failure.

### Option 1 — insert rows INSIDE the publish transaction (RECOMMENDED)

`publishAnnouncement()` (already `@Transactional`): set `publishedAt` → resolve recipient IDs (1 SELECT) → bulk-insert notification rows → commit. All-or-nothing.

- ✅ Atomic: a publish either fully happened (visible in feed AND rows exist) or didn't. No "published but nobody notified" state. Honors "in-app record always created".
- ✅ Idempotency trivially composable with the §D guard — retry after failure re-runs the whole thing.
- ✅ Deterministic tests — no async machinery (§F).
- ⚠️ Publish latency grows with audience. Mitigation below.
- ⚠️ A notification-insert failure rolls back the publish itself. At this scale that is a feature (operator retries), not a risk — the insert is a plain INSERT into a small table; realistic failure = DB down, which fails publish anyway.

**1000–2000-row insert inside one TX — how it behaves:** today (`createNotification` reused in a loop) it would be 2,000 × (`SELECT user` + `INSERT`) = ~4,000 round-trips, several seconds. **Do NOT loop `createNotification()`.** Proposed instead, in order of preference (choice PENDING CTO APPROVAL):
1. **Single `INSERT INTO notifications (...) SELECT ... FROM residents r JOIN ...`** native `@Modifying` query — one statement, recipients resolved and inserted in the DB, no entity hydration, fastest (sub-100ms for 2k rows). The recipient predicate lives in this one query (§B mirror + contract test still applies).
2. `saveAll()` with `hibernate.jdbc.batch_size=50` + `order_inserts=true` added to config — ~40 batched round-trips. Requires constructing 2k entities + 2k `User` references (`getReferenceById`, no SELECTs). Acceptable fallback if native SQL is unwanted.

Recommendation: **variant 1 (INSERT…SELECT)**. It also makes the dispatch row-count returnable (`int` from `@Modifying`) for logging/read-stats.

### Option 2 — dispatch AFTER commit (async / virtual thread)

Publish TX commits; `TransactionSynchronization.afterCommit` (or `@TransactionalEventListener(AFTER_COMMIT)`) hands recipient insertion to a virtual thread.

- ✅ Publish latency constant regardless of audience.
- ✅ Pattern scales to the *real* Module 10 endgame: external FCM/SMTP/SMS dispatch (which MUST be after-commit + fire-and-forget per DECISIONS.md).
- ❌ Window where announcement is published but rows don't exist; a crash in the window loses notifications **silently and permanently** (no outbox — DECISIONS.md explicitly rejected outbox as overkill, so there is no recovery mechanism).
- ❌ Violates the strict reading of "in-app record always created".
- ❌ Async tests need synchronous-executor injection or awaitility — more machinery (§F).

**Recommendation (PENDING CTO APPROVAL):** **Option 1 for the in-app row** (it is part of the business state, like `AnnouncementRead`), **Option 2 reserved for external channels** when push/email/SMS are actually implemented (separate future sprint — NOT in scope here; the INFO stub log for external channels stays). This keeps both DECISIONS.md entries satisfied: in-app row atomic with publish; external delivery fire-and-forget. Virtual-threads rationale (DECISIONS.md line 236) applies to the external I/O phase, not the single-statement DB insert.

---

## D. Idempotency / republish — spec divergence found, gap to close

**Spec:** `POST /api/announcements/{id}/publish` → `409 CONFLICT (announcement already published)` (API-SPEC.md ~line 1788).

**Code:** `publishAnnouncement()` lines 264–268 — already-published is **idempotent-return** (returns the announcement, no error, no re-save). Comment says "Idempotent: already published announcements are returned without error."

So the guard EXISTS (no path re-sets `publishedAt` or could double-dispatch through this method), but the **contract diverges from spec**. With dispatch added, the early-return must stay *above* the dispatch call — then double-click/retry cannot duplicate rows regardless of which contract is chosen:

- **D-1 (align to spec):** change early-return to `throw new AppException(ErrorCode.CONFLICT, ...)` → 409. Admin FE: `usePublishAnnouncement` error surfaces via `publishError` — verify VN message mapping. Breaking change for the current FE flow? FE only shows Publish button for drafts (`AnnouncementsPage.tsx:102`), so a 409 only appears on true double-submit — acceptable.
- **D-2 (keep code as-is):** idempotent 200, document spec change. Safer for FE retry logic, but spec edit needed.

**Recommendation: D-1** (409) — consistent with PUT/DELETE on published announcements (both already throw), and spec stays authoritative. **PENDING CTO APPROVAL.**

**DB-level note:** `notifications` has NO unique constraint, so the state guard is the *only* duplicate protection. Two concurrent publish requests on the same draft could theoretically both pass the `publishedAt == null` check (read-then-write race). Risk: near-zero (single admin UI, button disabled while pending). Optional hardening: `UPDATE announcements SET published_at = now() WHERE id = :id AND published_at IS NULL` returning row-count as the gate (atomic compare-and-set), dispatch only if count = 1. Cheap; recommend including. [PENDING CTO APPROVAL]

---

## E. The 3 secondary breaks (+1 new) — scoping

| # | Break | Fix | Effort | Sprint scope? |
|---|---|---|---|---|
| E-1 | Bell unread badge dead | Add `useUnreadCount()` hook → `GET /notifications/unread-count`, use in both Layouts (`unreadCount` from `UnreadCountResponse`); invalidate on mark-read/read-all. BE untouched | FE ~30min + verify | **IN** — dispatch is invisible without it |
| E-2 | Announcement body not rendered (resident) | Minimal: expand-on-click rendering `a.content` in the card (no new route). Alt: detail route `/announcements/:id` (already-built BE `GET /{id}`) — bigger, prettier | Minimal ~30min; route ~1.5h | **IN (minimal variant)** — notification says "new announcement" but resident can't read it = broken loop. Route variant separable |
| E-3 | Per-user `isRead` missing from `AnnouncementResponse` | Add `isRead` to DTO; compute via `AnnouncementReadRepository.existsByAnnouncementIdAndUserId(id, principalId)` — [TODO: kiểm tra] exact method name exists, `findByAnnouncementIdAndUserId` confirmed at `AnnouncementServiceImpl:302`, exists-variant may need adding. N+1 caution: list page = 20 exists-checks per page; acceptable at this scale, or batch with one `IN` query | BE ~1h incl. tests | **SEPARABLE** — cosmetic (unread highlight always-on). Recommend IN if sprint budget allows, else defer |
| E-4 (new) | `useMarkAllRead` PUT vs BE POST → 405 (both apps); also spec PUT vs code POST on `/{id}/read` | FE: `put(` → `post(` (2 one-line changes) — or BE switch to PUT to match spec. Recommend FE→POST | ~15min | **IN** — becomes user-visible the moment rows exist |

---

## F. Recommended task breakdown (each independently committable + testable)

> Assumes §C Option 1 + §D D-1 approved. Adjust mechanically if CTO picks otherwise.

| P | Task | Test strategy (deterministic) |
|---|---|---|
| P1 | BE: recipient resolution — new `ResidentRepository` recipient query (or the INSERT…SELECT in P2 directly) + **feed↔dispatch consistency contract test** (§B): fixtures 2 blocks × 2 floors × active/inactive residents/users; assert recipient set ⟺ feed visibility for all 3 scopes | Pure JPA integration test (Testcontainers PG, same as existing repo tests). No async |
| P2 | BE: wire dispatch into `publishAnnouncement()` — atomic CAS publish (§D), INSERT…SELECT dispatch, `ANNOUNCEMENT_PUBLISHED` type, `referenceId=announcement.id`, `referenceType="Announcement"`, title=announcement title, body=content (truncate? [TODO: kiểm tra] — `Notification.title` is varchar(255), body TEXT; announcement title is also 255 → safe; body fits TEXT) | Synchronous — publish in test TX, then assert: exact row count per scope, row fields, `countUnreadByUserId` increments. Double-publish test → 409 + row count unchanged. **Zero timing dependence because everything is in-transaction** |
| P3 | BE: publish contract change to 409 (`ErrorCode.CONFLICT`) + spec already matches; update any test asserting idempotent-200 | MockMvc test: publish twice → 200 then 409 |
| P4 | FE: E-4 PUT→POST fix (both apps) + E-1 `useUnreadCount()` + bell badge wiring + invalidation | Existing FE test setup; build + tsc; behavior = CTO browser-verify |
| P5 | FE: E-2 body render (minimal expand variant) | tsc/build; CTO browser-verify |
| P6 | BE+FE: E-3 per-user `isRead` (if approved in-scope) | BE: MockMvc asserts isRead flips after mark-read. FE: border logic uses real field |
| P7 | docs(context): PROGRESS/DECISIONS update + API-SPEC corrections (unreadCount page-field removed in favor of unread-count endpoint, or implemented — per CTO §G-4) | n/a |

Sequence rationale: P1–P3 are BE-only and land before any FE work; each leaves the suite green. External push/email/SMS dispatch stays stubbed (out of scope, future sprint).

**If CTO instead picks Option 2 (async):** P2 changes to `@TransactionalEventListener(AFTER_COMMIT)` + injected `Executor`; tests inject a synchronous (`Runnable::run`) executor so asserts stay deterministic — still no sleeps/awaitility. Flagging now so the test strategy is pre-agreed either way.

---

## G. Open questions for CTO

1. **Transaction boundary (§C):** approve Option 1 (in-app rows inside publish TX, INSERT…SELECT) with Option 2 reserved for future external channels? Or mandate async now?
2. **Publish contract (§D):** switch already-published → 409 per spec (D-1), or keep idempotent-200 and amend spec (D-2)?
3. **Atomic CAS publish guard (§D):** include the `UPDATE … WHERE published_at IS NULL` hardening, yes/no?
4. **`GET /notifications` `unreadCount` page-field (§A):** spec promises it, code omits it. Fix by (a) FE uses `/unread-count` endpoint and spec drops the field (recommended — no BE change), or (b) BE adds the field to the page response?
5. **E-3 per-user `isRead`** in this sprint or deferred?
6. **E-2 variant:** minimal expand-in-card, or full detail route?
7. **E-4 direction:** FE PUT→POST (recommended) or BE POST→PUT-to-match-spec?
8. **Audience (§B):** confirm staff (ADMIN/TECHNICIAN/BOARD_MEMBER) excluded from announcement notifications; confirm deactivated users excluded.
9. **Notification body content:** full announcement content as `body`, or a short fixed VN string ("Có tin tức mới: {title}") with content read on the announcement page? (Affects table size: 2k rows × content length per ALL-publish.)
