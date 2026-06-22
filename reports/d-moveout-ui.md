# Backlog (d) — Resident Move-Out ("Kết thúc cư trú") UI

**Status: ⏸ BLOCKED at BE-contract verification — did NOT build.**
**Reason: endpoint REQUIRES a `moveOutDate` body; CTO "no date picker, BE sets current date" decision is not honored by the current BE.**

## BE Contract Findings (verified, cited)

### 1. POST /api/residents/{id}/move-out — signature ❌ CONTRADICTS PLAN
- `ResidentController.java:172-180` —
  ```java
  @PostMapping("/residents/{id}/move-out")
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<ResidentResponse> moveOut(
          @PathVariable UUID id,
          @Valid @RequestBody MoveOutRequest req,
          @AuthenticationPrincipal UserPrincipal principal) { ... }
  ```
- `MoveOutRequest.java:23-27` — body is **required and validated**:
  ```java
  @NotNull(message = "moveOutDate is required.")
  private LocalDate moveOutDate;
  // plus: private String notes;  (optional)
  ```
- **Effect:** a no-body / empty-body POST → **400** "moveOutDate is required." The BE does **NOT** set `moveOutDate = now()` server-side — it persists the **client-supplied** date (`ResidentServiceImpl.java:265` `resident.setMoveOutDate(req.getMoveOutDate())`).
- **Contradiction:** CTO chose "no date picker, BE sets current date." BE does the opposite — it demands the date from the client. The no-picker decision needs revisiting (see Options below).

### 2. @PreAuthorize — ✅ ADMIN
- `ResidentController.java:173` — `@PreAuthorize("hasRole('ADMIN')")`. Matches an ADMIN-gated detail page.

### 3. What move-out actually DOES — soft, no login block
`ResidentServiceImpl.java:254-279`:
- Sets `moveOutDate = req.moveOutDate` (`:265`).
- Clears `primaryContact` flag if the resident was the primary contact (`:268-269`).
- Appends a `MOVED_OUT` history entry with the date + optional notes (`:275`).
- Guards re-trigger: if `moveOutDate != null` already → throws `RESIDENT_ALREADY_MOVED_OUT` (`:261-263`).
- **Does NOT** deactivate the user account, **does NOT** block login, **no** cascade. Pure soft end-of-residency.
- **Real effect for UI copy:** "Đánh dấu cư dân đã kết thúc cư trú (ghi ngày rời đi, gỡ vai trò liên hệ chính). Không khoá tài khoản đăng nhập."

### 4. Undo / reverse endpoint — ❌ NONE
- `ResidentController.java` has only `POST /residents`, `PUT /residents/{id}`, `POST /residents/{id}/move-out` (`:111,:154,:172`). No reactivate / clear-move-out endpoint.
- Plus the service guard (#3) hard-rejects a second move-out. → **Not reversible from the UI.** Confirm dialog must warn so.

### 5. moveOutDate exposed in detail response — ✅ YES
- `ResidentResponse.java:46-49` — `private LocalDate moveOutDate;` (`null` = active). UI can render the «Đã chuyển đi» badge + formatted date directly from the existing detail payload.

## Options for CTO (BE must NOT change per task)

| # | Approach | Honors "no picker"? | Honors "BE sets date"? | BE change? |
|---|----------|--------------------|------------------------|-----------|
| **A** *(recommended)* | FE sends `{ moveOutDate: <today, computed client-side> }`, no picker shown. User just confirms; FE auto-fills today. | ✅ yes | ⚠️ no — FE supplies today, not BE | none |
| B | Add a date picker, user picks the date. | ❌ no (revisits decision) | n/a | none |
| C | Make `moveOutDate` optional, default `now()` server-side. | ✅ | ✅ | **yes — forbidden by task** |

**Recommendation:** Option A. The UX intent (no manual date entry) is preserved — FE fills today's date automatically and only asks the admin to confirm. The only deviation from the literal "BE sets the date" wording is that the date is stamped client-side. If CTO accepts A, build proceeds with `moveOutDate = <today in project TZ>` baked into the POST body (still no picker UI). If CTO wants a true BE-stamped date, that needs a BE change (Option C) which this task forbids.

## Build status
Not started. Awaiting CTO decision on Option A vs B vs C.
