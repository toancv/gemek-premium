# FE Add-Resident Form Survey — 2026-06-08

## Current state (ResidentsPage.tsx)

OBSOLETE "select existing user" path is still live:
- `selectedUserId` state variable present
- `loadUserOptions` callback fetches `GET /api/users` and maps to SearchableSelect options
- Form renders a SearchableSelect labelled "Người dùng" bound to `selectedUserId`
- `handleCreate` submits `{ userId: selectedUserId, apartmentId, type, moveInDate, isPrimaryContact }`
- The `userId` field was REMOVED from the backend DTO in the 2026-06-05 breaking change — this form is broken

Missing entirely:
- `fullName`, `email`, `password`, `phone` text inputs
- "Generate password" button
- `dateOfBirth` field (optional per DTO)

Apartment dropdown: already async SearchableSelect (`loadApartmentOptions` → GET /api/apartments) — keep unchanged.

## Backend contract (CreateResidentRequest.java)

Required fields:
| Field        | Constraint                    |
|--------------|-------------------------------|
| fullName     | @NotBlank                     |
| email        | @NotBlank + @Email            |
| password     | @NotBlank only (no @Pattern)  |
| apartmentId  | @NotNull UUID                 |
| type         | @NotNull (OWNER / TENANT)     |
| moveInDate   | @NotNull LocalDate            |

Optional fields: `phone`, `dateOfBirth` (LocalDate), `isPrimaryContact` (boolean, default false), `notes`

## Password policy

`CreateResidentRequest.password` is validated only `@NotBlank` — no complexity rule.
BUT `CreateUserRequest` and `ResetPasswordRequest` both enforce:
  `^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[^a-zA-Z0-9]).{8,}$`
(min 8 chars, upper + lower + digit + special character)

Generate-password will satisfy this stricter pattern for forward compatibility and consistency.

## Implementation plan

1. Remove `selectedUserId`, `userError`, `loadUserOptions` from ResidentsPage
2. Add controlled state for: `fullName`, `email`, `password`, `phone` (+ validation errors)
3. Add "Tạo mật khẩu" button beside password field; generated value fills input AND is displayed in a copyable box so admin can share it with the resident
4. Update `handleCreate` to submit: `{ fullName, email, password, phone, apartmentId, type, moveInDate, isPrimaryContact }`
5. Keep apartment SearchableSelect unchanged
6. Error map: 409 → "Email đã được sử dụng.", 404 → "Căn hộ không tồn tại."

No ambiguity. Proceeding to implement.
