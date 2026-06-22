# Form-Feedback Standardization Survey
**Date:** 2026-06-09
**Standard:** Error → VN inline message by BE error CODE (never raw serverMsg). Success → toast.
**BE error shape:** `{ "error": "ERROR_CODE", "message": "...", "timestamp": "...", "path": "..." }`
**FE access:** `err?.response?.data?.error` = error code; `err?.response?.data?.message` = raw text (DO NOT USE as primary).

---

## Standard Definition

| Event | Required behavior |
|-------|------------------|
| Success | `toast()` with VN message (via `meta.successMessage` or explicit `toast()` in catch) |
| Server error (4xx/5xx) | Clear VN inline message below form/section, resolved from BE error code — NEVER echo raw serverMsg |
| Unknown code fallback | `"Có lỗi xảy ra, vui lòng thử lại."` — no English text, no raw BE string |
| Login redirect success | Navigate away — toast not required |

---

## ADMIN APP

### 1. ResidentsPage — Create Resident ✅ FIXED THIS TURN
- **Success:** toast("Tạo cư dân thành công") via `meta.successMessage` ✅
- **Error (was):** `err?.response?.data?.message` — raw serverMsg leaked phone number
- **Error (now):** maps `errorCode` field → `setPhoneError` / `setEmailError` by code; unknown → generic VN
- **Compliant:** YES (after this fix)

### 2. AmenitiesPage — Create Amenity ⚠️ DEVIATING
- **Success:** None — modal closes silently (no `successMessage` in hook meta)
- **Error:** `err?.response?.data?.message ?? 'Failed'` — raw serverMsg + English fallback
- **Fix needed:** Add `successMessage` to `useCreateAmenity`; replace error catch with code-based VN map

### 3. AmenitiesPage — Edit Amenity ⚠️ DEVIATING
- **Success:** None — silent
- **Error:** `err?.response?.data?.message ?? 'Failed'` — same as above
- **Fix needed:** Same as Create Amenity

### 4. AmenitiesPage — Approve Booking ⚠️ DEVIATING
- **Success:** toast("Đã duyệt đặt chỗ") ✅
- **Error:** No catch — global toast fires raw serverMsg (skipErrorToast not set)
- **Fix needed:** Add try/catch with VN inline error area

### 5. AmenitiesPage — Reject Booking ⚠️ DEVIATING
- **Success:** toast("Đã từ chối đặt chỗ") ✅
- **Error:** No catch — silent failure (mutateAsync in onClick, no error area in reject dialog)
- **Fix needed:** Add try/catch with VN inline error in reject dialog

### 6. AnnouncementsPage — Create Announcement ⚠️ DEVIATING
- **Success:** None — modal closes silently
- **Error:** `err?.response?.data?.message ?? 'Tạo thông báo thất bại'` — raw serverMsg primary
- **Fix needed:** Add `successMessage`; replace error with code-based VN

### 7. AnnouncementsPage — Publish Announcement ⚠️ DEVIATING
- **Success:** None — `skipSuccessToast: true`
- **Error:** None — `skipErrorToast: true` + no catch = completely silent
- **Fix needed:** Decide if publish needs confirmation feedback; add at minimum error inline

### 8. ApartmentsPage — Create Apartment ⚠️ DEVIATING
- **Success:** toast("Thêm căn hộ thành công") ✅
- **Error:** No try/catch — global toast fires (raw serverMsg); no inline error displayed
- **Fix needed:** Add try/catch; add formError state; show VN inline by code

### 9. ApartmentsPage — Edit Apartment ⚠️ DEVIATING
- **Success:** toast("Cập nhật căn hộ thành công") ✅
- **Error:** No try/catch — same as Create Apartment
- **Fix needed:** Same as Create Apartment

### 10. ContractorsPage — Create Contractor ⚠️ DEVIATING
- **Success:** None — modal closes silently
- **Error:** `err?.response?.data?.message ?? 'Failed'` — raw serverMsg + English fallback
- **Fix needed:** Add `successMessage`; VN error by code; fix English fallback

### 11. ContractorsPage — Edit Contractor ⚠️ DEVIATING
- **Success:** None — silent
- **Error:** `err?.response?.data?.message ?? 'Failed'` — same as Create
- **Fix needed:** Same as Create Contractor

### 12. LoginPage (admin) — Login ⚠️ DEVIATING
- **Success:** Navigate to /dashboard — acceptable ✅
- **Error:** `err?.response?.data?.message ?? 'Số điện thoại hoặc mật khẩu không đúng'` — raw serverMsg primary (VN fallback is good)
- **Fix needed:** Branch on error code (`INVALID_CREDENTIALS` → VN message); fallback generic VN; never echo raw

### 13. ParkingPage — Assign Parking Slot ⚠️ DEVIATING
- **Success:** None — modal closes silently
- **Error:** `err?.response?.data?.message ?? 'Failed'` — raw serverMsg + English fallback
- **Fix needed:** Add `successMessage`; VN error by code; fix English fallback

### 14. ParkingPage — End Parking Assignment ⚠️ DEVIATING
- **Success:** toast("Đã kết thúc phân công chỗ đậu xe") ✅
- **Error:** No catch — global toast fires raw serverMsg
- **Fix needed:** Add try/catch with VN inline or ensure global handler maps codes

### 15. TicketDetailPage (admin) — Assign Ticket ⚠️ DEVIATING
- **Success:** None — fields reset silently
- **Error:** `err?.response?.data?.message ?? 'Không thể phân công'` — raw serverMsg primary
- **Fix needed:** Add `successMessage`; VN error by code

### 16. TicketDetailPage (admin) — Update Ticket Status ⚠️ DEVIATING
- **Success:** None — silent
- **Error:** `err?.response?.data?.message ?? 'Failed to update status'` — raw serverMsg + English fallback
- **Fix needed:** Add `successMessage`; VN error by code; fix English fallback

### 17. TicketsPage — Create Ticket ⚠️ DEVIATING
- **Success:** None (redirects to ticket detail) — redirect is acceptable UX but no toast
- **Error:** `err?.response?.data?.message ?? 'Tạo ticket thất bại'` — raw serverMsg primary
- **Fix needed:** Add toast on success (or keep redirect as intent); VN error by code

### 18. VehiclesPage — Create Vehicle ⚠️ PARTIALLY COMPLIANT
- **Success:** None — modal closes silently
- **Error:** 409 → hardcoded VN "Biển số đã được đăng ký" ✅; else → `err?.response?.data?.message ?? 'Không thể tạo phương tiện'`
- **Fix needed:** Add `successMessage`; extend code mapping to all error codes

---

## RESIDENT APP

### 19. AmenitiesPage (resident) — Book Amenity ⚠️ DEVIATING
- **Success:** Inline checkmark panel auto-closes (not toast)
- **Error:** `err?.response?.data?.message ?? 'Failed to book'` — raw serverMsg + English fallback
- **Fix needed:** Replace inline success with toast; VN error by code; fix English fallback

### 20. AnnouncementsPage (resident) — Mark Read ⚠️ DEVIATING (low priority)
- **Success:** Optimistic UI — acceptable for fire-and-forget
- **Error:** No catch — silent failure
- **Fix needed:** Low priority; either add generic VN error or document intentional silent

### 21. LoginPage (resident) — Login ⚠️ DEVIATING
- **Success:** Navigate away ✅
- **Error:** `err?.response?.data?.message ?? 'Số điện thoại hoặc mật khẩu không đúng'` — raw serverMsg primary
- **Fix needed:** Same as admin LoginPage — branch on error code

### 22. MyBookingsPage — Cancel Booking ⚠️ DEVIATING
- **Success:** toast("Đã hủy đặt chỗ") ✅
- **Error:** No catch; global toast fires — code-mapping unknown
- **Fix needed:** Add try/catch; add VN inline error by code

### 23. MyTicketsPage — Create Ticket ⚠️ DEVIATING
- **Success:** None — modal closes silently
- **Error:** `err?.response?.data?.message ?? 'Tạo yêu cầu thất bại'` — raw serverMsg primary
- **Fix needed:** Add `successMessage`; VN error by code

### 24. MyVehiclesPage — Register Vehicle ⚠️ PARTIALLY COMPLIANT
- **Success:** None — modal closes silently
- **Error:** HTTP 409 → hardcoded VN "Biển số đã được đăng ký" ✅; else → raw serverMsg
- **Fix needed:** Add `successMessage`; extend code-based mapping to non-409 errors

### 25. ParkingPage (resident) — Log Guest Vehicle ⚠️ DEVIATING
- **Success:** None — modal closes silently
- **Error:** `err?.response?.data?.message ?? 'Failed'` — raw serverMsg + English fallback
- **Fix needed:** Add `successMessage`; VN error by code; fix English fallback

### 26. ProfilePage — Change Password ⚠️ DEVIATING
- **Success:** Inline green text "Password changed successfully" (English, not toast)
- **Error:** `err?.response?.data?.message ?? 'Failed to change password'` — raw serverMsg + English fallback
- **Fix needed:** Replace inline EN success with toast; VN error by code; fix English fallback

### 27. TicketDetailPage (resident) — Rate Ticket ⚠️ DEVIATING
- **Success:** None — form stays, no confirmation (waits for refetch)
- **Error:** `err?.response?.data?.message ?? 'Failed to submit rating'` — raw serverMsg + English fallback
- **Fix needed:** Add toast on success; VN error by code; fix English fallback

---

## Summary Table

| # | App | Form | Success OK? | Error OK? | Priority |
|---|-----|------|-------------|-----------|----------|
| 1 | admin | Create Resident | ✅ | ✅ (FIXED) | Done |
| 2 | admin | Create Amenity | ❌ no toast | ❌ raw+EN | Medium |
| 3 | admin | Edit Amenity | ❌ no toast | ❌ raw+EN | Medium |
| 4 | admin | Approve Booking | ✅ | ❌ silent | Medium |
| 5 | admin | Reject Booking | ✅ | ❌ silent | Medium |
| 6 | admin | Create Announcement | ❌ no toast | ❌ raw | Medium |
| 7 | admin | Publish Announcement | ❌ silent | ❌ silent | Low |
| 8 | admin | Create Apartment | ✅ | ❌ no inline | Medium |
| 9 | admin | Edit Apartment | ✅ | ❌ no inline | Medium |
| 10 | admin | Create Contractor | ❌ no toast | ❌ raw+EN | Medium |
| 11 | admin | Edit Contractor | ❌ no toast | ❌ raw+EN | Medium |
| 12 | admin | Login | ✅ (redirect) | ❌ raw serverMsg | Medium |
| 13 | admin | Assign Parking Slot | ❌ no toast | ❌ raw+EN | Medium |
| 14 | admin | End Parking Assignment | ✅ | ❌ global only | Low |
| 15 | admin | Assign Ticket | ❌ no toast | ❌ raw | Medium |
| 16 | admin | Update Ticket Status | ❌ no toast | ❌ raw+EN | Medium |
| 17 | admin | Create Ticket | ❌ no toast | ❌ raw | Medium |
| 18 | admin | Create Vehicle | ❌ no toast | ⚠️ partial | Medium |
| 19 | resident | Book Amenity | ❌ not toast | ❌ raw+EN | Medium |
| 20 | resident | Mark Announcement Read | ✅ (fire-forget) | ❌ silent | Low |
| 21 | resident | Login | ✅ (redirect) | ❌ raw | Medium |
| 22 | resident | Cancel Booking | ✅ | ❌ no inline | Low |
| 23 | resident | Create Ticket | ❌ no toast | ❌ raw | Medium |
| 24 | resident | Register Vehicle | ❌ no toast | ⚠️ partial | Medium |
| 25 | resident | Log Guest Vehicle | ❌ no toast | ❌ raw+EN | Medium |
| 26 | resident | Change Password | ❌ EN inline | ❌ raw+EN | High |
| 27 | resident | Rate Ticket | ❌ silent | ❌ raw+EN | Medium |

**Total forms:** 27
**Compliant:** 1 (resident form, fixed this turn)
**Deviating:** 26
**Systemic issues:**
1. Raw `err?.response?.data?.message` used as primary error source in 22/26 deviating forms
2. Missing success toast in 16/26 deviating forms
3. English hardcoded fallback strings in 10/26 deviating forms
4. Silent error (no handler at all) in 4/26 deviating forms

**Recommended fix approach (follow-up turn):**
1. Create shared `getVnErrorMessage(errorCode: string): string` util in `@gemek/ui` with a code→VN map + generic fallback
2. In each form catch: `const msg = getVnErrorMessage(err?.response?.data?.error); setFormError(msg);`
3. Add `successMessage` to all hooks lacking it
4. Never touch `err?.response?.data?.message` in FE catch blocks
