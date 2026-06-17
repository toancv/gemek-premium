import React, { useState } from 'react';
import { Outlet, NavLink, useNavigate } from 'react-router-dom';
import { useAuthStore } from '../store/authStore';
import { useNotifications, useMarkAllRead, useUnreadCount, useMarkNotificationRead } from '../api/hooks';
import { t } from '../i18n/vi';

// referenceType → route builder for bell deep-links (N3 P8, ports the resident N1
// pattern). Admin-receivable rows: "Ticket" (TICKET_CREATED, SLA warning/breach,
// assigned/rated/status) → detail page; "Contract" (CONTRACT_EXPIRING) → Reports,
// where the expiring-contracts table lives; "MaintenanceSchedule" (SCHEDULE_DUE)
// has no admin route — intentionally unmapped → mark-read only (N1 rule, never throws).
const NOTIF_ROUTES: Record<string, (referenceId: string) => string> = {
  Ticket: (referenceId) => `/tickets/${referenceId}`,
  Contract: () => '/reports',
};

const NAV = [
  { to: '/dashboard', label: t('nav.dashboard'), roles: ['ADMIN','BOARD_MEMBER','TECHNICIAN'] },
  { to: '/apartments', label: t('nav.apartments'), roles: ['ADMIN','BOARD_MEMBER'] },
  { to: '/residents', label: t('nav.residents'), roles: ['ADMIN'] },
  { to: '/users', label: t('nav.users'), roles: ['ADMIN'] },
  { to: '/tickets', label: t('nav.tickets'), roles: ['ADMIN','BOARD_MEMBER','TECHNICIAN'] },
  { to: '/contractors', label: t('nav.contractors'), roles: ['ADMIN','BOARD_MEMBER'] },
  { to: '/announcements', label: t('nav.announcements'), roles: ['ADMIN'] },
  { to: '/vehicles', label: t('nav.vehicles'), roles: ['ADMIN'] },
  // TEMP_HIDDEN_DEFERRED: amenities nav — feature deferred, see PROGRESS.md
  // { to: '/amenities', label: 'Amenities', roles: ['ADMIN'] },
  // TEMP_HIDDEN_DEFERRED: parking nav — feature deferred, see PROGRESS.md
  // { to: '/parking', label: 'Parking', roles: ['ADMIN'] },
  { to: '/reports', label: t('nav.reports'), roles: ['ADMIN','BOARD_MEMBER'] },
];

export function Layout() {
  const user = useAuthStore((s) => s.user);
  const logout = useAuthStore((s) => s.logout);
  const navigate = useNavigate();
  const [notifOpen, setNotifOpen] = useState(false);
  const { data: notifData } = useNotifications();
  const { data: unreadData } = useUnreadCount();
  const markAllRead = useMarkAllRead();
  const markNotifRead = useMarkNotificationRead();

  const nav = NAV.filter((n) => user && n.roles.includes(user.role));
  const handleLogout = async () => { await logout(); navigate('/login'); };

  const handleNotifClick = (n: any) => {
    if (!n.isRead) markNotifRead.mutate(n.id);
    // Unknown/unmapped referenceType → mark read only, no navigation.
    const route = n.referenceId ? NOTIF_ROUTES[n.referenceType ?? '']?.(n.referenceId) : undefined;
    if (route) { setNotifOpen(false); navigate(route); }
  };

  return (
    <div className="flex h-screen bg-gray-50" style={{ minWidth: 1280 }}>
      <aside className="w-60 bg-gray-900 flex flex-col flex-shrink-0">
        <div className="px-6 py-5 border-b border-gray-700">
          <h1 className="text-white font-bold text-lg">Gemek Premium</h1>
          <p className="text-gray-400 text-xs mt-0.5">{t('layout.adminPortal')}</p>
        </div>
        <nav className="flex-1 py-4 overflow-y-auto">
          {nav.map((n) => (
            <NavLink key={n.to} to={n.to} className={({ isActive }) =>
              'flex items-center px-6 py-2.5 text-sm transition-colors ' +
              (isActive ? 'bg-blue-600 text-white' : 'text-gray-300 hover:bg-gray-800 hover:text-white')
            }>{n.label}</NavLink>
          ))}
        </nav>
        <div className="px-6 py-4 border-t border-gray-700">
          <p className="text-gray-300 text-sm font-medium truncate">{user?.fullName}</p>
          <p className="text-gray-500 text-xs">{user?.role}</p>
          <button onClick={handleLogout} className="mt-2 text-xs text-gray-400 hover:text-white">{t('layout.signOut')}</button>
        </div>
      </aside>
      <div className="flex-1 flex flex-col overflow-hidden">
        <header className="bg-white border-b border-gray-200 px-6 py-3 flex items-center justify-end gap-4">
          <div className="relative">
            <button onClick={() => setNotifOpen((o) => !o)} className="relative p-1.5 text-gray-500 hover:text-gray-700">
              <svg className="h-5 w-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 17h5l-1.405-1.405A2.032 2.032 0 0118 14.158V11a6.002 6.002 0 00-4-5.659V5a2 2 0 10-4 0v.341C7.67 6.165 6 8.388 6 11v3.159c0 .538-.214 1.055-.595 1.436L4 17h5m6 0v1a3 3 0 11-6 0v-1m6 0H9" />
              </svg>
              {unreadData?.unreadCount > 0 && (
                <span className="absolute -top-0.5 -right-0.5 h-4 w-4 bg-red-500 text-white text-xs rounded-full flex items-center justify-center">
                  {unreadData.unreadCount > 9 ? '9+' : unreadData.unreadCount}
                </span>
              )}
            </button>
            {notifOpen && (
              <div className="absolute right-0 top-full mt-1 w-80 bg-white rounded-lg shadow-lg border border-gray-200 z-50">
                <div className="flex items-center justify-between px-4 py-2 border-b border-gray-100">
                  <span className="font-medium text-sm">{t('layout.notifications')}</span>
                  <button onClick={() => markAllRead.mutate()} className="text-xs text-blue-600 hover:underline">{t('layout.markAllRead')}</button>
                </div>
                <div className="max-h-64 overflow-y-auto">
                  {(!notifData?.data?.length) && <p className="text-center text-gray-400 text-sm py-6">{t('layout.noNotifications')}</p>}
                  {notifData?.data?.map((n: any) => (
                    <button
                      key={n.id}
                      onClick={() => handleNotifClick(n)}
                      className={'block w-full text-left px-4 py-3 border-b border-gray-50 hover:bg-gray-50 ' + (!n.isRead ? 'bg-blue-50' : '')}
                    >
                      <p className="text-sm font-medium text-gray-800">{n.title}</p>
                      <p className="text-xs text-gray-500 mt-0.5">{n.body}</p>
                    </button>
                  ))}
                </div>
              </div>
            )}
          </div>
          <span className="text-sm text-gray-700">{user?.fullName}</span>
        </header>
        <main className="flex-1 overflow-y-auto p-6"><Outlet /></main>
      </div>
    </div>
  );
}
