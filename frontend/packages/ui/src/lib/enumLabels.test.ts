import { describe, it, expect } from 'vitest';
import { labelFor, enumLabels } from './enumLabels';

describe('labelFor', () => {
  it('returns VN labels for known keys', () => {
    expect(labelFor('ApartmentStatus', 'OCCUPIED')).toBe('Đã ở');
    expect(labelFor('TicketStatus', 'IN_PROGRESS')).toBe('Đang xử lý');
    expect(labelFor('TicketPriority', 'URGENT')).toBe('Khẩn cấp');
    expect(labelFor('TicketCategory', 'MAINTENANCE_REPAIR')).toBe('Sửa chữa & Bảo trì');
    expect(labelFor('ContractorSpecialty', 'FIRE_SAFETY')).toBe('Phòng cháy');
    expect(labelFor('VehicleType', 'MOTORBIKE')).toBe('Xe máy');
    expect(labelFor('ParkingSlotStatus', 'RESERVED')).toBe('Đã đặt trước');
    expect(labelFor('ActiveStatus', 'INACTIVE')).toBe('Ngừng');
    expect(labelFor('ContractStatus', 'ACTIVE')).toBe('Hiệu lực');
    expect(labelFor('ContractStatus', 'EXPIRED')).toBe('Đã hết hạn');
    expect(labelFor('ResidentType', 'OWNER')).toBe('Chủ sở hữu');
    expect(labelFor('AnnouncementType', 'EVENT')).toBe('Sự kiện');
    expect(labelFor('AnnouncementType', 'URGENT')).toBe('Khẩn cấp');
    expect(labelFor('BookingStatus', 'PENDING')).toBe('Chờ duyệt');
    expect(labelFor('BookingStatus', 'COMPLETED')).toBe('Hoàn tất');
  });

  it('falls back to the raw key for unmapped values', () => {
    expect(labelFor('TicketStatus', 'SOME_FUTURE_STATUS')).toBe('SOME_FUTURE_STATUS');
  });

  it('returns empty string for null/undefined keys', () => {
    expect(labelFor('TicketStatus', null)).toBe('');
    expect(labelFor('TicketStatus', undefined)).toBe('');
  });

  it('every map value is non-empty Vietnamese display text', () => {
    for (const group of Object.values(enumLabels)) {
      for (const label of Object.values(group)) {
        expect(label.length).toBeGreaterThan(0);
      }
    }
  });
});
