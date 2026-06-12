# Module 10 — Extended Backlog (N1–N4)

CTO-approved scope decisions + open questions, recorded 2026-06-11 at phase-1 close-out.
Authoritative phase-1 design: `reports/module10-dispatch-design.md`.

## Phase 1 — DONE (2026-06-11)

Announcement→notification dispatch core: atomic in-publish-TX dispatch (saveAll batched), CAS publish guard = 409 source, per-user isRead, FE bell badge via /unread-count, mark-all-read 405 fix, expand-in-card body. CTO browser smoke-verified. Suite 256/256. The dispatch core (createNotification rows + referenceId/referenceType + bell + unread) is the REUSABLE FOUNDATION for all phases below.

Commits: P1 221813b/a671c70 · P2 22114b8/8880499 · P4 0070727/3b5b725 · P5 dcfc42b/732beac/32bda65.

## N1 — Notification deep-link + news detail route — ✅ DONE (2026-06-11)

Delivered: bell rows clickable (mark-read via `useMarkNotificationRead` + deep-link by `NOTIF_ROUTES` referenceType map — N3 types extend the map; unknown type → mark-read only); resident `/announcements/:id` detail page (full content, mark-read on first load, single read surface — P5 expand-in-card REMOVED); `any` debt paid (`api/types.ts`: AnnouncementItem + NotificationItem). Admin bell deferred to N3 (admins receive no dispatch rows until then). Commits e26a965 / d5e6b4f / b34628c.

## N2 — Rich content in announcements (CTO DECIDED: rich-text editor with embedded images/video)

> **F-05 gate LIFTED (2026-06-12, hardening H2):** the presign read path for `announcements/` keys is live (public-read per E3, commit 6f3dd96) — N2 is no longer blocked on F-05. N2 still owns: upload endpoint, attachment table + row check, editor.

Embedded media in content via editor (e.g. TipTap), MinIO-backed uploads. HARD REQUIREMENTS recorded now: server-side HTML sanitization (XSS), safe render on both apps, video size limits. PREREQUISITE: F-05 (IDOR on file presign) must be fixed before or together with this — expanding upload surface before the presign hardening is forbidden. Design session required before any code.

## N3 — Per-user event notifications — ✅ DONE (smoke-verified 2026-06-12, all 5 CTO rounds passed)

Delivered P1–P8 per the approved design (`reports/n3-event-notifications-design.md`): V13 enum values; V14 `notification_subscriptions` + backfill; ticket lifecycle dispatch C1–C6 (VN bodies, locked terms); C9 household notice; V15 `is_public` + redacted public view + visibility filter + follow/unfollow; V16 SLA scheduler (15-min, 2h warning, sent-markers) + ContractExpiry once-only fix (G6); resident FE (public toggle, «Cộng đồng» tab, follow button, Ticket deep-link); admin FE (bell clickable, Ticket/Contract routes). Viewer flags `isFollowing`/`redacted` added at P7. API-SPEC updated at P9.

## N4 — Ticket media + comments (BACKLOG, not scheduled)

Photo/video upload on tickets (before/during/after processing stages) + comment thread (resident + admin) per ticket. Comments will dispatch into the N3 participant thread — N3's participant model must anticipate this. TicketDetailPage already has a "Hình ảnh" section — investigate existing photo support before scoping. Also gated behind F-05.

## Known tech-debt carried

- ~~Resident AnnouncementsPage item type is `any`~~ — PAID in N1 (`api/types.ts`).
- External channels (FCM/SMTP/SMS) still stubbed.
- **Shared dev-DB test pollution** — part of the suite writes committed rows to the Docker dev DB (249 garbage tickets, 209 bookings observed 2026-06-11; caused the P2 backfill-gap misreading, the amenity list flake — de-flaked d90f98c — and the still-latent parking phone-collision flake). Fix direction: migrate Docker-required tests to testcontainers or per-run schema reset. Own session, not mixed into N3.
- **Resident FE ticket typing any-debt** — list rows + detail body still untyped (`tk: any`, `TicketDetailItem` index signature); only the P7 viewer flags are typed. Full TicketSummary/Detail FE typing pass deferred.

## New backlog items (CTO, N3 smoke close-out 2026-06-12)

- ~~(a) Submitter display in NON-redacted ticket views~~ **DONE 2026-06-12 (62239b6):** admin ticket detail now renders «{fullName} - {unitNumber}» — the only full-view surface that displays the submitter (resident pages verified: mine-rows/detail show no submitter; community line stays redacted «Cư dân»).
- ~~(b) Ticket assign form appointment-date renders mm/dd/yyyy~~ **DONE 2026-06-12 (639c98f):** was native `input type=datetime-local` (locale-driven, unfixable by attributes) AND sent a time component against BE `LocalDate`; swapped to existing `VNDatePicker` (dd/MM display, ISO date value). No new dependency.
- **(c) Staff/non-resident user management module MISSING** — no admin UI to create/manage TECHNICIAN/BOARD_MEMBER/ADMIN accounts. Role overlap (a resident who is also ADMIN or TECHNICIAN) is impossible today: `users.role` is a single-value enum; multi-role requires schema + auth redesign (JWT claims, RequireRole, BE role checks). **Design session required — do NOT improvise.**
- **(d) Resident move-out flow — UI MISSING** (correction to the smoke note: the BE endpoint EXISTS — `POST /api/residents/{id}/move-out`, ResidentController:172, spec:569). Schema fully supports it (`residents.moveOutDate`; all N3 recipient queries filter on it). Remaining work is admin UI only: an "end residency" action setting moveOutDate (soft), NOT a hard delete.
