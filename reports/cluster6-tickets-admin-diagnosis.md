# Diagnosis — Cluster 6 Admin Tickets (#15 Assign, #16 Update Status, #17 Create)

## 1. Current success/error handling

### #15 — Assign Ticket (handleAssign, TicketDetailPage:87–101)

| Aspect | Current state |
|--------|--------------|
| Client validation | `'Chọn nhân viên hoặc nhà thầu'` — ✅ VN |
| Try/catch | ✅ exists |
| Success | Silent — clears fields only; no toast ❌ |
| Error | `err?.response?.data?.message ?? 'Không thể phân công'` — raw server text as primary ❌ |
| Error display | `{actionError && ...}` at line 209 — shared state, inside assign form ✅ |
| Hook (`useAssignTicket`) meta | `meta: { skipErrorToast: true }` — no successMessage ❌ |

### #16 — Update Ticket Status (handleStatusUpdate, TicketDetailPage:103–111)

| Aspect | Current state |
|--------|--------------|
| Client validation | `'Select a status'` — English ❌ |
| Try/catch | ✅ exists |
| Success | Silent — clears fields only; no toast ❌ |
| Error | `err?.response?.data?.message ?? 'Failed to update status'` — English fallback ❌ |
| Error display | **BUG**: uses shared `actionError` state, but `{actionError && ...}` rendered in Assign panel (line 209), not Status panel — status update errors appear in the wrong form section or not at all ❌ |
| Hook (`useUpdateTicketStatus`) meta | `meta: { skipErrorToast: true }` — no successMessage ❌ |

### #17 — Create Ticket (handleCreate, TicketsPage:42–63)

| Aspect | Current state |
|--------|--------------|
| Client validation | VN ✅ (`'Vui lòng chọn căn hộ'`, `'Tiêu đề không được để trống'`) |
| Try/catch | ✅ exists |
| Success | redirect to `/tickets/${created.id}` — ✅ KEEP (valid UX exception) |
| Error | `err?.response?.data?.message ?? 'Tạo ticket thất bại'` — raw server text as primary ❌ |
| Error display | `{formError && ...}` ✅ |
| Hook (`useCreateTicket`) meta | `meta: { skipErrorToast: true }` — no successMessage (correct — redirect is success signal) |

## 2. BE ErrorCodes per operation

### PUT /api/tickets/{id}/assign — `TicketServiceImpl.assignTicket()`

| Code | Source | Java:line |
|------|--------|-----------|
| `NOT_FOUND` | ticket not found (requireTicket) | TicketServiceImpl:638 |
| `VALIDATION_ERROR` | both assignedToUserId + assignedToContractorId set simultaneously | TicketServiceImpl:342 |
| `CONTRACTOR_ASSIGNMENT_NOT_ALLOWED` | contractor assigned to non-MAINTENANCE_REPAIR ticket | TicketServiceImpl:349 |
| `NOT_FOUND` | caller user not found | TicketServiceImpl:354 |
| `NOT_FOUND` | staff user not found | TicketServiceImpl:363 |
| `NOT_FOUND` | contractor not found | TicketServiceImpl:370 |

### PUT /api/tickets/{id}/status — `TicketServiceImpl.updateStatus()`

| Code | Source | Java:line |
|------|--------|-----------|
| `NOT_FOUND` | ticket not found (requireTicket) | TicketServiceImpl:638 |
| `FORBIDDEN` | TECHNICIAN updating ticket not assigned to them | TicketServiceImpl:420 |
| `INVALID_STATUS_TRANSITION` | transition not in VALID_TRANSITIONS map | TicketServiceImpl:431 |
| `NOT_FOUND` | caller user not found | TicketServiceImpl:436 |

### POST /api/tickets — `TicketServiceImpl.createTicket()`

| Code | Source | Java:line |
|------|--------|-----------|
| `NOT_FOUND` | apartment not found | TicketServiceImpl:286 |
| `FORBIDDEN` | resident no active record / wrong apartment | TicketServiceImpl:292, 295 (resident path only — admin unaffected) |
| `NOT_FOUND` | submitter user not found | TicketServiceImpl:301 |

## 3. getVnErrorMessage coverage check

| Code | Mapped | VN message |
|------|--------|------------|
| `NOT_FOUND` | ✅ | "Không tìm thấy dữ liệu." |
| `VALIDATION_ERROR` | ✅ | "Dữ liệu không hợp lệ." |
| `CONTRACTOR_ASSIGNMENT_NOT_ALLOWED` | ✅ | "Phân công nhà thầu chỉ áp dụng cho yêu cầu bảo trì." |
| `FORBIDDEN` | ✅ | "Bạn không có quyền thực hiện thao tác này." |
| `INVALID_STATUS_TRANSITION` | ✅ | "Không thể chuyển trạng thái trong bước này." |

**No missing codes — no feat(ui) commit needed.**

## 4. Generic code reuse notes (recommendations, not split this turn)

- `VALIDATION_ERROR` at TicketServiceImpl:342 covers "both assignees set" — a specific case surfacing as generic. Recommend `BOTH_ASSIGNEES_SET` code. **Deferred.**
- `INVALID_STATUS_TRANSITION` is specific (dedicated code), already handled well. No issue.

## 5. Fix plan

`hooks.ts`:
- `useAssignTicket`: add `successMessage: 'Đã phân công yêu cầu.'`
- `useUpdateTicketStatus`: add `successMessage: 'Đã cập nhật trạng thái.'`
(both already have `skipErrorToast: true`)

`TicketDetailPage.tsx`:
- Import `getVnErrorMessage` from `@gemek/ui`.
- Split shared `actionError` into `assignError` + `statusError` states (BUG FIX — status errors currently render in wrong panel).
- `handleAssign` catch: `getVnErrorMessage(err?.response?.data?.error)`.
- `handleStatusUpdate`: validation `'Select a status'` → `'Vui lòng chọn trạng thái.'`; catch: `getVnErrorMessage(err?.response?.data?.error)`; translate English UI labels.
- Show `{assignError && ...}` in assign form; `{statusError && ...}` in status form.
- Translate English UI strings in status form: heading, labels, button text, select options.

`TicketsPage.tsx`:
- Import `getVnErrorMessage` from `@gemek/ui`.
- `handleCreate` catch: `getVnErrorMessage(err?.response?.data?.error)` — remove raw `.message`.
- Redirect on success: UNCHANGED.
