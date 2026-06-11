# N3 — Per-User Event Notifications: Design Proposal

Status: **PROPOSAL — every choice below is PENDING CTO APPROVAL unless marked "CTO DECIDED" (from `reports/module10-extended-backlog.md` §N3).**
Date: 2026-06-11 · Investigated at HEAD `61952ed`. No production code, tests, or migrations in this step.

CTO DECIDED (inherited, not re-opened here): tickets get creator-chosen public/private flag, DEFAULT PRIVATE; residents browse public tickets and opt-in follow to join the notification thread.

---

## A. Verified current state (code citations)

### A.1 Spec-vs-code divergences

| API-SPEC promise | Code reality |
|---|---|
| spec:767 — new ticket → admin in-app notification | **ABSENT.** `TicketServiceImpl.createTicket` (TicketServiceImpl.java:282–328) creates ticket + history, calls no notification. |
| spec:855 — assign → assignee in-app + push | **in-app EXISTS** — `TicketServiceImpl.assignTicket` (TicketServiceImpl.java:391–399) calls `notificationService.createNotification(..., TICKET_ASSIGNED, ticketId, "Ticket")`. Two gaps: body is **English** ("Ticket Assigned" / "Ticket #<uuid> has been assigned to you.") — violates VN-first UI; fires only for `assignedToUser`, never for contractor assignment (acceptable — contractors have no accounts). Push = FCM still stubbed (known tech-debt). |
| spec:880 — status DONE → rating-prompt notification to resident | **ABSENT.** `TicketServiceImpl.updateStatus` (TicketServiceImpl.java:409–454) has zero notification calls — no status-change event of any kind. |
| spec:1069 — amenity deactivation cancels pending bookings "with a system notification to affected residents" | **Notification ABSENT.** `AmenityServiceImpl` has no `NotificationService` reference at all (grep: zero hits in module). |
| spec:2008 / spec:2018 — `PUT /notifications/{id}/read`, `PUT /notifications/read-all` | Code is **POST** (NotificationController.java:104, :124 — `@PostMapping`). Doc-only divergence; FE already uses POST since P5. Fix spec in this effort's docs step. |

### A.2 NotificationType enum (NotificationType.java:13–47, = PG enum `notification_type` in V1__create_enums_and_users.sql:134)

Existing values: `TICKET_ASSIGNED` (used: 1 site), `TICKET_STATUS_CHANGED` (**unused**), `TICKET_RATED` (**unused**), `TICKET_SLA_BREACHED` (**unused**), `BOOKING_APPROVED`/`BOOKING_REJECTED`/`BOOKING_REMINDER` (**unused**), `ANNOUNCEMENT_PUBLISHED` (used: phase-1 dispatch), `CONTRACT_EXPIRING` (used: scheduler), `SCHEDULE_DUE` (used: MaintenanceScheduleRunner), `GENERAL` (unused).

New values needed: `TICKET_CREATED`, `TICKET_SLA_WARNING` (approaching ≠ breached), `HOUSEHOLD_MEMBER_ADDED`. ⚠️ Column is a **named PG enum** (`Notification.java:69–72`, `@JdbcTypeCode(SqlTypes.NAMED_ENUM)`) → new values require a Flyway `ALTER TYPE notification_type ADD VALUE` migration. On PG ≥ 12 this runs inside a transaction, but the new value is unusable in the same transaction — the migration must contain ONLY the `ALTER TYPE` statements (no seed rows using them).

### A.3 Dispatch foundation (phase 1, reusable)

- Single-recipient: `NotificationService.createNotification(userId, title, body, type, referenceId, referenceType)` (NotificationServiceImpl.java:62–78) — loads User, saves one row.
- Multi-recipient batch: `AnnouncementServiceImpl` injects `NotificationRepository` directly and does in-TX `saveAll` with `userRepository.getReferenceById` (AnnouncementServiceImpl.java:396–407 — "build full batch in memory, one saveAll, no logging in loop"). N3 multi-recipient events reuse this exact pattern.
- Audience-resolution precedent: `ResidentRepository.findRecipientUserIdsByScopeName` (ResidentRepository.java:159–171) returns `List<UUID>` directly — model for new recipient queries.

### A.4 Household resolution

`ResidentRepository.findActiveByApartmentId(apartmentId)` **already exists** (ResidentRepository.java:35–41, JOIN FETCH user, moveOutDate IS NULL). Hook point: end of `ResidentServiceImpl.createResident` (ResidentServiceImpl.java:122–179), after `residentRepository.save` — notify all active residents of the apartment EXCEPT the newly created user. No new query needed.

Admin-recipient resolution: `UserRepository` has only `existsByRole(UserRole)` (UserRepository.java:66) — a **new** `List<UUID> findIdsByRoleAndActiveTrue(UserRole)`-style query is required for "notify all admins".

### A.5 Vehicles + bookings — actual mutation points

- `VehicleServiceImpl`: createVehicle:88, updateVehicle:152, deleteVehicle:200. **Vehicle entity has NO status/approval field** (Vehicle.java fields: type, licensePlate, brand, model, color, notes — no workflow). "Vehicle approved" events from the backlog do not exist in the domain. Only candidate: vehicle registered → admins.
- `AmenityServiceImpl`: createBooking:295 (resident creates PENDING → admin notification candidate), approveOrReject:453–486 (admin acts → resident recipient = `booking.getResident().getUser()`, rejectionReason available), cancelBooking:497, deactivateAmenity:214. Types BOOKING_APPROVED/REJECTED already exist. **However**: bookings/amenities are TEMP_HIDDEN_DEFERRED on resident FE (Layout.tsx:35–40) — notifications would deep-link to hidden pages.

### A.6 Ticket visibility today (grounds the is_public change)

`enforceReadAccess` (TicketServiceImpl.java:653–672) + `buildScopeSpec` (:687–709): RESIDENT sees tickets of **their active apartment** (already household-shared, not creator-only); TECHNICIAN sees assigned-to-me OR status NEW; ADMIN/BOARD_MEMBER unrestricted. Presign access (`assertPresignAccess`, :619–624) delegates to the same rule — any read-access widening automatically widens photo access (F-05 relevant, see §E).

### A.7 SLA data + scheduler infrastructure

- `Ticket.slaDeadline` set once at creation from per-category hours map (TicketServiceImpl.java:84–93, :316–319); SUGGESTION_FEEDBACK has no SLA. Breach is computed on-read (`isSlaBreached`, :876–881) — never persisted, never notified.
- Scheduling infrastructure **already exists**: `@EnableScheduling` (GemekApplication.java:20), two `@Scheduled` cron jobs (`MaintenanceScheduleRunner`:61 daily 07:30, `ContractExpiryScheduler`:56 daily 08:00). SLA scheduler is a third job in the same package, not new infrastructure.
- ⚠️ **Existing idempotency gap**: `ContractExpiryScheduler.checkExpiringContracts` (ContractExpiryScheduler.java:57–83) has NO sent-marker — it re-notifies the same staff user **every day for 30 days** per expiring contract. Do NOT copy this pattern; §D fixes it for SLA and proposes an optional follow-up fix for contracts.
- Tickets have **no human-readable code field** — only UUID + title. VN notification bodies below use the title.

---

## B. Participant/thread model — core schema decision (PENDING CTO APPROVAL)

### Options compared

**Option B1 — generic `notification_subscriptions` table (RECOMMENDED)**

```
notification_subscriptions (
    id          UUID PK DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL REFERENCES users(id),
    entity_type VARCHAR(100) NOT NULL,   -- same label space as notifications.reference_type ("Ticket")
    entity_id   UUID NOT NULL,
    joined_via  VARCHAR(20) NOT NULL,    -- CREATOR | ASSIGNEE | FOLLOWER
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (user_id, entity_type, entity_id)
)
```

**Option B2 — ticket-specific `ticket_followers` (user_id, ticket_id, ...)**

**Membership style — explicit rows for everyone (recommended) vs implicit creator/assignee + explicit followers only:**

| Criterion | B1 + explicit-all (recommended) | B2 / implicit hybrid |
|---|---|---|
| N4 comments reuse | Direct: comment dispatch = `SELECT user_id WHERE entity_type='Ticket' AND entity_id=?`. Same table later serves comment threads on any entity. | ticket_followers locks schema to tickets; N4 on other entities needs a second table or a rebuild. |
| Unsubscribe semantics | Uniform: delete row. Creator can mute own ticket; follower unfollows the same way. | Implicit creator cannot unsubscribe without an extra "mute" table — two mechanisms. |
| Assignee reassignment | Old assignee's row **stays** (joined_via=ASSIGNEE, historical) — they keep receiving updates on work they touched; removable later if CTO prefers (delete-on-reassign is one line). | Implicit "current assignee" silently drops old assignee mid-thread — surprising, and history is lost. |
| Dispatch query cost | One indexed SELECT per event (idx on entity_type+entity_id). | Implicit = UNION of derived sets + follower table every dispatch — more complex, same cost class. |
| Migration cost | Backfill needed: one INSERT…SELECT for existing tickets' creators + current assignees. | No backfill, but pays for it in every dispatch query forever. |

**Recommendation: B1, explicit rows for all participants.** Rows written at: ticket create (CREATOR), assign (ASSIGNEE), follow endpoint (FOLLOWER). Backfill in the same migration. **Admins get NO rows** — they receive only TICKET_CREATED (and SLA escalations); putting every admin in every thread would spam them on each status change while they already have the full list view. Old-assignee row retention = open question G-4.

Migration sketch (no SQL file yet): V14 creates table + 2 indexes (entity lookup, user lookup) + backfill `INSERT … SELECT submitted_by, 'Ticket', id, 'CREATOR' FROM tickets` and same for `assigned_to_user_id IS NOT NULL` with `ON CONFLICT DO NOTHING`.

---

## C. Event catalog (VN bodies designed now — PENDING CTO APPROVAL)

Recipients legend: *thread* = all `notification_subscriptions` rows for the ticket EXCEPT the acting user.

| # | Event | Trigger point | Recipients | NotificationType | VN body (title → body) | reference |
|---|---|---|---|---|---|---|
| C1 | Ticket created | `TicketServiceImpl.createTicket` after save (:321) | All active ADMIN users (new UserRepository query) | **NEW** `TICKET_CREATED` | «Phản ánh mới» → «Phản ánh mới: "{title}" — căn hộ {unitNumber}, tòa {block}.» | Ticket/{id} |
| C2 | Ticket assigned (assignee side) | `assignTicket` (:391 — EXISTS, localize) | Assigned staff user | `TICKET_ASSIGNED` (exists) | «Phản ánh được phân công» → «Phản ánh "{title}" đã được phân công cho bạn.» | Ticket/{id} |
| C3 | Ticket assigned (thread side) | `assignTicket`, NEW→ASSIGNED auto-transition (:381–385) | thread (= creator at this point) | `TICKET_STATUS_CHANGED` (exists, unused) | «Cập nhật phản ánh» → «Phản ánh "{title}" đã được tiếp nhận và phân công xử lý.» | Ticket/{id} |
| C4 | Status changed | `updateStatus` after transition (:448–451) | thread, except actor | `TICKET_STATUS_CHANGED` | «Cập nhật phản ánh» → «Phản ánh "{title}" chuyển sang trạng thái: {statusVN}.» (statusVN: Đang xử lý / Hoàn thành / Đã hủy) | Ticket/{id} |
| C5 | DONE → rating prompt | `updateStatus` when target=DONE (:440) | Submitter only (extra row beyond C4) | `TICKET_STATUS_CHANGED` (**reuse** — no new enum value; see G-7) | «Đánh giá xử lý phản ánh» → «Phản ánh "{title}" đã hoàn thành. Vui lòng đánh giá chất lượng xử lý.» | Ticket/{id} |
| C6 | Ticket rated | `rateTicket` after save (:602) | Assigned staff user (if any) | `TICKET_RATED` (exists, unused) | «Phản ánh được đánh giá» → «Phản ánh "{title}" được cư dân đánh giá {rating}/5 sao.» | Ticket/{id} |
| C7 | SLA approaching | SLA scheduler (§D) | Assignee (if any) + all active ADMINs | **NEW** `TICKET_SLA_WARNING` | «Phản ánh sắp quá hạn» → «Phản ánh "{title}" sắp đến hạn xử lý ({deadline, dd/MM HH:mm}).» | Ticket/{id} |
| C8 | SLA overdue | SLA scheduler | Assignee (if any) + all active ADMINs | `TICKET_SLA_BREACHED` (exists, unused) | «Phản ánh quá hạn» → «Phản ánh "{title}" đã quá hạn xử lý.» | Ticket/{id} |
| C9 | Resident added to apartment | `ResidentServiceImpl.createResident` after save (:170) | `findActiveByApartmentId` minus new user | **NEW** `HOUSEHOLD_MEMBER_ADDED` | «Thành viên mới» → «Cư dân {fullName} đã được thêm vào căn hộ {unitNumber}.» | Resident/{id} (no deep-link route v1 — bell marks read only, per N1 unknown-type rule) |

**Proposed CUT from v1 scope** (each PENDING CTO APPROVAL):

| Cut event | Reason |
|---|---|
| BOOKING_APPROVED / BOOKING_REJECTED / booking-created→admin | Bookings/amenities are TEMP_HIDDEN_DEFERRED on resident FE (Layout.tsx:35–40) — notifications would deep-link to hidden pages. Types already exist in the enum; wire them when the feature is unhidden (small, isolated additions to `approveOrReject`/`createBooking`). |
| Vehicle registered → admins | No approval workflow exists (Vehicle entity has no status field) — notification is FYI-only; admin has the vehicles list page. Low value vs. enum+dispatch cost. Revisit if an approval workflow is ever added. |
| Amenity-deactivation booking-cancellation notice (spec:1069) | Same TEMP_HIDDEN reason. Recorded as spec divergence in §A; spec to be annotated. |
| moveOut / updateResident household notices | Backlog names only "resident assigned"; move-out broadcast is sensitive (privacy) — needs its own decision. |

---

## D. SLA scheduler (PENDING CTO APPROVAL)

- **Placement**: third job in existing `scheduler/` package, `@Scheduled` like the two precedents. **ShedLock NOT needed**: deployment is a single backend container (docker-compose monolith, no horizontal scaling configured anywhere); a second instance would require revisiting all three schedulers, not just this one. Documented assumption, not silent.
- **Frequency**: every 15 minutes (`cron = "0 */15 * * * *"`). SLAs are 24–72h; 15-min granularity gives ≤1% deadline slack, and each scan is one cheap indexed query. Daily (like the precedents) is too coarse for a 24h SLA warning.
- **Warning threshold**: fixed 2 hours before `slaDeadline` for v1 (single constant). Per-category percentage (e.g. 10% of SLA window) is an alternative — open question G-2.
- **Idempotency — recommended: marker columns on `tickets`**: `sla_warning_notified_at TIMESTAMPTZ NULL`, `sla_overdue_notified_at TIMESTAMPTZ NULL`.
  - Scan query: `WHERE status NOT IN (DONE, CANCELLED) AND sla_deadline IS NOT NULL AND sla_deadline < now() + interval '2 hours' AND sla_warning_notified_at IS NULL` (analogous for overdue with `< now()` and the other marker). Marker set in the same TX as the notification insert → a re-scan can never re-notify; a crash between insert and commit rolls back both.
  - Rejected alternative — separate `sla_notifications_sent` table: more general but adds a join per scan and a second write path; markers are 2 columns on an entity that already owns the deadline. The generic-table benefit (multiple notification kinds per ticket) is not needed — there are exactly two SLA events ever.
- **Edge cases**:
  - Ticket resolved between scans → excluded by the status predicate; no late notification.
  - Deadline changed after warning sent → cannot happen today (`slaDeadline` is set once at creation, no mutation path exists). If a future feature edits deadlines, that feature must null the markers — one line, noted in Javadoc.
  - Ticket already overdue at warning-scan time (created with breach in the past, e.g. after downtime) → both predicates match; propose sending ONLY the overdue notification and setting both markers (skip the now-pointless warning).
- **Existing-bug follow-up (optional, G-6)**: apply the same marker pattern to `ContractExpiryScheduler` (`contracts.expiry_notified_at`) — currently re-notifies daily for 30 days.
- **Deterministic tests**: scheduler bean's public method invoked directly in `@SpringBootTest` (no sleeps, no cron wait — same approach as direct service-method tests in the suite). Fixtures: ticket with deadline inside warning window → 1 notification + marker set; second direct call → 0 new rows; resolved ticket in window → 0 rows; overdue ticket → breach notification once. Clock: deadline values computed relative to `OffsetDateTime.now()` in the fixture (no clock mocking needed since the window is wide).

---

## E. Public/private tickets (flag itself = CTO DECIDED; details PENDING)

- **Migration**: `ALTER TABLE tickets ADD COLUMN is_public BOOLEAN NOT NULL DEFAULT FALSE;` — existing rows stay private (matches CTO default-private decision). `CreateTicketRequest` gains optional `isPublic` (default false). Immutable after create in v1 (no edit endpoint — avoids "was private, suddenly public" surprise; G-3).
- **List scoping**: `buildScopeSpec` RESIDENT branch (TicketServiceImpl.java:694–706) becomes `apartment = mine OR isPublic = true`. Plus a request flag/filter so the FE can show "Của tôi" vs "Cộng đồng" tabs without client-side filtering. Admin/technician scoping unchanged.
- **Detail access**: `enforceReadAccess` RESIDENT branch allows `ticket.isPublic()` as an alternative to apartment match — but returns the **redacted** view (below) when the caller is outside the household.
- **Follow endpoints**: `POST /api/tickets/{id}/follow` and `DELETE /api/tickets/{id}/follow` (RESIDENT role; 404-style behavior for private tickets the caller cannot see; follow = INSERT subscription row joined_via=FOLLOWER, unfollow = DELETE). Idempotent via the unique constraint + ON CONFLICT semantics.
- **VISIBILITY SCOPE PROPOSAL (open question from backlog — this is the proposed redaction rule):** non-household residents viewing a public ticket see:
  - **Visible**: title, description, category, status, priority, block name, createdAt, status history *timestamps + statuses*, resolution notes.
  - **Hidden**: submitter fullName + phone (show «Cư dân» placeholder — neighbour complaints must not paint a target on the creator), apartment `unitNumber` (block only — same reason), **photos** (photos can show the inside of a home; additionally `assertPresignAccess` reuses `enforceReadAccess` (:619–624), so exposing photos publicly would widen the presign surface **before F-05 is fixed** — forbidden by the F-05 gate), status-history `changedBy` names (staff names are fine to show — alternative; kept hidden for symmetry, G-5), rating comment.
  - Reasoning: the creator chose to publicize the *issue* ("elevator broken in block A"), not their identity or home interior. Followers want progress updates, which status + history timestamps fully provide.
- **FE touchpoints** (resident app): create-form checkbox «Công khai phản ánh để cư dân khác theo dõi» (default off); TicketsPage second tab «Cộng đồng» listing public tickets; detail page follow/unfollow button «Theo dõi» / «Bỏ theo dõi» (only on non-household public tickets); redacted fields simply absent from the response — no FE-side hiding logic.

---

## F. Task breakdown (each independently committable + testable; BE before FE)

| Step | Scope | Migration? |
|---|---|---|
| P1 | **V13**: `ALTER TYPE notification_type ADD VALUE` × 3 (`TICKET_CREATED`, `TICKET_SLA_WARNING`, `HOUSEHOLD_MEMBER_ADDED`) — ALTER statements only, nothing else in the file. + Java enum constants. Test: context loads, enum round-trips. | V13 |
| P2 | **V14**: `notification_subscriptions` table + backfill (creator + current assignee of existing tickets) + entity/repository + small `SubscriptionService` (subscribe / unsubscribe / participantUserIds). Unit-tested standalone. | V14 |
| P3 | Ticket lifecycle dispatch: C1 (create→admins, new `UserRepository` role query, batch `saveAll` pattern from AnnouncementServiceImpl:396–407), C2 VN-localization of the existing assign notification, C3/C4/C5 thread dispatch in `assignTicket`/`updateStatus` (exclude actor), C6 in `rateTicket`. Subscription rows written at create/assign. Tests per event. | — |
| P4 | C9 household notify in `ResidentServiceImpl.createResident` (reuses `findActiveByApartmentId`). Tests. | — |
| P5 | **V15**: `tickets.is_public` + scoping (`buildScopeSpec`, `enforceReadAccess`) + redacted public detail mapping + follow/unfollow endpoints + `isPublic` on create request. Tests incl. redaction assertions + follow→thread-dispatch integration. | V15 |
| P6 | **V16**: SLA marker columns + scheduler job + repository scan queries. Deterministic direct-invocation tests (§D). | V16 |
| P7 | FE resident: `NOTIF_ROUTES` + `Ticket: id => /tickets/{id}` (Layout.tsx:9–11 — map was built for this); create-form public toggle; «Cộng đồng» tab + follow button; vi.ts keys. tsc + vite green. | — |
| P8 | FE admin: bell rows clickable (deferred from N1 — admins start RECEIVING rows at P3, so the admin bell becomes live here) — port resident `handleNotifClick` pattern + admin `NOTIF_ROUTES` (Ticket → admin ticket detail route). tsc + vite green. | — |
| P9 | Docs: API-SPEC — new endpoints, PUT→POST fix (spec:2008/2018), spec:1069 annotation, event list; PROGRESS/DECISIONS; backlog file update. | — |

Cut line for a smaller v1 if CTO wants: P1–P4 + P7/P8 (events without public-tickets/SLA); P5 and P6 are independent of each other and of P3/P4.

## G. Open questions for CTO

1. **Cuts confirmed?** Booking events, vehicle-registered, amenity-deactivation notice, move-out notice — all cut from v1 (§C table). Booking types stay in enum, wired when bookings unhide.
2. **SLA warning threshold**: fixed 2h for all categories, or per-category (e.g. 10% of SLA window: 2.4h/4.8h/7.2h)?
3. **is_public immutable after create** (v1) — acceptable, or need a "make public/private later" toggle (adds edit endpoint + follower-implication decisions)?
4. **Old assignee on reassignment**: keep subscription row (recommended — worked the ticket, keeps context) or delete on reassign?
5. **TICKET_CREATED recipients**: ADMIN only (proposed), or ADMIN + BOARD_MEMBER? Same question for SLA escalations (C7/C8).
6. **ContractExpiryScheduler daily re-notify bug**: fix inside N3 (one marker column + WHERE clause, ~30 min) or log as separate debt?
7. **Rating prompt type**: reuse `TICKET_STATUS_CHANGED` (proposed — no enum migration growth) vs dedicated `TICKET_RATING_REQUESTED` value (cleaner analytics, +1 enum value)?
8. **Redaction rule** (§E): approve as proposed? Specifically: photos stay hidden on public tickets until F-05 is fixed; status-history staff names hidden or shown?
