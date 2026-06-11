# Module 10 — Extended Backlog (N1–N4)

CTO-approved scope decisions + open questions, recorded 2026-06-11 at phase-1 close-out.
Authoritative phase-1 design: `reports/module10-dispatch-design.md`.

## Phase 1 — DONE (2026-06-11)

Announcement→notification dispatch core: atomic in-publish-TX dispatch (saveAll batched), CAS publish guard = 409 source, per-user isRead, FE bell badge via /unread-count, mark-all-read 405 fix, expand-in-card body. CTO browser smoke-verified. Suite 256/256. The dispatch core (createNotification rows + referenceId/referenceType + bell + unread) is the REUSABLE FOUNDATION for all phases below.

Commits: P1 221813b/a671c70 · P2 22114b8/8880499 · P4 0070727/3b5b725 · P5 dcfc42b/732beac/32bda65.

## N1 — Notification deep-link + news detail route — ✅ DONE (2026-06-11)

Delivered: bell rows clickable (mark-read via `useMarkNotificationRead` + deep-link by `NOTIF_ROUTES` referenceType map — N3 types extend the map; unknown type → mark-read only); resident `/announcements/:id` detail page (full content, mark-read on first load, single read surface — P5 expand-in-card REMOVED); `any` debt paid (`api/types.ts`: AnnouncementItem + NotificationItem). Admin bell deferred to N3 (admins receive no dispatch rows until then). Commits e26a965 / d5e6b4f / b34628c.

## N2 — Rich content in announcements (CTO DECIDED: rich-text editor with embedded images/video)

Embedded media in content via editor (e.g. TipTap), MinIO-backed uploads. HARD REQUIREMENTS recorded now: server-side HTML sanitization (XSS), safe render on both apps, video size limits. PREREQUISITE: F-05 (IDOR on file presign) must be fixed before or together with this — expanding upload surface before the presign hardening is forbidden. Design session required before any code.

## N3 — Per-user event notifications (design-first; investigation session required)

Events: resident assigned to apartment → notify household members; ticket lifecycle (created → admins; assigned → assignee joins thread; status change / SLA-approaching / SLA-overdue → all thread participants); similar for vehicles + bookings.

CTO DECIDED: tickets get a creator-chosen public/private flag (DEFAULT PRIVATE); residents can browse public tickets and opt-in follow (button) to join the notification thread.

Known design work (do NOT improvise during implementation):
- Participant/follower model (new table, must also serve future comments — N4).
- SLA scheduler (NEW infrastructure — must be idempotent, sent-marker so re-scans don't re-notify).
- Public-ticket visibility scope (what other residents see: apartment number? photos? — OPEN, propose in design).
- Migration for `is_public` + list-query scoping.

## N4 — Ticket media + comments (BACKLOG, not scheduled)

Photo/video upload on tickets (before/during/after processing stages) + comment thread (resident + admin) per ticket. Comments will dispatch into the N3 participant thread — N3's participant model must anticipate this. TicketDetailPage already has a "Hình ảnh" section — investigate existing photo support before scoping. Also gated behind F-05.

## Known tech-debt carried

- ~~Resident AnnouncementsPage item type is `any`~~ — PAID in N1 (`api/types.ts`).
- External channels (FCM/SMTP/SMS) still stubbed.
- **Shared dev-DB test pollution** — part of the suite writes committed rows to the Docker dev DB (249 garbage tickets, 209 bookings observed 2026-06-11; caused the P2 backfill-gap misreading, the amenity list flake — de-flaked d90f98c — and the still-latent parking phone-collision flake). Fix direction: migrate Docker-required tests to testcontainers or per-run schema reset. Own session, not mixed into N3.
