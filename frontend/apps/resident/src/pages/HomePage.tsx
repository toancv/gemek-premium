import React from 'react';
import { Link } from 'react-router-dom';
import { useAuthStore } from '../store/authStore';
// TEMP_HIDDEN_DEFERRED: useMyBookings removed from import — bookings feature deferred, see PROGRESS.md
import { useMyTickets, useAnnouncements, useMe } from '../api/hooks';
import { t } from '../i18n/vi';

export function HomePage() {
  const user = useAuthStore((s) => s.user);
  const { data: me } = useMe();
  const { data: ticketsData } = useMyTickets({ size: 5, status: ['NEW', 'ASSIGNED', 'IN_PROGRESS'] });
  // TEMP_HIDDEN_DEFERRED: bookings hook removed — feature deferred, see PROGRESS.md
  const { data: announcements } = useAnnouncements({ size: 3, isPublished: true });

  return (
    <div className="p-4 space-y-4">
      {/* Welcome card */}
      <div className="bg-blue-600 rounded-2xl p-5 text-white">
        <p className="text-blue-200 text-sm">{t('home.welcomeBack')}</p>
        <h2 className="text-xl font-bold mt-0.5">{user?.fullName}</h2>
        {me?.phone && <p className="text-blue-200 text-xs mt-1">{me.phone}</p>}
      </div>

      {/* Quick stats */}
      <div className="grid grid-cols-1 gap-3">
        <Link to="/tickets" className="bg-white rounded-xl border border-gray-200 p-4 hover:border-blue-300 transition-colors">
          <p className="text-2xl font-bold text-blue-600">{ticketsData?.total ?? 0}</p>
          <p className="text-sm text-gray-500 mt-0.5">{t('home.activeTickets')}</p>
        </Link>
        {/* TEMP_HIDDEN_DEFERRED: bookings stat card — feature deferred, see PROGRESS.md */}
        {/* <Link to="/bookings" className="bg-white rounded-xl border border-gray-200 p-4 hover:border-blue-300 transition-colors">
          <p className="text-2xl font-bold text-green-600">{bookingsData?.total ?? 0}</p>
          <p className="text-sm text-gray-500 mt-0.5">Bookings</p>
        </Link> */}
      </div>

      {/* Announcements */}
      <div>
        <div className="flex items-center justify-between mb-2">
          <h3 className="font-semibold text-gray-900">{t('home.announcements')}</h3>
          <Link to="/announcements" className="text-xs text-blue-600">{t('home.viewAll')}</Link>
        </div>
        {!announcements?.data?.length && (
          <div className="bg-white rounded-xl border border-gray-200 p-4 text-center text-gray-400 text-sm">{t('home.noAnnouncements')}</div>
        )}
        <div className="space-y-2">
          {announcements?.data?.map((a: any) => (
            <Link to="/announcements" key={a.id} className="block bg-white rounded-xl border border-gray-200 p-4 hover:border-blue-300 transition-colors">
              <div className="flex items-start justify-between gap-2">
                <p className="font-medium text-gray-900 text-sm">{a.title}</p>
                <span className={`flex-shrink-0 text-xs px-2 py-0.5 rounded-full ${a.type === 'URGENT' ? 'bg-red-100 text-red-600' : 'bg-blue-100 text-blue-600'}`}>{a.type}</span>
              </div>
              <p className="text-xs text-gray-400 mt-1">{a.publishedAt ? new Date(a.publishedAt).toLocaleDateString() : ''}</p>
            </Link>
          ))}
        </div>
      </div>
    </div>
  );
}
