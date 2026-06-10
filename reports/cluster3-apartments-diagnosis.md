# Diagnosis — Cluster 3 ApartmentsPage (#8 Create Apartment, #9 Edit Apartment)

## 1. Current error handling

| Form | Hook meta | onSubmit try/catch | Error displayed |
|------|-----------|-------------------|-----------------|
| #8 Create | `meta: { successMessage: 'Thêm căn hộ thành công' }` — NO skipErrorToast | NONE | Global MutationCache toast fires raw `.message` (English server text) |
| #9 Edit | `meta: { successMessage: 'Cập nhật căn hộ thành công' }` — NO skipErrorToast | NONE | Same — global toast fires raw `.message` |

`ApartmentsPage.tsx` line 15: one `blockError` state exists (for client-side "select a block" validation) — no server error state for either form.

## 2. BE ErrorCodes per operation

### POST /api/apartments (create) — `ApartmentServiceImpl.java`

| Code | Source | File:line |
|------|--------|-----------|
| `VALIDATION_ERROR` | `@Valid` on `CreateApartmentRequest` | ApartmentController:103 |
| `NOT_FOUND` | block not found by `request.blockId()` | ApartmentServiceImpl:127–128 |
| `CONFLICT` | unit number already exists in block | ApartmentServiceImpl:132–133 |

### PUT /api/apartments/{id} (update) — `ApartmentServiceImpl.java`

| Code | Source | File:line |
|------|--------|-----------|
| `VALIDATION_ERROR` | `@Valid` on `UpdateApartmentRequest` | ApartmentController:142 |
| `NOT_FOUND` | apartment not found by id | ApartmentServiceImpl:204–205 |
| `CONFLICT` | unit number already exists in block (excluding self) | ApartmentServiceImpl:208–210 |

## 3. getVnErrorMessage coverage

| Code | Mapped | VN message |
|------|--------|------------|
| `VALIDATION_ERROR` | ✅ | "Dữ liệu không hợp lệ." |
| `NOT_FOUND` | ✅ | "Không tìm thấy dữ liệu." |
| `CONFLICT` | ✅ | "Thao tác không thể thực hiện do xung đột dữ liệu." |

**No missing codes → no feat(ui) commit needed.**

## 4. CONFLICT reuse note (recommendation, not a blocker)

`CONFLICT` is used for:
- `createApartment`: dup unit number in block
- `updateApartment`: dup unit number in block
- `deleteApartment`: apartment has active residents (separate context)

Recommendation: split `createApartment`/`updateApartment` dup-unit case to `UNIT_NUMBER_ALREADY_EXISTS` → "Số căn hộ đã tồn tại trong block này." for a more precise VN message. **Deferred — CTO decision required.**

For now, `CONFLICT` → "Thao tác không thể thực hiện do xung đột dữ liệu." is acceptable and not misleading.

## 5. Fix plan

`hooks.ts`:
- `useCreateApartment`: add `skipErrorToast: true` to existing meta.
- `useUpdateApartment`: add `skipErrorToast: true` to existing meta.

`ApartmentsPage.tsx`:
- Import `getVnErrorMessage` from `@gemek/ui`.
- Add `createError` state (string).
- Add `editError` state (string).
- Create form `onSubmit`: wrap `mutateAsync` in try/catch; on catch → `setCreateError(getVnErrorMessage(err?.response?.data?.error))`; clear on open/cancel.
- Edit form `onSubmit`: same pattern with `editError`.
- Show inline error below each form (same `<p className="text-sm text-red-600 bg-red-50 px-3 py-2 rounded">` pattern).
- Success paths (`meta.successMessage`) unchanged.
