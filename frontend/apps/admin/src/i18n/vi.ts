/**
 * Admin-app Vietnamese dictionary.
 *
 * App-specific strings only — genuinely cross-app strings live in
 * `@gemek/ui` viShared; enum display labels in `@gemek/ui` enumLabels
 * (see DECISIONS.md 2026-06-10 i18n entries).
 *
 * Terminology (locked): Ticket = "Phản ánh", Announcements = "Tin tức",
 * notification bell = "Thông báo". Display text only — routes, keys and
 * enum values stay raw.
 */
import { createT, viShared, type TranslationDict } from '@gemek/ui';

export const vi: TranslationDict = {
  nav: {
    dashboard: 'Tổng quan',
    apartments: 'Căn hộ',
    residents: 'Cư dân',
    tickets: 'Phản ánh',
    contractors: 'Nhà thầu',
    announcements: 'Tin tức',
    vehicles: 'Phương tiện',
    reports: 'Báo cáo',
  },
  layout: {
    adminPortal: 'Cổng quản trị',
    signOut: 'Đăng xuất',
    notifications: 'Thông báo',
    markAllRead: 'Đánh dấu đã đọc tất cả',
    noNotifications: 'Không có thông báo',
  },
  dashboard: {
    title: 'Tổng quan',
    loadError: 'Không thể tải dữ liệu tổng quan. Vui lòng thử lại.',
    openTickets: 'Phản ánh đang mở',
    inProgress: '{n} đang xử lý',
    slaBreached: 'Vi phạm SLA',
    requiresAction: 'Cần xử lý ngay',
    expiringContracts: 'Hợp đồng sắp hết hạn',
    in90Days: '{n} trong 90 ngày',
    apartments: 'Căn hộ',
    total: 'Tổng',
    occupied: 'Đã ở',
    available: 'Còn trống',
    occupancyRate: 'Tỷ lệ lấp đầy',
    ticketsByCategory: 'Phản ánh theo loại',
  },
  reports: {
    title: 'Báo cáo',
    tabSummary: 'Tổng hợp',
    tabTickets: 'Phản ánh',
    tabAmenities: 'Tiện ích',
    tabContracts: 'Hợp đồng sắp hết hạn',
    from: 'Từ:',
    to: 'Đến:',
    totalApartments: 'Tổng căn hộ',
    pctOccupied: '{n}% đã ở',
    openTickets: 'Phản ánh đang mở',
    nOverdue: '{n} quá hạn',
    activeContracts: 'Hợp đồng hiệu lực',
    nExpiring30: '{n} sắp hết hạn trong 30 ngày',
    amenityBookingsMonth: 'Lượt đặt tiện ích (tháng)',
    nPending: '{n} chờ duyệt',
    ticketsByCategory: 'Phản ánh theo loại',
    total: 'Tổng',
    completed: 'Hoàn tất',
    slaBreachRate: 'Tỷ lệ vi phạm SLA',
    slaBreachedCol: 'Vi phạm SLA',
    avgRating: 'Đánh giá TB',
    period: 'Kỳ',
    amenity: 'Tiện ích',
    totalBookings: 'Tổng lượt đặt',
    approved: 'Đã duyệt',
    rejected: 'Từ chối',
    cancelled: 'Đã hủy',
    utilization: 'Hiệu suất sử dụng',
    contractTitle: 'Tiêu đề',
    contractor: 'Nhà thầu',
    endDate: 'Ngày kết thúc',
    daysToExpiry: 'Số ngày còn lại',
    valueVnd: 'Giá trị (VND)',
    nDays: '{n} ngày',
  },
};

/** App-bound t(): admin dict first (can shadow shared), then viShared. */
export const t = createT(vi, viShared);
