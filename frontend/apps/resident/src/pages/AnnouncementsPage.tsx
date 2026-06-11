import React from 'react';
import { useAnnouncements, useMarkAnnouncementRead } from '../api/hooks';
import { t } from '../i18n/vi';
import { labelFor } from '@gemek/ui';

const TYPE_COLORS: Record<string, string> = {
  GENERAL: 'bg-blue-100 text-blue-700', URGENT: 'bg-red-100 text-red-700',
  MAINTENANCE: 'bg-yellow-100 text-yellow-700', AMENITY: 'bg-purple-100 text-purple-700',
  EVENT: 'bg-green-100 text-green-700',
};

export function AnnouncementsPage() {
  const { data, isLoading } = useAnnouncements({ size: 30, isPublished: true });
  const markRead = useMarkAnnouncementRead();

  return (
    <div className="p-4">
      <h1 className="text-lg font-bold text-gray-900 mb-4">{t('announcements.title')}</h1>
      {isLoading && <div className="text-center py-8 text-gray-400">{t('common.loading')}</div>}
      {!isLoading && !data?.data?.length && (
        <div className="text-center py-12 text-gray-400">
          <p className="text-4xl mb-2">📢</p>
          <p>{t('common.emptyYet', { item: 'tin tức' })}</p>
        </div>
      )}
      {/* markRead is intentionally fire-and-forget: read-marking is best-effort UX, not a user action that needs feedback */}
      <div className="space-y-3">
        {data?.data?.map((a: any) => (
          <div key={a.id} onClick={() => { if (!a.isRead) markRead.mutate(a.id); }}
            className={`bg-white rounded-xl border p-4 cursor-pointer transition-colors ${!a.isRead ? 'border-blue-300 bg-blue-50' : 'border-gray-200'}`}>
            <div className="flex items-start justify-between gap-2 mb-2">
              <h3 className="font-semibold text-gray-900 text-sm">{a.title}</h3>
              <span className={`flex-shrink-0 text-xs px-2 py-0.5 rounded-full ${TYPE_COLORS[a.type] ?? 'bg-gray-100 text-gray-700'}`}>{labelFor('AnnouncementType', a.type)}</span>
            </div>
            <div className="flex items-center justify-between text-xs text-gray-400">
              <span>{a.targetScope === 'ALL' ? t('announcements.everyone') : `${a.targetScope}${a.targetBlock ? ': ' + a.targetBlock.name : ''}`}</span>
              <span>{a.publishedAt ? new Date(a.publishedAt).toLocaleDateString() : ''}</span>
            </div>
            {!a.isRead && <div className="w-2 h-2 rounded-full bg-blue-600 absolute top-4 left-4" />}
          </div>
        ))}
      </div>
    </div>
  );
}
