# Diagnosis — Cluster 7 Admin VehiclesPage (#18 Create Vehicle)

## 1. Current success/error handling

### #18 — Create Vehicle (handleCreate, VehiclesPage:55–84)

| Aspect | Current state |
|--------|--------------|
| Client validation | VN ✅ (`'Vui lòng chọn cư dân'`, `'Biển số và loại phương tiện là bắt buộc'`) |
| Try/catch | ✅ exists |
| Success | Silent — modal closes + form resets; no toast ❌ |
| Error — 409 path | `if (err?.response?.status === 409)` → hardcodes `'Biển số đã được đăng ký'` — HTTP-status detection, not code-based ❌ |
| Error — other path | `err?.response?.data?.message ?? 'Không thể tạo phương tiện'` — raw server text as primary ❌ |
| Error display | `{formError && ...}` at line 208 ✅ |
| Hook (`useCreateVehicle`) meta | `meta: { skipErrorToast: true }` — no successMessage ❌ |

Detection method: the 409-branch checks `err?.response?.status === 409` (HTTP status code), not `err?.response?.data?.error` (error code string). If the BE ever changes the response status or adds another 409-returning operation, the mapping silently breaks.

## 2. BE ErrorCodes for POST /api/vehicles — `VehicleServiceImpl.createVehicle()`

| Code | Source | Java:line |
|------|--------|-----------|
| `NOT_FOUND` | resident not found | VehicleServiceImpl:92 |
| `NOT_FOUND` | apartment not found | VehicleServiceImpl:96 |
| `LICENSE_PLATE_ALREADY_EXISTS` | plate already registered | VehicleServiceImpl:106 |
| `VALIDATION_ERROR` | `@Valid @NotBlank` on licensePlate in `CreateVehicleRequest` | CreateVehicleRequest:41 (via Spring controller) |
| `FORBIDDEN` | resident caller not active in given apartment | VehicleServiceImpl:263 (resident path only — admin unaffected) |

## 3. getVnErrorMessage coverage

| Code | Mapped | VN message |
|------|--------|------------|
| `NOT_FOUND` | ✅ | "Không tìm thấy dữ liệu." |
| `LICENSE_PLATE_ALREADY_EXISTS` | ✅ | "Biển số xe đã được đăng ký." |
| `VALIDATION_ERROR` | ✅ | "Dữ liệu không hợp lệ." |
| `FORBIDDEN` | ✅ | "Bạn không có quyền thực hiện thao tác này." |

**No missing codes — no feat(ui) commit needed.**

Note: existing `LICENSE_PLATE_ALREADY_EXISTS` mapping is "Biển số xe đã được đăng ký." (includes "xe"); hardcoded string was "Biển số đã được đăng ký" (omits "xe"). Mapping is natural VN and preferred — keeping as-is.

## 4. Fix plan

`hooks.ts`:
- `useCreateVehicle`: add `successMessage: 'Đã thêm phương tiện.'` (matches "phương tiện" term used in labels).

`VehiclesPage.tsx`:
- Import `getVnErrorMessage` from `@gemek/ui`.
- Replace entire catch block (HTTP-status branch + raw-message fallback) with single `setFormError(getVnErrorMessage(err?.response?.data?.error))`.
