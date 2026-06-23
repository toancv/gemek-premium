/**
 * Maps a backend ErrorCode string to a Vietnamese user-facing message.
 *
 * <p>Rules:
 * - Read `err?.response?.data?.error` (the code), pass it here.
 * - Never pass `err?.response?.data?.message` (raw server text — may be English / leak data).
 * - Unknown or undefined codes return the generic fallback.
 */

const ERROR_MESSAGES: Record<string, string> = {
  // Auth
  INVALID_CREDENTIALS: 'Số điện thoại hoặc mật khẩu không đúng.',
  INVALID_TOKEN: 'Phiên đăng nhập không hợp lệ, vui lòng đăng nhập lại.',
  UNAUTHORIZED: 'Bạn chưa đăng nhập hoặc phiên làm việc đã hết hạn.',
  ACCOUNT_INACTIVE: 'Tài khoản đã bị vô hiệu hóa.',

  // Authorization
  FORBIDDEN: 'Bạn không có quyền thực hiện thao tác này.',
  WRONG_PORTAL: 'Tài khoản không có quyền truy cập cổng này.',

  // Uniqueness
  PHONE_ALREADY_EXISTS: 'Số điện thoại đã được sử dụng.',
  EMAIL_ALREADY_EXISTS: 'Email đã được sử dụng.',

  // Validation
  VALIDATION_ERROR: 'Dữ liệu không hợp lệ.',
  CONTRACTOR_ASSIGNMENT_NOT_ALLOWED: 'Phân công nhà thầu chỉ áp dụng cho yêu cầu bảo trì.',
  SELF_OPERATION_NOT_ALLOWED: 'Không thể thực hiện thao tác này trên tài khoản của bạn.',

  // State / conflict
  NOT_FOUND: 'Không tìm thấy dữ liệu.',
  CONFLICT: 'Thao tác không thể thực hiện do xung đột dữ liệu.',
  INVALID_STATUS_TRANSITION: 'Không thể chuyển trạng thái trong bước này.',
  HAS_ACTIVE_DEPENDENCIES: 'Không thể xóa vì vẫn còn dữ liệu liên quan đang hoạt động.',

  // Vehicle / parking uniqueness
  LICENSE_PLATE_ALREADY_EXISTS: 'Biển số xe đã được đăng ký.',
  SLOT_NUMBER_ALREADY_EXISTS: 'Số ô đỗ xe đã tồn tại.',

  // Ticket lifecycle
  TICKET_ALREADY_RATED: 'Yêu cầu này đã được đánh giá.',
  RESIDENT_ALREADY_MOVED_OUT: 'Cư dân này đã rời khỏi căn hộ.',

  // Place-resident (move-in / return / multi-residency)
  ALREADY_ACTIVE_IN_APARTMENT: 'Cư dân này đang ở căn hộ này rồi.',
  REUSE_CONFIRMATION_REQUIRED: 'Số điện thoại đã thuộc về một cư dân khác — vui lòng xác nhận dùng lại hồ sơ.',

  // Password change
  WRONG_CURRENT_PASSWORD: 'Mật khẩu hiện tại không đúng.',
  PASSWORD_POLICY_VIOLATION: 'Mật khẩu mới phải có tối thiểu 8 ký tự, gồm chữ hoa, chữ thường, số và ký tự đặc biệt.',

  // Amenity
  AMENITY_NAME_EXISTS: 'Tên tiện ích đã tồn tại.',

  // Booking lifecycle
  BOOKING_NOT_PENDING: 'Chỉ có thể duyệt hoặc từ chối đặt chỗ đang chờ xử lý.',

  // Announcement rich content (Markdown)
  ANNOUNCEMENT_CONTENT_TOO_LONG: 'Nội dung quá dài (tối đa 20.000 ký tự).',
  ANNOUNCEMENT_CONTENT_HTML_NOT_ALLOWED: 'Nội dung không được chứa mã HTML. Vui lòng dùng định dạng Markdown.',

  // Rate / server
  RATE_LIMITED: 'Bạn thao tác quá nhanh, vui lòng thử lại sau.',
  INTERNAL_ERROR: 'Có lỗi xảy ra, vui lòng thử lại.',
};

const FALLBACK = 'Có lỗi xảy ra, vui lòng thử lại.';

/**
 * Returns a Vietnamese error message for the given backend ErrorCode string.
 *
 * @param errorCode The `error` field from the API error response body.
 * @return A Vietnamese string. Never empty, never English, never raw server text.
 */
export function getVnErrorMessage(errorCode?: string): string {
  if (!errorCode) return FALLBACK;
  return ERROR_MESSAGES[errorCode] ?? FALLBACK;
}
