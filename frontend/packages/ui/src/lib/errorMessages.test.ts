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

  it('never returns empty string for any known code', () => {
    const knownCodes = [
      'PHONE_ALREADY_EXISTS', 'EMAIL_ALREADY_EXISTS', 'INVALID_CREDENTIALS',
      'INVALID_TOKEN', 'UNAUTHORIZED', 'ACCOUNT_INACTIVE', 'FORBIDDEN',
      'NOT_FOUND', 'VALIDATION_ERROR', 'CONFLICT', 'INVALID_STATUS_TRANSITION',
      'HAS_ACTIVE_DEPENDENCIES', 'CONTRACTOR_ASSIGNMENT_NOT_ALLOWED',
      'SELF_OPERATION_NOT_ALLOWED', 'RATE_LIMITED', 'INTERNAL_ERROR',
    ];
    for (const code of knownCodes) {
      const msg = getVnErrorMessage(code);
      expect(msg.length).toBeGreaterThan(0);
      // Must not be English (no ASCII-only words longer than 3 chars other than VN particles)
      expect(msg).not.toMatch(/^[A-Za-z ]+$/);
    }
  });
});
