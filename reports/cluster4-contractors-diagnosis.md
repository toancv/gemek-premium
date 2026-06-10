# Diagnosis — Cluster 4 ContractorsPage (#10 Create Contractor, #11 Edit Contractor)

## 1. Current success/error handling

Both forms share one `handleSubmit` (ContractorsPage.tsx lines 17–28):

| Aspect | Current state |
|--------|--------------|
| Try/catch | ✅ exists (line 23) |
| Success | Silent — `setModal(null)` only; no toast |
| Error message | `err?.response?.data?.message ?? 'Failed'` — raw server text + English fallback ❌ |
| Client-side validation | `'Company name is required'` — English ❌ |
| formError state | ✅ exists (line 10) |
| Inline error area | ✅ exists (line 105) |
| Global toast on error | Suppressed — both hooks have `meta: { skipErrorToast: true }` ✅ |
| Global toast on success | Not firing — neither hook has `meta.successMessage` ❌ |

**Changes needed (minimal — structure already correct):**
- `hooks.ts`: add `successMessage` to both hooks.
- `ContractorsPage.tsx`: fix error resolution + English validation string.

## 2. BE ErrorCodes per operation

### POST /api/contractors (create) — `ContractorServiceImpl.java`

| Code | Source | File:line |
|------|--------|-----------|
| `VALIDATION_ERROR` | `@Valid` on `CreateContractorRequest` | ContractorController:105 |
| `CONFLICT` | DB unique constraint via GlobalExceptionHandler (e.g. taxCode uniqueness if constrained) | GlobalExceptionHandler (DataIntegrityViolationException → CONFLICT) |

`createContractor` service method (lines 126–142) throws no explicit `AppException` — only DB-level constraints via GlobalExceptionHandler.

### PUT /api/contractors/{id} (update) — `ContractorServiceImpl.java`

| Code | Source | File:line |
|------|--------|-----------|
| `VALIDATION_ERROR` | `@Valid` on `UpdateContractorRequest` | ContractorController:137 |
| `NOT_FOUND` | contractor not found by id | ContractorServiceImpl:397–398 (via `loadContractorOrThrow`) |
| `CONFLICT` | DB unique constraint via GlobalExceptionHandler | GlobalExceptionHandler |

## 3. getVnErrorMessage coverage

| Code | Mapped | VN message |
|------|--------|------------|
| `VALIDATION_ERROR` | ✅ | "Dữ liệu không hợp lệ." |
| `NOT_FOUND` | ✅ | "Không tìm thấy dữ liệu." |
| `CONFLICT` | ✅ | "Thao tác không thể thực hiện do xung đột dữ liệu." |

**No missing codes → no feat(ui) commit needed.**

## 4. Fix plan

`hooks.ts`:
- `useCreateContractor`: add `successMessage: 'Thêm nhà thầu thành công'` (keep existing `skipErrorToast: true`).
- `useUpdateContractor`: add `successMessage: 'Cập nhật nhà thầu thành công'` (keep existing `skipErrorToast: true`).

`ContractorsPage.tsx`:
- Import `getVnErrorMessage` from `@gemek/ui`.
- Line 22 client-side guard: `'Company name is required'` → `'Tên công ty là bắt buộc.'`
- Line 27 catch: `err?.response?.data?.message ?? 'Failed'` → `getVnErrorMessage(err?.response?.data?.error)`.
- No structural changes needed — try/catch, formError state, and inline error area already exist.
