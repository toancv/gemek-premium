import React, { useState } from 'react';
import { Outlet, NavLink, useNavigate } from 'react-router-dom';
import { useAuthStore } from '../store/authStore';
import { useNotifications, useMarkAllRead } from '../api/hooks';

export function Layout() {
  const user = useAuthStore((s) => s.user);
  const logout = useAuthStore((s) => s.logout);
  const navigate = useNavigate();
  const [notifOpen, setNotifOpen] = useState(false);
  const { data: notifData } = useNotifications();
  const markAllRead = useMarkAllRead();

  const handleLogout = async () => { await logout(); navigate('/login'); };

  const navLinks = [
    { to: '/', label: 'Home', icon: 'H', end: true },
    { to: '/tickets', label: 'Tickets', icon: 'T' },
    // TEMP_HIDDEN_DEFERRED: amenities nav — feature deferred, see PROGRESS.md
    // { to: '/amenities', label: 'Amenities', icon: 'A' },
    // TEMP_HIDDEN_DEFERRED: bookings nav — feature deferred, see PROGRESS.md
    // { to: '/bookings', label: 'Bookings', icon: 'B' },
    // TEMP_HIDDEN_DEFERRED: parking nav — feature deferred, see PROGRESS.md
    // { to: '/parking', label: 'Parking', icon: 'P' },
    { to: '/announcements', label: 'News', icon: 'N' },
    { to: '/profile', label: 'Profile', icon: 'Me' },
  ];

  return (
    <div className="flex flex-col min-h-screen bg-gray-50 max-w-md mx-auto">
      {/* Top bar */}
      <header className="bg-blue-600 text-white px-4 py-3 flex items-center justify-between sticky top-0 z-40">
        <div>
          <h1 className="font-semibold text-sm">Gemek Premium</h1>
          <p className="text-blue-200 text-xs">Hello, {user?.fullName?.split(' ').pop()}</p>
        </div>
        <div className="flex items-center gap-3">
          <div className="relative">
            <button onClick={() => setNotifOpen((o) => !o)} className="relative p-1">
              <svg className="h-5 w-5 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 17h5l-1.405-1.405A2.032 2.032 0 0118 14.158V11a6.002 6.002 0 00-4-5.659V5a2 2 0 10-4 0v.341C7.67 6.165 6 8.388 6 11v3.159c0 .538-.214 1.055-.595 1.436L4 17h5m6 0v1a3 3 0 11-6 0v-1m6 0H9" />
              </svg>
              {notifData?.unreadCount > 0 && (
                <span className="absolute -top-0.5 -right-0.5 h-4 w-4 bg-red-500 text-white text-xs rounded-full flex items-center justify-center">{notifData.unreadCount > 9 ? '9+' : notifData.unreadCount}</span>
              )}
            </button>
            {notifOpen && (
              <div className="absolute right-0 top-full mt-1 w-72 bg-white rounded-lg shadow-lg border border-gray-200 z-50">
                <div className="flex items-center justify-between px-4 py-2 border-b border-gray-100">
                  <span className="font-medium text-sm text-gray-900">Notifications</span>
                  <button onClick={() => markAllRead.mutate()} className="text-xs text-blue-600">Mark all read</button>
                </div>
                <div className="max-h-64 overflow-y-auto">
                  {(!notifData?.data?.length) && <p className="text-center text-gray-400 text-sm py-4">No notifications</p>}
                  {notifData?.data?.map((n: any) => (
                    <div key={n.id} className={'px-4 py-3 border-b border-gray-50 ' + (!n.isRead ? 'bg-blue-50' : '')}>
                      <p className="text-sm font-medium text-gray-800">{n.title}</p>
                      <p className="text-xs text-gray-500 mt-0.5">{n.body}</p>
                    </div>
                  ))}
                </div>
              </div>
            )}
          </div>
          <button onClick={handleLogout} className="text-blue-200 text-xs hover:text-white">Out</button>
        </div>
      </header>

      {/* Page content */}
      <main className="flex-1 pb-16 overflow-y-auto">
        <Outlet />
      </main>

      {/* Bottom nav */}
      <nav className="fixed bottom-0 left-1/2 -translate-x-1/2 w-full max-w-md bg-white border-t border-gray-200 flex z-40">
        {navLinks.map((n) => (
          <NavLink key={n.to} to={n.to} end={n.end} className={({ isActive }) =>
            'flex-1 flex flex-col items-center py-2 text-xs ' + (isActive ? 'text-blue-600' : 'text-gray-500')
          }>
            <span className="text-xs font-bold w-5 h-5 flex items-center justify-center">{n.icon}</span>
            <span>{n.label}</span>
          </NavLink>
        ))}
      </nav>
    </div>
  );
}
