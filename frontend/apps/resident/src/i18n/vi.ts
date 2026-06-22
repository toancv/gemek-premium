/**
 * Resident-app Vietnamese dictionary.
 *
 * App-specific strings only — genuinely cross-app strings live in
 * `@gemek/ui` viShared (see DECISIONS.md 2026-06-10 i18n entry).
 * Enum display-maps go in `src/i18n/enums.ts` (separate later step) —
 * raw enum keys (value= attrs, API payloads) are never translated here.
 */
import { createT, viShared, type TranslationDict } from '@gemek/ui';

export const vi: TranslationDict = {
  nav: {
    home: 'Trang chủ',
    // Terminology decision 2026-06-10: user-facing "Ticket" = "Phản ánh" (display only)
    tickets: 'Phản ánh',
    vehicles: 'Phương tiện',
    news: 'Tin tức',
    profile: 'Cá nhân',
  },
  layout: {
    hello: 'Xin chào, {name}',
    notifications: 'Thông báo',
    markAllRead: 'Đánh dấu đã đọc',
    noNotifications: 'Không có thông báo',
    signOut: 'Thoát',
  },
  home: {
    welcomeBack: 'Chào mừng trở lại',
    activeTickets: 'Phản ánh đang xử lý',
    announcements: 'Tin tức',
    viewAll: 'Xem tất cả',
    noAnnouncements: 'Chưa có tin tức nào',
  },
  tickets: {
    title: 'Phản ánh',
    new: '+ Tạo mới',
    createFirst: 'Tạo phản ánh đầu tiên',
    // Verb consistency (CTO 2026-06-10): create/submit action = "Gửi phản ánh"
    modalTitle: 'Gửi phản ánh',
    // N3 P7 — community tickets (design §E FE touchpoints)
    tabMine: 'Của tôi',
    tabCommunity: 'Cộng đồng',
    publicToggle: 'Công khai phản ánh để cư dân khác theo dõi',
  },
  bookings: {
    title: 'Lượt đặt của tôi',
  },
  ticketDetail: {
    back: '← Quay lại',
    category: 'Loại:',
    priority: 'Mức ưu tiên:',
    submitted: 'Ngày gửi:',
    assignedTo: 'Phụ trách:',
    sla: 'Hạn hoàn thành:',
    breached: '(Quá hạn)',
    yourRating: 'Đánh giá của bạn:',
    photos: 'Hình ảnh',
    timeline: 'Lịch sử',
    // Null-oldStatus fallback in status timeline — display text, not an enum value
    created: 'Khởi tạo',
    rateService: 'Đánh giá dịch vụ',
    commentPlaceholder: 'Nhận xét (không bắt buộc)...',
    submitting: 'Đang gửi...',
    submitRating: 'Gửi đánh giá',
    // N3 P7 — follow button on redacted public-ticket view only
    follow: 'Theo dõi',
    unfollow: 'Bỏ theo dõi',
  },
  announcements: {
    // Terminology (CTO 2026-06-10): announcements feature = "Tin tức";
    // notification bell/panel = "Thông báo". Never swap.
    title: 'Tin tức',
    everyone: 'Tất cả',
    back: '← Quay lại',
    publishedOn: 'Ngày đăng:',
    loadError: 'Không thể tải tin tức.',
  },
  amenities: {
    title: 'Đặt tiện ích',
    capacity: 'Sức chứa:',
    requiresApproval: 'Cần phê duyệt',
    book: 'Đặt',
    bookName: 'Đặt {name}',
    date: 'Ngày',
    start: 'Bắt đầu',
    end: 'Kết thúc',
    notes: 'Ghi chú',
    booking: 'Đang đặt...',
    confirm: 'Xác nhận',
  },
  parking: {
    title: 'Bãi xe',
    mySlots: 'Chỗ đậu xe của tôi',
    zone: 'Khu:',
    type: 'Loại:',
    vehicle: 'Phương tiện:',
    card: 'Thẻ:',
    since: 'Từ:',
    // Fallback when slotNumber missing — display text, not enum
    slotFallback: 'Chỗ đậu',
  },
  profile: {
    title: 'Trang cá nhân',
    role: 'Vai trò:',
    lastLogin: 'Đăng nhập gần nhất:',
    changePassword: 'Đổi mật khẩu',
    currentPassword: 'Mật khẩu hiện tại',
    newPassword: 'Mật khẩu mới',
    confirmNewPassword: 'Xác nhận mật khẩu mới',
    changing: 'Đang đổi...',
    signOut: 'Đăng xuất',
    editInfo: 'Cập nhật thông tin',
    fullName: 'Họ và tên',
    phone: 'Số điện thoại',
    email: 'Email',
    emailPlaceholder: 'Không bắt buộc',
    save: 'Lưu thay đổi',
    saving: 'Đang lưu...',
    phoneConfirmTitle: 'Đổi số điện thoại đăng nhập',
    phoneConfirmBody: 'Bạn sắp đổi số điện thoại đăng nhập thành {phone}. Lần sau hãy dùng số này để đăng nhập.',
    phoneConfirmOk: 'Xác nhận',
    phoneConfirmCancel: 'Hủy',
  },
};

/** App-bound t(): resident dict first (can shadow shared), then viShared. */
export const t = createT(vi, viShared);
