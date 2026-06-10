# Diagnosis — Cluster 5 Admin ParkingPage (#13 Assign, #14 End Assignment)

## 1. Current success/error handling

### #13 — Assign Parking Slot (handleAssign, lines 59–78)

| Aspect | Current state |
|--------|--------------|
| Try/catch | ✅ exists |
| Success | Silent — `closeAssign()` only; no toast |
| Error message | `err?.response?.data?.message ?? 'Failed'` — raw server text + English fallback ❌ |
| Client-side validation | `'Vehicle, Apartment and Start Date are required'` — English ❌ |
| formError state | ✅ exists (line 18) |
| Inline error area | ✅ exists (line 203) |
| Hook (`useCreateParkingAssignment`) meta | `meta: { skipErrorToast: true }` — no successMessage ❌ |

### #14 — End Parking Assignment (table row button, line 130)

| Aspect | Current state |
|--------|--------------|
| Try/catch | ❌ — `.mutate()` fire-and-forget, no catch |
| Success toast | ✅ working — `meta: { successMessage: 'Đã kết thúc phân công chỗ đậu xe' }` |
| Error | Global raw-serverMsg toast fires — no `skipErrorToast: true`, no inline state ❌ |
| Confirm text | `window.confirm('End this assignment?')` — English ❌ |

## 2. BE ErrorCodes per operation

### POST /api/parking/slots/{id}/assign — `ParkingServiceImpl.assignSlot()`

| Code | Source | File:line |
|------|--------|-----------|
| `NOT_FOUND` | slot not found | ParkingServiceImpl:392–394 (via `loadSlot`) |
| `CONFLICT` | slot status not AVAILABLE | ParkingServiceImpl:219–221 |
| `CONFLICT` | slot already has active assignment | ParkingServiceImpl:224–226 |
| `NOT_FOUND` | vehicle not found | ParkingServiceImpl:228–230 |
| `NOT_FOUND` | apartment not found | ParkingServiceImpl:232–234 |
| `VALIDATION_ERROR` | `@Valid` on `CreateAssignmentRequest` (inferred — controller pattern consistent with module) | ParkingController |

### POST /api/parking/slots/{id}/unassign — `ParkingServiceImpl.unassignSlot()`

| Code | Source | File:line |
|------|--------|-----------|
| `NOT_FOUND` | slot not found | ParkingServiceImpl:392–394 (via `loadSlot`) |
| `NOT_FOUND` | no active assignment found for slot | ParkingServiceImpl:265–267 |

## 3. getVnErrorMessage coverage

| Code | Mapped | VN message |
|------|--------|------------|
| `NOT_FOUND` | ✅ | "Không tìm thấy dữ liệu." |
| `CONFLICT` | ✅ | "Thao tác không thể thực hiện do xung đột dữ liệu." |
| `VALIDATION_ERROR` | ✅ | "Dữ liệu không hợp lệ." |

**No missing codes → no feat(ui) commit needed.**

## 4. CONFLICT reuse note (recommendation)

`CONFLICT` covers two distinct assign scenarios: "slot not AVAILABLE" and "slot already has active assignment." Recommend `SLOT_NOT_AVAILABLE` / `SLOT_ALREADY_ASSIGNED` for more specific VN messages. **Deferred — CTO decision.**

## 5. Fix plan

`hooks.ts`:
- `useCreateParkingAssignment`: add `successMessage: 'Đã phân công chỗ đậu xe.'`
- `useEndParkingAssignment`: add `skipErrorToast: true` (keep existing `successMessage` untouched)

`ParkingPage.tsx`:
- Import `getVnErrorMessage` from `@gemek/ui`.
- Add `endError` state (string) — shown above slots table.
- `handleAssign`: fix validation message to VN; fix catch to `getVnErrorMessage(err?.response?.data?.error)`.
- `handleEndAssignment(slotId)` new async function: VN confirm text, `mutateAsync`, catch → `setEndError(getVnErrorMessage(...))`.
- Replace inline `window.confirm` + `.mutate()` on line 130 with `handleEndAssignment(s.id)` call.
- Show `endError` above the slots table (cleared on new attempt).
