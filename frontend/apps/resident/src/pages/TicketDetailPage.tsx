import React, { useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { toast, getVnErrorMessage } from '@gemek/ui';
import { useTicket, useRateTicket } from '../api/hooks';
import { t } from '../i18n/vi';

const STATUS_BG: Record<string, string> = {
  NEW: 'bg-blue-100 text-blue-700', ASSIGNED: 'bg-purple-100 text-purple-700',
  IN_PROGRESS: 'bg-yellow-100 text-yellow-700', DONE: 'bg-green-100 text-green-700',
  CANCELLED: 'bg-gray-100 text-gray-700',
};

export function TicketDetailPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { data: ticket, isLoading, isError } = useTicket(id!);
  const rate = useRateTicket();
  const [rating, setRating] = useState(0);
  const [comment, setComment] = useState('');
  const [rateError, setRateError] = useState('');

  if (isLoading) return <div className="flex items-center justify-center h-64"><svg className="animate-spin h-8 w-8 text-blue-600" fill="none" viewBox="0 0 24 24"><circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4"/><path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z"/></svg></div>;
  if (isError) return <div className="p-4 bg-red-50 text-red-700 m-4 rounded-xl">Không thể tải phản ánh.</div>;

  const canRate = ticket.status === 'DONE' && !ticket.rating;

  const handleRate = async (e: React.FormEvent) => {
    e.preventDefault();
    setRateError('');
    if (!rating) { setRateError('Vui lòng chọn số sao'); return; }
    try {
      await rate.mutateAsync({ id: id!, data: { rating, comment: comment || null } });
      toast.success('Đánh giá thành công.');
    } catch (err: any) { setRateError(getVnErrorMessage((err as any)?.response?.data?.error)); }
  };

  return (
    <div className="p-4 space-y-4">
      <button onClick={() => navigate(-1)} className="text-sm text-blue-600 flex items-center gap-1">{t('ticketDetail.back')}</button>

      <div className="bg-white rounded-xl border border-gray-200 p-4">
        <div className="flex items-start justify-between gap-2 mb-3">
          <h1 className="font-semibold text-gray-900">{ticket.title}</h1>
          <span className={`flex-shrink-0 text-xs px-2 py-0.5 rounded-full ${STATUS_BG[ticket.status] ?? 'bg-gray-100 text-gray-700'}`}>{ticket.status}</span>
        </div>
        <div className="space-y-1 text-sm">
          <div className="flex gap-2"><span className="text-gray-500">{t('ticketDetail.category')}</span><span>{ticket.category?.replace(/_/g, ' ')}</span></div>
          <div className="flex gap-2"><span className="text-gray-500">{t('ticketDetail.priority')}</span><span>{ticket.priority}</span></div>
          <div className="flex gap-2"><span className="text-gray-500">{t('ticketDetail.submitted')}</span><span>{new Date(ticket.createdAt).toLocaleDateString()}</span></div>
          {ticket.assignedToUser && <div className="flex gap-2"><span className="text-gray-500">{t('ticketDetail.assignedTo')}</span><span>{ticket.assignedToUser.fullName}</span></div>}
          {ticket.slaDeadline && <div className="flex gap-2"><span className="text-gray-500">SLA:</span><span className={ticket.slaBreached ? 'text-red-600 font-medium' : ''}>{new Date(ticket.slaDeadline).toLocaleDateString()}{ticket.slaBreached ? ' ' + t('ticketDetail.breached') : ''}</span></div>}
        </div>
        {ticket.description && <p className="mt-3 text-sm text-gray-600 bg-gray-50 p-3 rounded-lg">{ticket.description}</p>}
        {ticket.rating && (
          <div className="mt-3 p-3 bg-yellow-50 rounded-lg">
            <p className="text-sm font-medium">{t('ticketDetail.yourRating')} {'★'.repeat(ticket.rating)}{'☆'.repeat(5 - ticket.rating)}</p>
            {ticket.ratingComment && <p className="text-xs text-gray-500 mt-1">"{ticket.ratingComment}"</p>}
          </div>
        )}
      </div>

      {ticket.photos?.length > 0 && (
        <div className="bg-white rounded-xl border border-gray-200 p-4">
          <h2 className="font-semibold text-gray-900 mb-3 text-sm">{t('ticketDetail.photos')}</h2>
          <div className="grid grid-cols-2 gap-2">
            {ticket.photos.map((p: any) => (
              <div key={p.id} className="rounded-lg overflow-hidden border border-gray-200">
                <div className="bg-gray-100 px-2 py-0.5 text-xs text-gray-500">{p.phase}</div>
                <img src={p.presignedUrl} alt={p.fileName} className="w-full h-28 object-cover" />
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Status timeline */}
      <div className="bg-white rounded-xl border border-gray-200 p-4">
        <h2 className="font-semibold text-gray-900 mb-3 text-sm">{t('ticketDetail.timeline')}</h2>
        <div className="space-y-3">
          {ticket.statusHistory?.map((h: any) => (
            <div key={h.id} className="flex gap-3 text-sm">
              <div className="w-2 h-2 rounded-full bg-blue-500 mt-1.5 flex-shrink-0" />
              <div>
                <p className="font-medium">{h.oldStatus ?? t('ticketDetail.created')} → {h.newStatus}</p>
                <p className="text-xs text-gray-400">{h.changedBy?.fullName} • {new Date(h.changedAt).toLocaleString()}</p>
                {h.notes && <p className="text-xs text-gray-500 mt-0.5">{h.notes}</p>}
              </div>
            </div>
          ))}
        </div>
      </div>

      {canRate && (
        <div className="bg-white rounded-xl border border-gray-200 p-4">
          <h2 className="font-semibold text-gray-900 mb-3 text-sm">{t('ticketDetail.rateService')}</h2>
          <form onSubmit={handleRate} className="space-y-3">
            <div className="flex gap-2 justify-center">
              {[1,2,3,4,5].map((s) => (
                <button type="button" key={s} onClick={() => setRating(s)}
                  className={`text-2xl transition-transform hover:scale-110 ${s <= rating ? 'text-yellow-400' : 'text-gray-300'}`}>★</button>
              ))}
            </div>
            <textarea value={comment} onChange={(e) => setComment(e.target.value)} rows={2}
              placeholder={t('ticketDetail.commentPlaceholder')} className="block w-full border border-gray-300 rounded-lg px-3 py-2 text-sm resize-none" />
            {rateError && <p className="text-xs text-red-600">{rateError}</p>}
            <button type="submit" disabled={rate.isPending} className="w-full bg-yellow-500 text-white rounded-lg py-2.5 text-sm font-medium hover:bg-yellow-600 disabled:opacity-50">
              {rate.isPending ? t('ticketDetail.submitting') : t('ticketDetail.submitRating')}
            </button>
          </form>
        </div>
      )}
    </div>
  );
}
