# Add-Resident 3-Fixes Survey — 2026-06-08

## 1. Toast mechanism

`main.tsx`: `QueryClient` uses `new MutationCache(mutationCacheHandlers)` from `@gemek/ui`.
`mutationCacheHandlers` fires a success toast when `mutation.options.meta?.successMessage` is set,
and suppresses error toast when `meta?.skipErrorToast` is true.

`useCreateResident` currently: `meta: { skipErrorToast: true }` — no `successMessage`.
Fix: add `successMessage: 'Tạo cư dân thành công'` alongside the existing `skipErrorToast: true`.

Pattern confirmed from `useCreateApartment`: `meta: { successMessage: 'Thêm căn hộ thành công' }`.

## 2. Current form fields (ResidentsPage.tsx, commit 448bc15)

| Field         | State var     | Required (FE) | In payload |
|---------------|---------------|---------------|------------|
| fullName      | controlled    | yes           | yes        |
| email         | controlled    | yes           | yes        |
| password      | controlled    | yes           | yes        |
| phone         | controlled    | NO            | yes (if set) |
| dateOfBirth   | MISSING       | —             | MISSING    |
| apartmentId   | SearchableSelect | yes        | yes        |
| type          | select        | yes (native)  | yes        |
| moveInDate    | date input    | yes           | yes        |
| isPrimaryContact | select     | yes (native)  | yes        |

## 3. CreateResidentRequest constraints (post-last-fix)

| Field       | Current constraints                      | Required change       |
|-------------|------------------------------------------|-----------------------|
| fullName    | @NotBlank                               | none                  |
| email       | @NotBlank + @Email                      | none                  |
| password    | @NotBlank + @Pattern                    | none                  |
| phone       | NONE (optional)                         | add @NotBlank         |
| dateOfBirth | NONE (optional LocalDate)               | add @NotNull          |
| apartmentId | @NotNull                                | none                  |
| type        | @NotNull                                | none                  |
| moveInDate  | @NotNull                                | none                  |
| isPrimaryContact | boolean primitive                  | none                  |
| notes       | NONE (optional)                         | stays optional        |

## 4. Tests to update

All calls to `createResident()` helper and direct-map payloads lack phone/dateOfBirth.
After BE requires them, these tests would fail. Fix: add defaults to helper + all direct-map payloads.
New tests needed: missing phone → 400, missing dateOfBirth → 400.
