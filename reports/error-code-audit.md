# Backend Error Code Audit — 2026-06-09

## Total: 16 ErrorCode values (10 specific, 6 generic)

### Specific codes (precise meaning → exact VN message)

| Code | HTTP | Meaning | VN message |
|------|------|---------|-----------|
| `PHONE_ALREADY_EXISTS` | 409 | Phone number duplicate | Số điện thoại đã được sử dụng. |
| `EMAIL_ALREADY_EXISTS` | 409 | Email address duplicate | Email đã được sử dụng. |
| `INVALID_CREDENTIALS` | 401 | Wrong phone or password on login | Số điện thoại hoặc mật khẩu không đúng. |
| `INVALID_TOKEN` | 401 | JWT malformed/expired/revoked | Phiên đăng nhập không hợp lệ, vui lòng đăng nhập lại. |
| `ACCOUNT_INACTIVE` | 401 | User account deactivated | Tài khoản đã bị vô hiệu hóa. |
| `CONTRACTOR_ASSIGNMENT_NOT_ALLOWED` | 400 | Contractor on non-MAINTENANCE_REPAIR ticket | Phân công nhà thầu chỉ áp dụng cho yêu cầu bảo trì. |
| `INVALID_STATUS_TRANSITION` | 409 | Ticket/entity state machine violation | Không thể chuyển trạng thái trong bước này. |
| `HAS_ACTIVE_DEPENDENCIES` | 409 | Delete blocked by active children | Không thể xóa vì vẫn còn dữ liệu liên quan đang hoạt động. |
| `SELF_OPERATION_NOT_ALLOWED` | 400 | Action on own account (e.g. deactivate self) | Không thể thực hiện thao tác này trên tài khoản của bạn. |
| `RATE_LIMITED` | 429 | Too many requests | Bạn thao tác quá nhanh, vui lòng thử lại sau. |

### Generic codes (cover many situations → broad VN message)

| Code | HTTP | Current usage | VN message |
|------|------|--------------|-----------|
| `UNAUTHORIZED` | 401 | Missing/invalid JWT (not login credentials) | Bạn chưa đăng nhập hoặc phiên làm việc đã hết hạn. |
| `FORBIDDEN` | 403 | Wrong role or resource ownership | Bạn không có quyền thực hiện thao tác này. |
| `NOT_FOUND` | 404 | Any entity not found | Không tìm thấy dữ liệu. |
| `VALIDATION_ERROR` | 400 | Bean/custom validation failures | Dữ liệu không hợp lệ. |
| `CONFLICT` | 409 | Catch-all for state/uniqueness conflicts (see gaps below) | Thao tác không thể thực hiện do xung đột dữ liệu. |
| `INTERNAL_ERROR` | 500 | Uncaught errors, file upload failure | Có lỗi xảy ra, vui lòng thử lại. |

---

## Generic-Code Gaps — CONFLICT used where a distinct code exists or is warranted

These are NOT fixed this turn — logged for later BE patch decisions.

| # | File:line | Thrown message | Arguably should be | Priority |
|---|-----------|---------------|-------------------|---------|
| 1 | `VehicleServiceImpl.java:106` | "License plate '...' is already registered." | `LICENSE_PLATE_ALREADY_EXISTS` | HIGH — UI can show inline field error instead of generic toast |
| 2 | `VehicleServiceImpl.java:166` | same (update path) | `LICENSE_PLATE_ALREADY_EXISTS` | HIGH — same |
| 3 | `ParkingServiceImpl.java:121` | "Slot number '...' already exists." | `SLOT_NUMBER_ALREADY_EXISTS` | MEDIUM — admin-only form |
| 4 | `TicketServiceImpl.java:596` | "Ticket already rated." | `TICKET_ALREADY_RATED` | MEDIUM — resident action |
| 5 | `TicketServiceImpl.java:591` | "Ticket must be DONE to rate." | `INVALID_STATUS_TRANSITION` (already exists) | LOW — state UI should prevent this |
| 6 | `AnnouncementServiceImpl.java:198` | "Cannot edit a published announcement." | `INVALID_STATUS_TRANSITION` (already exists) | LOW — UI should disable edit when published |
| 7 | `ResidentServiceImpl.java:251` | "Resident has already moved out." | `RESIDENT_ALREADY_MOVED_OUT` | LOW — admin form already checks moveOutDate |

**Highest value fix (next BE patch):** Gap #1/#2 — license-plate dup on VehicleServiceImpl. FE VehiclesPage currently shows generic CONFLICT toast; with specific code it can show an inline field error on the license-plate field.

---

## Notes

- `CONFLICT` used as defense-in-depth fallback in `GlobalExceptionHandler` for `DataIntegrityViolationException` — intentional, do NOT change.
- `VALIDATION_ERROR` is always correct (bean-validation + custom guards); FE inline messages per-field come from the field-level spring errors (schema validation), not this code.
- All generic codes map to a sensible VN sentence in `getVnErrorMessage`; forms should never echo raw `message` field regardless.
