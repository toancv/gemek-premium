# (c) P1 — Admin Staff/User Management Page (UsersPage)

Frontend-only (admin app). Built over the EXISTING `/api/users` endpoints. No backend / schema / migration. No role-gate / authStore / ALLOWED_ROLES change (that is P3).

## What landed

**`@gemek/ui` (feat(ui) — committed first):**
- `enumLabels.ts`: new `UserRole` group — ADMIN→Quản trị viên, TECHNICIAN→Kỹ thuật viên, RESIDENT→Cư dân, BOARD_MEMBER→Thành viên ban quản trị. Per locked enum-map convention (DECISIONS 2026-06-10), NOT a local map in the page.
- `enumLabels.test.ts`: +3 assertions. Suite 66/66 green.

**admin app (feat(admin)):**
- `pages/UsersPage.tsx` (new): list (filters role/active/search, paged), create modal, edit modal, soft-deactivate, admin force reset-password modal. Typed `StaffUserItem` (no `any` for rows).
- `api/hooks.ts`: `del` helper + `useCreateUser` / `useUpdateUser` / `useDeactivateUser` / `useResetUserPassword`. All `meta.successMessage` (toast) + `skipErrorToast` (inline). All invalidate `['users']`.
- `App.tsx`: `/users` route, `RequireRole roles={['ADMIN']}` (ADMIN-only; BOARD_MEMBER excluded per ruling §4).
- `Layout.tsx`: nav item `/users` label «Tài khoản», `roles: ['ADMIN']`.
- `i18n/vi.ts`: `nav.users = 'Tài khoản'`.

## Scope decisions (matched to CTO ruling 2026-06-17)

- Create role select = ADMIN / TECHNICIAN / BOARD_MEMBER only (RESIDENT NOT offered — residents go through ResidentsPage). List **filter** offers all four (list shows every account).
- **ADMIN guardrail (§5):** explicit confirmation dialog before the mutation fires when creating an ADMIN, OR editing that PROMOTES to ADMIN (`role==='ADMIN' && (create || originalRole!=='ADMIN')`). Non-ADMIN and ADMIN-staying-ADMIN edits bypass. Confirm copy warns of full system access.
- Password: admin-set, generate button, VN policy hint shown. Reset-password field cleared after submit; value never echoed/logged/retained.
- Edit form does NOT expose email (the BE `UpdateUserRequest` has no email field — only fullName/phone/role/isActive). Email is set at create only. Documented, not a bug.

## Form feedback (canonical pattern, reused)

- Errors inline VN via `getVnErrorMessage(err?.response?.data?.error)`; PHONE_ALREADY_EXISTS anchors to the phone field, others to form-level. No raw server message, no English.
- Success via `meta.successMessage` toast (admin Toaster top-right, unchanged).
- No new `errorMessages` key needed — PHONE_ALREADY_EXISTS, EMAIL_ALREADY_EXISTS, SELF_OPERATION_NOT_ALLOWED, VALIDATION_ERROR, PASSWORD_POLICY_VIOLATION all already mapped in `@gemek/ui` errorMessages.

## Code-review pass (ECC /code-review, local mode)

- CRITICAL/HIGH: none. No secrets/injection; authStore/ALLOWED_ROLES/role-gate confirmed untouched (only route + nav added).
- Guardrail logic verified correct (create + promotion only).
- MEDIUM (FIXED this pass): deactivate originally relied on a server `SELF_OPERATION_NOT_ALLOWED` round-trip + `window.alert`. Now the deactivate button is HIDDEN on the logged-in admin's own row (`u.id !== currentUserId`, read-only `useAuthStore` access — no gate change). BE guard still in place as defense-in-depth; alert retained for any other deactivate error.
- LOW (accepted): deactivate uses `window.confirm`/`window.alert` (row action has no inline anchor) — consistent, VN copy. `err: any` in catch blocks matches the existing ResidentsPage pattern.

## Verification

- `@gemek/ui` vitest: **66/66 pass**.
- admin `tsc --noEmit` + `vite build`: **green** (587 modules).
- **NOT browser-verified** — CTO smoke step (port 80, `docker compose up -d --build nginx`).

## NOT done (later phases, do not start)

- P2 RequireRole audit on ALL admin pages (tickets/dashboard currently ungated).
- P3 admit TECHNICIAN to admin allowed-set (FORBIDDEN before P2).
- P4 audit_logs persistence (split). P5 docs (API-SPEC + user-guide).
