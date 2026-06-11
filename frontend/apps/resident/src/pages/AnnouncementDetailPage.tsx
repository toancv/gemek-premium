import React, { useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { labelFor, formatVNDate } from '@gemek/ui';
import { useAnnouncement, useMarkAnnouncementRead } from '../api/hooks';
import { t } from '../i18n/vi';

const TYPE_COLORS: Record<string, string> = {
  GENERAL: 'bg-blue-100 text-blue-700', URGENT: 'bg-red-100 text-red-700',
  MAINTENANCE: 'bg-yellow-100 text-yellow-700', AMENITY: 'bg-purple-100 text-purple-700',
  EVENT: 'bg-green-100 text-green-700',
};

export function AnnouncementDetailPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { data: announcement, isLoading, isError } = useAnnouncement(id!);
  const markRead = useMarkAnnouncementRead();

  // Single read surface: mark read on first successful load. The mutation's
  // ['announcements'] invalidation refetches isRead=true, so this fires once.
  useEffect(() => {
    if (announcement && !announcement.isRead) markRead.mutate(announcement.id);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [announcement?.id, announcement?.isRead]);

  if (isLoading) return <div className="flex items-center justify-center h-64"><svg className="animate-spin h-8 w-8 text-blue-600" fill="none" viewBox="0 0 24 24"><circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4"/><path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z"/></svg></div>;
  if (isError || !announcement) return <div className="p-4 bg-red-50 text-red-700 m-4 rounded-xl">{t('announcements.loadError')}</div>;

  return (
    <div className="p-4 space-y-4">
      <button onClick={() => navigate('/announcements')} className="text-sm text-blue-600 flex items-center gap-1">{t('announcements.back')}</button>

      <div className="bg-white rounded-xl border border-gray-200 p-4">
        <div className="flex items-start justify-between gap-2 mb-2">
          <h1 className="font-semibold text-gray-900">{announcement.title}</h1>
          <span className={`flex-shrink-0 text-xs px-2 py-0.5 rounded-full ${TYPE_COLORS[announcement.type] ?? 'bg-gray-100 text-gray-700'}`}>{labelFor('AnnouncementType', announcement.type)}</span>
        </div>
        {announcement.publishedAt && (
          <p className="text-xs text-gray-400 mb-3">{t('announcements.publishedOn')} {formatVNDate(announcement.publishedAt)}</p>
        )}
        <p className="text-sm text-gray-700 whitespace-pre-line">{announcement.content}</p>
      </div>
    </div>
  );
}
