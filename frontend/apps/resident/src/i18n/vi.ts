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
    tickets: 'Yêu cầu',
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
    activeTickets: 'Yêu cầu đang xử lý',
    announcements: 'Tin tức',
    viewAll: 'Xem tất cả',
    noAnnouncements: 'Chưa có tin tức nào',
  },
};

/** App-bound t(): resident dict first (can shadow shared), then viShared. */
export const t = createT(vi, viShared);
