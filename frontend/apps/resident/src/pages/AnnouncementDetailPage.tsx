import React, { useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { labelFor, formatVNDate, MarkdownContent } from '@gemek/ui';
import { useAnnouncement, useMarkAnnouncementRead } from '../api/hooks';
import { t } from '../i18n/vi';

const TYPE_COLORS: Record<string, string> = {
  GENERAL: 'bg-blue-100 text-blue-700', URGENT: 'bg-red-100 text-red-700',
  MAINTENANCE: 'bg-yellow-100 text-yellow-700', AMENITY: 'bg-purple-100 text-purple-700',
  EVENT: 'bg-green-100 text-green-700',
};

/** Human-readable byte size (B/KB/MB) for an attachment; '' when unknown/invalid. */
function formatSize(bytes: number | null): string {
  if (bytes == null || !Number.isFinite(bytes) || bytes < 0) return '';
  if (bytes < 1024) return `${bytes} B`;
  const kb = bytes / 1024;
  // Guard the rounding boundary: round(kb) can hit 1024 just under 1 MiB — roll over to MB instead of '1024 KB'.
  if (kb < 1023.5) return `${Math.round(kb)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

/**
 * Returns the href only when it is an http(s) URL; otherwise undefined. Defense-in-depth — the BE
 * mints a presigned http(s) URL, but a non-http value must never reach the DOM as a link.
 */
function safeHref(url: string): string | undefined {
  return /^https?:\/\//i.test(url) ? url : undefined;
}

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

  // Cover image (if any) renders as a banner above the title; inline images resolve inside the body.
  // Defense-in-depth: the url is a server-minted presigned URL, but assert an http(s) scheme before
  // binding it to src so a non-http manifest value can never reach the DOM (matches the renderer's gate).
  const coverEntry = announcement.media?.find((m) => m.kind === 'COVER');
  const cover = coverEntry && /^https?:\/\//i.test(coverEntry.url) ? coverEntry : undefined;

  // Attachments are BE-gated server-side (out-of-scope → empty); render the section ONLY when non-empty.
  // The downloadUrl already forces a download (Content-Disposition: attachment, signed P1), so a plain
  // anchor is enough — no client fetch/transform, never through MarkdownContent, never inline.
  const attachments = announcement.attachments ?? [];

  return (
    <div className="p-4 space-y-4">
      <button onClick={() => navigate('/announcements')} className="text-sm text-blue-600 flex items-center gap-1">{t('announcements.back')}</button>

      <div className="bg-white rounded-xl border border-gray-200 p-4">
        {cover && (
          <img
            src={cover.url}
            alt={announcement.title}
            loading="lazy"
            className="w-full max-h-64 object-cover rounded-lg mb-3"
          />
        )}
        <div className="flex items-start justify-between gap-2 mb-2">
          <h1 className="font-semibold text-gray-900">{announcement.title}</h1>
          <span className={`flex-shrink-0 text-xs px-2 py-0.5 rounded-full ${TYPE_COLORS[announcement.type] ?? 'bg-gray-100 text-gray-700'}`}>{labelFor('AnnouncementType', announcement.type)}</span>
        </div>
        {announcement.publishedAt && (
          <p className="text-xs text-gray-400 mb-3">{t('announcements.publishedOn')} {formatVNDate(announcement.publishedAt)}</p>
        )}
        <MarkdownContent content={announcement.content} className="text-sm text-gray-700" mediaManifest={announcement.media?.map((m) => ({ mediaId: m.id, kind: m.kind, url: m.url })) ?? []} />

        {attachments.length > 0 && (
          <div className="mt-4 pt-4 border-t border-gray-100">
            <h2 className="text-sm font-semibold text-gray-900 mb-2">{t('announcements.attachments')}</h2>
            <ul className="divide-y divide-gray-100 border border-gray-200 rounded-lg">
              {attachments.map((a) => {
                const href = safeHref(a.downloadUrl);
                return (
                  <li key={a.id} className="flex items-center gap-3 px-3 py-2.5">
                    <svg className="w-5 h-5 flex-shrink-0 text-gray-400" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth="1.5" aria-hidden="true">
                      <path strokeLinecap="round" strokeLinejoin="round" d="M19.5 14.25v-2.625a3.375 3.375 0 00-3.375-3.375h-1.5A1.125 1.125 0 0113.5 7.125v-1.5a3.375 3.375 0 00-3.375-3.375H8.25m2.25 0H5.625c-.621 0-1.125.504-1.125 1.125v17.25c0 .621.504 1.125 1.125 1.125h12.75c.621 0 1.125-.504 1.125-1.125V11.25a9 9 0 00-9-9z" />
                    </svg>
                    <span className="flex-1 min-w-0 truncate text-sm text-gray-800" title={a.displayFilename}>{a.displayFilename}</span>
                    {a.sizeBytes != null && <span className="flex-shrink-0 text-xs text-gray-400">{formatSize(a.sizeBytes)}</span>}
                    {/* Forced-download is set server-side (P1); `download` is belt-and-suspenders. No inline preview. */}
                    {href ? (
                      <a href={href} download rel="noopener noreferrer" aria-label={`${t('announcements.download')} ${a.displayFilename}`} className="flex-shrink-0 px-2.5 py-1 text-xs font-medium text-blue-600 border border-blue-200 rounded-lg active:bg-blue-50">
                        {t('announcements.download')}
                      </a>
                    ) : (
                      <span className="flex-shrink-0 px-2.5 py-1 text-xs text-gray-300 border border-gray-200 rounded-lg" aria-disabled="true">{t('announcements.download')}</span>
                    )}
                  </li>
                );
              })}
            </ul>
          </div>
        )}
      </div>
    </div>
  );
}
