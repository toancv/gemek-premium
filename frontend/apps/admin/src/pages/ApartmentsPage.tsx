import React, { useState, useCallback } from 'react';
import { SearchableSelect, getVnErrorMessage, labelFor } from '@gemek/ui';
import type { SearchableOption } from '@gemek/ui';
import { useApartments, useBlocks, useCreateApartment, useUpdateApartment } from '../api/hooks';
import { apiClient } from '../api/client';
import { useRoleFlags } from '../lib/useRoleFlags';
import { t } from '../i18n/vi';

export function ApartmentsPage() {
  const [search, setSearch] = useState('');
  const [status, setStatus] = useState('');
  const [blockId, setBlockId] = useState('');
  const [page, setPage] = useState(0);
  const [editApt, setEditApt] = useState<any>(null);
  const [showCreate, setShowCreate] = useState(false);
  const [newBlockId, setNewBlockId] = useState('');
  const [blockError, setBlockError] = useState('');
  const [createError, setCreateError] = useState('');
  const [editError, setEditError] = useState('');

  // Apartment create/edit are ADMIN-only on the BE (POST + PUT /apartments = hasRole('ADMIN')).
  // Hide both write controls from BOARD_MEMBER (read/oversight) — backlog (c) 403 fix, Direction A.
  const { isAdmin } = useRoleFlags();

  const params = { page, size: 20, ...(search && { search }), ...(status && { status }), ...(blockId && { blockId }) };
  const { data, isLoading, isError } = useApartments(params);
  const { data: blocksData } = useBlocks();
  const createApt = useCreateApartment();
  const updateApt = useUpdateApartment();

  const loadBlockOptions = useCallback(async (query: string): Promise<SearchableOption[]> => {
    const params: Record<string, unknown> = { size: 10, sort: 'name', direction: 'asc' };
    if (query) params.search = query;
    const res = await apiClient.get('/blocks', { params });
    return (res.data?.data ?? []).map((b: any) => ({ value: b.id, label: b.name }));
  }, []);

  const statusColors: Record<string, string> = {
    OCCUPIED: 'bg-green-100 text-green-700',
    AVAILABLE: 'bg-blue-100 text-blue-700',
    MAINTENANCE: 'bg-yellow-100 text-yellow-700',
  };

  function openCreate() {
    setNewBlockId('');
    setBlockError('');
    setCreateError('');
    setShowCreate(true);
  }

  return (
    <div>
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-2xl font-bold text-gray-900">{t('apartments.title')}</h1>
        {isAdmin && (
          <button onClick={openCreate}
            className="px-4 py-2 text-sm bg-blue-600 text-white rounded-md hover:bg-blue-700">
            + Thêm căn hộ
          </button>
        )}
      </div>

      <div className="bg-white rounded-lg border border-gray-200 p-4 mb-4 flex gap-3">
        <input value={search} onChange={(e) => { setSearch(e.target.value); setPage(0); }}
          placeholder={t('apartments.searchPlaceholder')} className="border border-gray-300 rounded-md px-3 py-2 text-sm flex-1 focus:outline-none focus:ring-2 focus:ring-blue-500" />
        <select value={status} onChange={(e) => { setStatus(e.target.value); setPage(0); }}
          className="border border-gray-300 rounded-md px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500">
          <option value="">{t('apartments.allStatuses')}</option>
          <option value="OCCUPIED">{labelFor('ApartmentStatus', 'OCCUPIED')}</option>
          <option value="AVAILABLE">{labelFor('ApartmentStatus', 'AVAILABLE')}</option>
          {/* MAINTENANCE hidden from the UI (no set flow); BE filter/resolver still support it for re-enable. */}
        </select>
        <select value={blockId} onChange={(e) => { setBlockId(e.target.value); setPage(0); }}
          className="border border-gray-300 rounded-md px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500">
          <option value="">{t('apartments.allBlocks')}</option>
          {blocksData?.data?.map((b: any) => <option key={b.id} value={b.id}>{b.name}</option>)}
        </select>
      </div>

      {isError && <div className="bg-red-50 border border-red-200 rounded-lg p-4 text-red-700 mb-4">{t('apartments.loadError')}</div>}

      <div className="bg-white rounded-lg border border-gray-200 overflow-hidden">
        <table className="w-full text-sm">
          <thead className="bg-gray-50 border-b border-gray-200">
            <tr>
              <th className="text-left px-4 py-3 font-medium text-gray-500">{t('apartments.block')}</th>
              <th className="text-left px-4 py-3 font-medium text-gray-500">{t('apartments.unit')}</th>
              <th className="text-left px-4 py-3 font-medium text-gray-500">{t('apartments.floor')}</th>
              <th className="text-left px-4 py-3 font-medium text-gray-500">{t('apartments.area')}</th>
              <th className="text-left px-4 py-3 font-medium text-gray-500">{t('common.status')}</th>
              <th className="text-left px-4 py-3 font-medium text-gray-500">{t('apartments.primaryContact')}</th>
              <th className="text-left px-4 py-3 font-medium text-gray-500">{t('common.actions')}</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-100">
            {isLoading && <tr><td colSpan={7} className="text-center py-8 text-gray-400">{t('common.loading')}</td></tr>}
            {!isLoading && !data?.data?.length && <tr><td colSpan={7} className="text-center py-8 text-gray-400">{t('common.emptyFound', { item: 'căn hộ' })}</td></tr>}
            {data?.data?.map((apt: any) => (
              <tr key={apt.id} className="hover:bg-gray-50">
                <td className="px-4 py-3">{apt.block?.name}</td>
                <td className="px-4 py-3 font-medium">{apt.unitNumber}</td>
                <td className="px-4 py-3">{apt.floor}</td>
                <td className="px-4 py-3">{apt.areaSqm}</td>
                <td className="px-4 py-3">
                  <span className={`inline-flex px-2 py-0.5 rounded-full text-xs font-medium ${statusColors[apt.status] ?? 'bg-gray-100 text-gray-700'}`}>{labelFor('ApartmentStatus', apt.status)}</span>
                </td>
                <td className="px-4 py-3">{apt.primaryContact?.fullName ?? '—'}</td>
                <td className="px-4 py-3">
                  {isAdmin && (
                    <button onClick={() => { setEditApt(apt); setEditError(''); }} className="text-blue-600 hover:underline text-xs">{t('common.edit')}</button>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        <div className="flex items-center justify-between px-4 py-3 border-t border-gray-200">
          <span className="text-xs text-gray-500">{t('common.total')} {data?.total ?? 0}</span>
          <div className="flex gap-2">
            <button onClick={() => setPage((p) => Math.max(0, p - 1))} disabled={page === 0}
              className="px-3 py-1 text-xs border border-gray-300 rounded hover:bg-gray-50 disabled:opacity-40">{t('common.prev')}</button>
            <span className="px-3 py-1 text-xs">{page + 1} / {data?.totalPages ?? 1}</span>
            <button onClick={() => setPage((p) => p + 1)} disabled={page + 1 >= (data?.totalPages ?? 1)}
              className="px-3 py-1 text-xs border border-gray-300 rounded hover:bg-gray-50 disabled:opacity-40">{t('common.next')}</button>
          </div>
        </div>
      </div>

      {showCreate && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
          <div className="absolute inset-0 bg-black/50" onClick={() => setShowCreate(false)} />
          <div className="relative bg-white rounded-lg shadow-xl w-full max-w-md p-6">
            <h2 className="text-lg font-semibold mb-4">Thêm căn hộ mới</h2>
            <form onSubmit={async (e) => {
              e.preventDefault();
              if (!newBlockId) { setBlockError('Vui lòng chọn tòa.'); return; }
              setBlockError('');
              setCreateError('');
              const fd = new FormData(e.target as HTMLFormElement);
              try {
                await createApt.mutateAsync({
                  blockId: newBlockId,
                  floor: Number(fd.get('floor')),
                  unitNumber: fd.get('unitNumber') as string,
                  areaSqm: Number(fd.get('areaSqm')),
                  notes: (fd.get('notes') as string) || undefined,
                });
                setShowCreate(false);
                setNewBlockId('');
              } catch (err: any) {
                setCreateError(getVnErrorMessage(err?.response?.data?.error));
              }
            }} className="space-y-3">
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">{t('apartments.block')} <span className="text-red-500">*</span></label>
                <SearchableSelect
                  loadOptions={loadBlockOptions}
                  value={newBlockId}
                  onChange={(v) => { setNewBlockId(v); setBlockError(''); }}
                  placeholder="Chọn tòa..."
                  error={blockError}
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Số căn hộ</label>
                <input name="unitNumber" required className="block w-full border border-gray-300 rounded-md px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Tầng</label>
                <input name="floor" type="number" required className="block w-full border border-gray-300 rounded-md px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Diện tích (m²)</label>
                <input name="areaSqm" type="number" step="0.1" required className="block w-full border border-gray-300 rounded-md px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Ghi chú</label>
                <textarea name="notes" className="block w-full border border-gray-300 rounded-md px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" rows={2} />
              </div>
              {createError && <p className="text-sm text-red-600 bg-red-50 px-3 py-2 rounded">{createError}</p>}
              <div className="flex gap-2 justify-end pt-2">
                <button type="button" onClick={() => setShowCreate(false)} className="px-4 py-2 text-sm border border-gray-300 rounded-md hover:bg-gray-50">Hủy</button>
                <button type="submit" disabled={createApt.isPending} className="px-4 py-2 text-sm bg-blue-600 text-white rounded-md hover:bg-blue-700 disabled:opacity-50">
                  {createApt.isPending ? 'Đang lưu...' : 'Tạo mới'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {editApt && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
          <div className="absolute inset-0 bg-black/50" onClick={() => setEditApt(null)} />
          <div className="relative bg-white rounded-lg shadow-xl w-full max-w-md p-6">
            <h2 className="text-lg font-semibold mb-4">{t('apartments.editTitle', { unitNumber: editApt.unitNumber })}</h2>
            <form onSubmit={async (e) => {
              e.preventDefault();
              setEditError('');
              const fd = new FormData(e.target as HTMLFormElement);
              try {
                await updateApt.mutateAsync({ id: editApt.id, data: {
                  floor: Number(fd.get('floor')),
                  unitNumber: fd.get('unitNumber') as string,
                  areaSqm: Number(fd.get('areaSqm')),
                  notes: fd.get('notes') as string,
                }});
                setEditApt(null);
              } catch (err: any) {
                setEditError(getVnErrorMessage(err?.response?.data?.error));
              }
            }} className="space-y-3">
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">{t('apartments.block')}</label>
                <div className="block w-full border border-gray-200 rounded-md px-3 py-2 text-sm bg-gray-50 text-gray-600">
                  {editApt.block?.name ?? '—'} <span className="text-xs text-gray-400">(không thể thay đổi)</span>
                </div>
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">{t('apartments.unitNumber')}</label>
                <input name="unitNumber" defaultValue={editApt.unitNumber} className="block w-full border border-gray-300 rounded-md px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">{t('apartments.floor')}</label>
                <input name="floor" type="number" defaultValue={editApt.floor} className="block w-full border border-gray-300 rounded-md px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">{t('apartments.area')}</label>
                <input name="areaSqm" type="number" step="0.1" defaultValue={editApt.areaSqm} className="block w-full border border-gray-300 rounded-md px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" />
              </div>
              <div>
                {/* Status is derived from occupancy — read-only; not client-settable on the BE. */}
                <label className="block text-sm font-medium text-gray-700 mb-1">{t('common.status')}</label>
                <div className="block w-full border border-gray-200 rounded-md px-3 py-2 text-sm bg-gray-50 text-gray-600">
                  {labelFor('ApartmentStatus', editApt.status)} <span className="text-xs text-gray-400">(tự động theo cư dân)</span>
                </div>
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">{t('apartments.notes')}</label>
                <textarea name="notes" defaultValue={editApt.notes ?? ''} className="block w-full border border-gray-300 rounded-md px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" rows={2} />
              </div>
              {editError && <p className="text-sm text-red-600 bg-red-50 px-3 py-2 rounded">{editError}</p>}
              <div className="flex gap-2 justify-end pt-2">
                <button type="button" onClick={() => setEditApt(null)} className="px-4 py-2 text-sm border border-gray-300 rounded-md hover:bg-gray-50">{t('common.cancel')}</button>
                <button type="submit" disabled={updateApt.isPending} className="px-4 py-2 text-sm bg-blue-600 text-white rounded-md hover:bg-blue-700 disabled:opacity-50">
                  {updateApt.isPending ? t('common.saving') : t('common.save')}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
