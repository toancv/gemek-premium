/**
 * BE enum key → Vietnamese DISPLAY label maps, shared by both apps.
 *
 * Kept separate from the UI-text dictionary (vi.ts) by design
 * (DECISIONS.md 2026-06-10 i18n entry).
 *
 * ABSOLUTE RULE: display labels only. Every <option value={...}>, filter
 * param, and comparison keeps the raw BE enum key — only the text rendered
 * to the user goes through labelFor(). Never feed a label back to the API.
 */

export const enumLabels = {
  ApartmentStatus: {
    OCCUPIED: 'Đã ở',
    AVAILABLE: 'Còn trống',
    MAINTENANCE: 'Bảo trì',
  },
  TicketStatus: {
    NEW: 'Mới',
    ASSIGNED: 'Đã phân công',
    IN_PROGRESS: 'Đang xử lý',
    DONE: 'Hoàn tất',
    CANCELLED: 'Đã hủy',
  },
  TicketPriority: {
    LOW: 'Thấp',
    MEDIUM: 'Trung bình',
    HIGH: 'Cao',
    URGENT: 'Khẩn cấp',
  },
  ContractorSpecialty: {
    CLEANING: 'Vệ sinh',
    SECURITY: 'An ninh',
    ELEVATOR: 'Thang máy',
    FIRE_SAFETY: 'Phòng cháy',
    LANDSCAPING: 'Cảnh quan',
    PEST_CONTROL: 'Kiểm soát côn trùng',
    ELECTRICAL: 'Điện',
    PLUMBING: 'Cấp thoát nước',
    OTHER: 'Khác',
  },
  VehicleType: {
    CAR: 'Ô tô',
    MOTORBIKE: 'Xe máy',
    BICYCLE: 'Xe đạp',
    OTHER: 'Khác',
  },
  ParkingSlotStatus: {
    AVAILABLE: 'Còn trống',
    OCCUPIED: 'Đã sử dụng',
    RESERVED: 'Đã đặt trước',
  },
  // Generic active flag used by contractor/vehicle status displays
  ActiveStatus: {
    ACTIVE: 'Hoạt động',
    INACTIVE: 'Ngừng',
  },
} as const;

export type EnumLabelType = keyof typeof enumLabels;

/**
 * Returns the VN display label for a BE enum key.
 * Unmapped keys fall back to the raw key — display never breaks on new
 * BE enum values, they just show untranslated.
 */
export function labelFor(enumType: EnumLabelType, key: string | null | undefined): string {
  if (!key) return '';
  return (enumLabels[enumType] as Record<string, string>)[key] ?? key;
}
