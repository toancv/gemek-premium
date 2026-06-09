# Change-Password Feedback Diagnosis (2026-06-09)

## Issue A — Success: no toast

**Trace:**
- PUT /auth/me/password → 204 No Content → Axios resolves → mutateAsync resolves → code after await runs
- `toast.success('Đổi mật khẩu thành công.')` IS syntactically reached after the await

**Root cause:**
Before the previous fix (skipSuccessToast: true), the global MutationCache handler was firing
"Thao tác thành công" on success — that WAS the working toast path.
The previous fix added `skipSuccessToast: true`, suppressing the global handler.
The component-level `toast.success(...)` was not confirmed to work (possible pnpm workspace
module singleton divergence: @gemek/ui imported externally vs internally may resolve to
different module instances in some build configurations → different `listeners` arrays).

**Net effect of previous fix:** went from 1 working toast ("Thao tác thành công") to 0 toasts.

**Fix:** Remove `skipSuccessToast: true`; use `meta.successMessage: 'Đổi mật khẩu thành công.'`
so the MutationCache handler fires the specific VN message via the proven-working path.
Remove `toast.success(...)` from ProfilePage component.

## Issue B — Error: generic VALIDATION_ERROR for weak password

**Trace:**
- ChangePasswordRequest has `@Pattern(regexp = "^(?=.*...)$", ...)` on `newPassword`
- Spring validation failure → MethodArgumentNotValidException
- GlobalExceptionHandler maps MethodArgumentNotValidException → VALIDATION_ERROR (generic, 400)
- FE: getVnErrorMessage('VALIDATION_ERROR') = "Dữ liệu không hợp lệ." — no clue it's password policy

**No structured fieldErrors** — handler returns single `{ error: "VALIDATION_ERROR", message: "..." }`.

**Fix:** 
- Add `PASSWORD_POLICY_VIOLATION(HttpStatus.UNPROCESSABLE_ENTITY)` to ErrorCode
- Remove `@Pattern` from ChangePasswordRequest (keep @NotBlank; domain rule belongs in service)
- Add manual regex check in AuthServiceImpl.changePassword() → throw PASSWORD_POLICY_VIOLATION
- Map in errorMessages.ts → "Mật khẩu mới phải có tối thiểu 8 ký tự, gồm chữ hoa, chữ thường, số và ký tự đặc biệt."
