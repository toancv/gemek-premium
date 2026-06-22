# Apartment status lockdown — stop client-set status on update + hide MAINTENANCE in UI

**Date:** 2026-06-22  **Branch:** `deploy/local`
**Predecessors:** `reports/apartment-occupancy-fix.md` (status derived), `reports/apartment-filter-fix.md` (filter derived).
**Mode:** implemented (TDD). Two related changes finalizing the occupancy model.

---

## Why

Occupancy is now **fully derived** (`OccupancyResolver`): `OCCUPIED`/`AVAILABLE` come from active
residents (`move_out_date IS NULL`), `MAINTENANCE` is the only stored-priority value. Post-V19 the
stored column only ever holds `AVAILABLE`/`MAINTENANCE`. Two loose ends remained:

1. The update endpoint still accepted a **client-supplied `status`** (`setStatus(request.status())`),
   letting an admin store `OCCUPIED`/`MAINTENANCE` that contradicts the derived display — a desync hole.
2. The admin UI still **offered MAINTENANCE** in the filter dropdown and a free status `<select>` in the
   edit form, even though there is no maintenance set-flow.

Verified before changing: the **only** code that sets `status` was `createApartment` (:144, hardcoded
`AVAILABLE` — left as-is) and `updateApartment` (`setStatus(request.status())`). After this change,
**nothing client-driven sets status**; create's hardcoded `AVAILABLE` stays.

## (b) Backend — status no longer client-settable on update

- `UpdateApartmentRequest` (DTO): **`status` field removed** (+ its `@NotNull`, the `ApartmentStatus`
  import, and the javadoc `@param`). Now 4 fields: `floor`, `unitNumber`, `areaSqm`, `notes`.
- `ApartmentServiceImpl.updateApartment`: `apartment.setStatus(request.status())` **removed**; replaced
  with a comment encoding the rationale. The apartment keeps its stored status; other fields still update.
- **No production code depended on update setting status** — only one test helper did (adjusted below).
- `OccupancyResolver`, the `ApartmentStatus` enum, and the BE `?status=` filter (incl. MAINTENANCE) are
  **untouched** — MAINTENANCE stays fully supported in BE for later re-enable.

## (a) Frontend — hide MAINTENANCE

- `ApartmentsPage.tsx` filter dropdown: **MAINTENANCE option removed** (kept All / `OCCUPIED` «Đã ở» /
  `AVAILABLE` «Còn trống»). The BE filter still supports `?status=MAINTENANCE` — just not offered in UI.
- `ApartmentsPage.tsx` edit form: the free status `<select>` is replaced with a **read-only** derived
  display ("(tự động theo cư dân)"); the `status` field is no longer sent in the update payload.
- Status **badge** left graceful: `statusColors` still maps MAINTENANCE (yellow) so a legacy MAINTENANCE
  value renders correctly, but in practice only Đã ở / Còn trống appear (no set flow). Unknown values fall
  back to a grey badge — rendering never breaks.

## Tests (TDD, green)

- **New guard** `ApartmentServiceImplTest.updateApartment_doesNotChangeStoredStatus`: stored status
  `MAINTENANCE`; update request (no status field) with new floor/unit/area/notes → captured saved entity
  keeps `MAINTENANCE` (desync hole closed) AND the other four fields are applied. Red first (the new
  4-arg DTO + removed `setStatus` would not compile against the old code), green after the change.
- **Adjusted** `ApartmentStatusFilterIntegrationTest.setMaintenance(...)`: previously stamped MAINTENANCE
  via the **PUT update endpoint** (which no longer accepts status). Now persists it directly via
  `ApartmentRepository.save` — the value the BE filter/resolver still honour. Removed the now-unused
  `UpdateApartmentRequest`/`put` imports; added `ApartmentRepository` + entity imports. The 5 filter
  tests (incl. `?status=MAINTENANCE`) are unchanged in intent and still green.
- Occupancy display/filter behaviour is unchanged (derived) — all existing occupancy/filter tests green.

## Verify

- Full backend suite (`backend/mvnw.cmd test`): **359/359**, 0 failures, 0 errors (was 358 + the new
  guard test). BUILD SUCCESS.
- Admin frontend (`pnpm --filter admin build` → `tsc && vite build`): green, 590 modules transformed.

## Files

- `backend/.../apartment/dto/UpdateApartmentRequest.java` — `status` field removed.
- `backend/.../apartment/ApartmentServiceImpl.java` — `setStatus(request.status())` removed (+ rationale).
- `frontend/apps/admin/src/pages/ApartmentsPage.tsx` — MAINTENANCE filter option removed; edit status
  control made read-only; `status` dropped from update payload.
- `backend/.../apartment/ApartmentServiceImplTest.java` — new status-not-settable guard test.
- `backend/.../apartment/ApartmentStatusFilterIntegrationTest.java` — `setMaintenance` now persists via repo.
- `docs/API-SPEC.md` — `PUT /api/apartments/{id}` no longer accepts `status`.
- `PROGRESS.md`, `DECISIONS.md` — updated.

## Deferred (backlog)

- **Maintenance set flow.** No UI/endpoint currently sets `MAINTENANCE`. The BE (resolver, enum, filter)
  fully supports it; when a maintenance workflow is needed, re-add a dedicated, intentional set path
  (NOT a free status field on the generic update) and unhide the UI filter option.
