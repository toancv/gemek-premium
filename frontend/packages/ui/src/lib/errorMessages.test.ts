import { describe, it, expect } from 'vitest';
import { getVnErrorMessage } from './errorMessages';

const FALLBACK = 'Có lỗi xảy ra, vui lòng thử lại.';

describe('getVnErrorMessage', () => {
  it('maps PHONE_ALREADY_EXISTS', () => {
    expect(getVnErrorMessage('PHONE_ALREADY_EXISTS')).toBe('Số điện thoại đã được sử dụng.');
  });

  it('maps EMAIL_ALREADY_EXISTS', () => {
    expect(getVnErrorMessage('EMAIL_ALREADY_EXISTS')).toBe('Email đã được sử dụng.');
  });

  it('maps INVALID_CREDENTIALS', () => {
    expect(getVnErrorMessage('INVALID_CREDENTIALS')).toBe('Số điện thoại hoặc mật khẩu không đúng.');
  });

  it('maps INVALID_TOKEN', () => {
    expect(getVnErrorMessage('INVALID_TOKEN')).toBe('Phiên đăng nhập không hợp lệ, vui lòng đăng nhập lại.');
  });

  it('maps UNAUTHORIZED', () => {
    expect(getVnErrorMessage('UNAUTHORIZED')).toBe('Bạn chưa đăng nhập hoặc phiên làm việc đã hết hạn.');
  });

  it('maps ACCOUNT_INACTIVE', () => {
    expect(getVnErrorMessage('ACCOUNT_INACTIVE')).toBe('Tài khoản đã bị vô hiệu hóa.');
  });

  it('maps FORBIDDEN', () => {
    expect(getVnErrorMessage('FORBIDDEN')).toBe('Bạn không có quyền thực hiện thao tác này.');
  });

  it('maps WRONG_PORTAL', () => {
    expect(getVnErrorMessage('WRONG_PORTAL')).toBe('Tài khoản không có quyền truy cập cổng này.');
  });

  it('maps NOT_FOUND', () => {
    expect(getVnErrorMessage('NOT_FOUND')).toBe('Không tìm thấy dữ liệu.');
  });

  it('maps VALIDATION_ERROR', () => {
    expect(getVnErrorMessage('VALIDATION_ERROR')).toBe('Dữ liệu không hợp lệ.');
  });

  it('maps CONFLICT', () => {
    expect(getVnErrorMessage('CONFLICT')).toBe('Thao tác không thể thực hiện do xung đột dữ liệu.');
  });

  it('maps INVALID_STATUS_TRANSITION', () => {
    expect(getVnErrorMessage('INVALID_STATUS_TRANSITION')).toBe('Không thể chuyển trạng thái trong bước này.');
  });

  it('maps HAS_ACTIVE_DEPENDENCIES', () => {
    expect(getVnErrorMessage('HAS_ACTIVE_DEPENDENCIES')).toBe('Không thể xóa vì vẫn còn dữ liệu liên quan đang hoạt động.');
  });

  it('maps CONTRACTOR_ASSIGNMENT_NOT_ALLOWED', () => {
    expect(getVnErrorMessage('CONTRACTOR_ASSIGNMENT_NOT_ALLOWED')).toBe('Phân công nhà thầu chỉ áp dụng cho yêu cầu bảo trì.');
  });

  it('maps SELF_OPERATION_NOT_ALLOWED', () => {
    expect(getVnErrorMessage('SELF_OPERATION_NOT_ALLOWED')).toBe('Không thể thực hiện thao tác này trên tài khoản của bạn.');
  });

  it('maps LICENSE_PLATE_ALREADY_EXISTS', () => {
    expect(getVnErrorMessage('LICENSE_PLATE_ALREADY_EXISTS')).toBe('Biển số xe đã được đăng ký.');
  });

  it('maps SLOT_NUMBER_ALREADY_EXISTS', () => {
    expect(getVnErrorMessage('SLOT_NUMBER_ALREADY_EXISTS')).toBe('Số ô đỗ xe đã tồn tại.');
  });

  it('maps TICKET_ALREADY_RATED', () => {
    expect(getVnErrorMessage('TICKET_ALREADY_RATED')).toBe('Yêu cầu này đã được đánh giá.');
  });

  it('maps RESIDENT_ALREADY_MOVED_OUT', () => {
    expect(getVnErrorMessage('RESIDENT_ALREADY_MOVED_OUT')).toBe('Cư dân này đã rời khỏi căn hộ.');
  });

  it('maps WRONG_CURRENT_PASSWORD', () => {
    expect(getVnErrorMessage('WRONG_CURRENT_PASSWORD')).toBe('Mật khẩu hiện tại không đúng.');
  });

  it('maps PASSWORD_POLICY_VIOLATION', () => {
    expect(getVnErrorMessage('PASSWORD_POLICY_VIOLATION')).toBe(
      'Mật khẩu mới phải có tối thiểu 8 ký tự, gồm chữ hoa, chữ thường, số và ký tự đặc biệt.'
    );
  });

  it('maps RATE_LIMITED', () => {
    expect(getVnErrorMessage('RATE_LIMITED')).toBe('Bạn thao tác quá nhanh, vui lòng thử lại sau.');
  });

  it('maps INTERNAL_ERROR', () => {
    expect(getVnErrorMessage('INTERNAL_ERROR')).toBe('Có lỗi xảy ra, vui lòng thử lại.');
  });

  it('returns fallback for unknown code', () => {
    expect(getVnErrorMessage('SOME_FUTURE_CODE')).toBe(FALLBACK);
  });

  it('returns fallback for undefined', () => {
    expect(getVnErrorMessage(undefined)).toBe(FALLBACK);
  });

  it('returns fallback for empty string', () => {
    expect(getVnErrorMessage('')).toBe(FALLBACK);
  });

  it('AMENITY_NAME_EXISTS returns VN message about duplicate name', () => {
    expect(getVnErrorMessage('AMENITY_NAME_EXISTS')).toBe('Tên tiện ích đã tồn tại.');
  });

  it('BOOKING_NOT_PENDING returns VN message about non-pending booking', () => {
    expect(getVnErrorMessage('BOOKING_NOT_PENDING')).toBe('Chỉ có thể duyệt hoặc từ chối đặt chỗ đang chờ xử lý.');
  });

  it('ANNOUNCEMENT_MEDIA_TYPE_NOT_ALLOWED returns VN message about allowed image formats', () => {
    expect(getVnErrorMessage('ANNOUNCEMENT_MEDIA_TYPE_NOT_ALLOWED')).toBe('Định dạng ảnh không hợp lệ. Chỉ chấp nhận JPG, PNG hoặc WebP.');
  });

  it('ANNOUNCEMENT_MEDIA_LIMIT_EXCEEDED returns VN message about the image caps', () => {
    expect(getVnErrorMessage('ANNOUNCEMENT_MEDIA_LIMIT_EXCEEDED')).toBe('Vượt quá giới hạn ảnh (tối đa 5 ảnh, tổng dung lượng tối đa 50MB).');
  });

  it('ANNOUNCEMENT_NOT_DRAFT returns VN message about draft-only media changes', () => {
    expect(getVnErrorMessage('ANNOUNCEMENT_NOT_DRAFT')).toBe('Chỉ có thể thay đổi ảnh khi thông báo còn ở trạng thái nháp.');
  });

  it('ANNOUNCEMENT_ATTACHMENT_* codes return VN messages about document attachments', () => {
    expect(getVnErrorMessage('ANNOUNCEMENT_ATTACHMENT_TYPE_NOT_ALLOWED')).toBe('Định dạng tệp không hợp lệ. Chỉ chấp nhận PDF, Word, Excel, PowerPoint hoặc TXT.');
    expect(getVnErrorMessage('ANNOUNCEMENT_ATTACHMENT_TOO_LARGE')).toBe('Tệp đính kèm quá lớn (tối đa 10MB mỗi tệp).');
    expect(getVnErrorMessage('ANNOUNCEMENT_ATTACHMENT_LIMIT_EXCEEDED')).toBe('Vượt quá giới hạn tệp đính kèm (tối đa 5 tệp, tổng dung lượng tối đa 50MB).');
  });

  it('never returns empty string for any known code', () => {
    const knownCodes = [
      'PHONE_ALREADY_EXISTS', 'EMAIL_ALREADY_EXISTS', 'INVALID_CREDENTIALS',
      'INVALID_TOKEN', 'UNAUTHORIZED', 'ACCOUNT_INACTIVE', 'FORBIDDEN',
      'NOT_FOUND', 'VALIDATION_ERROR', 'CONFLICT', 'INVALID_STATUS_TRANSITION',
      'HAS_ACTIVE_DEPENDENCIES', 'CONTRACTOR_ASSIGNMENT_NOT_ALLOWED',
      'SELF_OPERATION_NOT_ALLOWED', 'RATE_LIMITED', 'INTERNAL_ERROR',
      'LICENSE_PLATE_ALREADY_EXISTS', 'SLOT_NUMBER_ALREADY_EXISTS',
      'TICKET_ALREADY_RATED', 'RESIDENT_ALREADY_MOVED_OUT', 'WRONG_CURRENT_PASSWORD',
      'PASSWORD_POLICY_VIOLATION', 'AMENITY_NAME_EXISTS', 'BOOKING_NOT_PENDING',
      'ANNOUNCEMENT_MEDIA_TYPE_NOT_ALLOWED', 'ANNOUNCEMENT_MEDIA_LIMIT_EXCEEDED', 'ANNOUNCEMENT_NOT_DRAFT',
      'ANNOUNCEMENT_ATTACHMENT_TYPE_NOT_ALLOWED', 'ANNOUNCEMENT_ATTACHMENT_TOO_LARGE', 'ANNOUNCEMENT_ATTACHMENT_LIMIT_EXCEEDED',
      'PAYLOAD_TOO_LARGE',
    ];
    for (const code of knownCodes) {
      const msg = getVnErrorMessage(code);
      expect(msg.length).toBeGreaterThan(0);
      // Must not be English (no ASCII-only words longer than 3 chars other than VN particles)
      expect(msg).not.toMatch(/^[A-Za-z ]+$/);
    }
  });
});
