import React, { useState } from 'react';
import { SearchableSelect } from '@gemek/ui';
import { useApartments, useBlocks, useCreateApartment, useUpdateApartment } from '../api/hooks';

export function ApartmentsPage() {
  const [search, setSearch] = useState('');
  const [status, setStatus] = useState('');
  const [blockId, setBlockId] = useState('');
  const [page, setPage] = useState(0);
  const [editApt, setEditApt] = useState<any>(null);
  const [showCreate, setShowCreate] = useState(false);
  const [newBlockId, setNewBlockId] = useState('');
  const [blockError, setBlockError] = useState('');

  const params = { page, size: 20, ...(search && { search }), ...(status && { status }), ...(blockId && { blockId }) };
  const { data, isLoading, isError } = useApartments(params);
  const { data: blocksData, isLoading: blocksLoading } = useBlocks();
  const createApt = useCreateApartment();
  const updateApt = useUpdateApartment();

  const blockOptions = (blocksData?.data ?? []).map((b: any) => ({ value: b.id, label: b.name }));

  const statusColors: Record<string, string> = {
    OCCUPIED: 'bg-green-100 text-green-700',
    AVAILABLE: 'bg-blue-100 text-blue-700',
    MAINTENANCE: 'bg-yellow-100 text-yellow-700',
  };

  function openCreate() {
    setNewBlockId('');
    setBlockError('');
    setShowCreate(true);
  }

  return (
    <div>
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-2xl font-bold text-gray-900">Apartments</h1>
        <button onClick={openCreate}
          className="px-4 py-2 text-sm bg-blue-600 text-white rounded-md hover:bg-blue-700">
          + Thêm căn hộ
        </button>
      </div>

      <div className="bg-white rounded-lg border border-gray-200 p-4 mb-4 flex gap-3">
        <input value={search} onChange={(e) => { setSearch(e.target.value); setPage(0); }}
          placeholder="Search unit number..." className="border border-gray-300 rounded-md px-3 py-2 text-sm flex-1 focus:outline-none focus:ring-2 focus:ring-blue-500" />
        <select value={status} onChange={(e) => { setStatus(e.target.value); setPage(0); }}
          className="border border-gray-300 rounded-md px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500">
          <option value="">All Statuses</option>
          <option value="OCCUPIED">Occupied</option>
          <option value="AVAILABLE">Available</option>
          <option value="MAINTENANCE">Maintenance</option>
        </select>
        <select value={blockId} onChange={(e) => { setBlockId(e.target.value); setPage(0); }}
          className="border border-gray-300 rounded-md px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500">
          <option value="">All Blocks</option>
          {blocksData?.data?.map((b: any) => <option key={b.id} value={b.id}>{b.name}</option>)}
        </select>
      </div>

      {isError && <div className="bg-red-50 border border-red-200 rounded-lg p-4 text-red-700 mb-4">Failed to load apartments.</div>}

      <div className="bg-white rounded-lg border border-gray-200 overflow-hidden">
        <table className="w-full text-sm">
          <thead className="bg-gray-50 border-b border-gray-200">
            <tr>
              <th className="text-left px-4 py-3 font-medium text-gray-500">Block</th>
              <th className="text-left px-4 py-3 font-medium text-gray-500">Unit</th>
              <th className="text-left px-4 py-3 font-medium text-gray-500">Floor</th>
              <th className="text-left px-4 py-3 font-medium text-gray-500">Area (sqm)</th>
              <th className="text-left px-4 py-3 font-medium text-gray-500">Status</th>
              <th className="text-left px-4 py-3 font-medium text-gray-500">Primary Contact</th>
              <th className="text-left px-4 py-3 font-medium text-gray-500">Actions</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-100">
            {isLoading && <tr><td colSpan={7} className="text-center py-8 text-gray-400">Loading...</td></tr>}
            {!isLoading && !data?.data?.length && <tr><td colSpan={7} className="text-center py-8 text-gray-400">No apartments found</td></tr>}
            {data?.data?.map((apt: any) => (
              <tr key={apt.id} className="hover:bg-gray-50">
                <td className="px-4 py-3">{apt.block?.name}</td>
                <td className="px-4 py-3 font-medium">{apt.unitNumber}</td>
                <td className="px-4 py-3">{apt.floor}</td>
                <td className="px-4 py-3">{apt.areaSqm}</td>
                <td className="px-4 py-3">
                  <span className={`inline-flex px-2 py-0.5 rounded-full text-xs font-medium ${statusColors[apt.status] ?? 'bg-gray-100 text-gray-700'}`}>{apt.status}</span>
                </td>
                <td className="px-4 py-3">{apt.primaryContact?.fullName ?? '—'}</td>
                <td className="px-4 py-3">
                  <button onClick={() => setEditApt(apt)} className="text-blue-600 hover:underline text-xs">Edit</button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        <div className="flex items-center justify-between px-4 py-3 border-t border-gray-200">
          <span className="text-xs text-gray-500">Total: {data?.total ?? 0}</span>
          <div className="flex gap-2">
            <button onClick={() => setPage((p) => Math.max(0, p - 1))} disabled={page === 0}
              className="px-3 py-1 text-xs border border-gray-300 rounded hover:bg-gray-50 disabled:opacity-40">Prev</button>
            <span className="px-3 py-1 text-xs">{page + 1} / {data?.totalPages ?? 1}</span>
            <button onClick={() => setPage((p) => p + 1)} disabled={page + 1 >= (data?.totalPages ?? 1)}
              className="px-3 py-1 text-xs border border-gray-300 rounded hover:bg-gray-50 disabled:opacity-40">Next</button>
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
              if (!newBlockId) { setBlockError('Vui lòng chọn block.'); return; }
              setBlockError('');
              const fd = new FormData(e.target as HTMLFormElement);
              await createApt.mutateAsync({
                blockId: newBlockId,
                floor: Number(fd.get('floor')),
                unitNumber: fd.get('unitNumber') as string,
                areaSqm: Number(fd.get('areaSqm')),
                notes: (fd.get('notes') as string) || undefined,
              });
              setShowCreate(false);
              setNewBlockId('');
            }} className="space-y-3">
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Block <span className="text-red-500">*</span></label>
                <SearchableSelect
                  options={blockOptions}
                  value={newBlockId}
                  onChange={(v) => { setNewBlockId(v); setBlockError(''); }}
                  loading={blocksLoading}
                  placeholder="Chọn block..."
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
                <label className="block text-sm font-medium text-gray-700 mb-1">Diện tích (sqm)</label>
                <input name="areaSqm" type="number" step="0.1" required className="block w-full border border-gray-300 rounded-md px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Ghi chú</label>
                <textarea name="notes" className="block w-full border border-gray-300 rounded-md px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" rows={2} />
              </div>
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
            <h2 className="text-lg font-semibold mb-4">Edit Apartment {editApt.unitNumber}</h2>
            <form onSubmit={async (e) => {
              e.preventDefault();
              const fd = new FormData(e.target as HTMLFormElement);
              await updateApt.mutateAsync({ id: editApt.id, data: {
                floor: Number(fd.get('floor')),
                unitNumber: fd.get('unitNumber') as string,
                areaSqm: Number(fd.get('areaSqm')),
                status: fd.get('status') as string,
                notes: fd.get('notes') as string,
              }});
              setEditApt(null);
            }} className="space-y-3">
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Block</label>
                <div className="block w-full border border-gray-200 rounded-md px-3 py-2 text-sm bg-gray-50 text-gray-600">
                  {editApt.block?.name ?? '—'} <span className="text-xs text-gray-400">(không thể thay đổi)</span>
                </div>
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Unit Number</label>
                <input name="unitNumber" defaultValue={editApt.unitNumber} className="block w-full border border-gray-300 rounded-md px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Floor</label>
                <input name="floor" type="number" defaultValue={editApt.floor} className="block w-full border border-gray-300 rounded-md px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Area (sqm)</label>
                <input name="areaSqm" type="number" step="0.1" defaultValue={editApt.areaSqm} className="block w-full border border-gray-300 rounded-md px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Status</label>
                <select name="status" defaultValue={editApt.status} className="block w-full border border-gray-300 rounded-md px-3 py-2 text-sm bg-white focus:outline-none focus:ring-2 focus:ring-blue-500">
                  <option value="OCCUPIED">Occupied</option>
                  <option value="AVAILABLE">Available</option>
                  <option value="MAINTENANCE">Maintenance</option>
                </select>
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Notes</label>
                <textarea name="notes" defaultValue={editApt.notes ?? ''} className="block w-full border border-gray-300 rounded-md px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" rows={2} />
              </div>
              <div className="flex gap-2 justify-end pt-2">
                <button type="button" onClick={() => setEditApt(null)} className="px-4 py-2 text-sm border border-gray-300 rounded-md hover:bg-gray-50">Cancel</button>
                <button type="submit" disabled={updateApt.isPending} className="px-4 py-2 text-sm bg-blue-600 text-white rounded-md hover:bg-blue-700 disabled:opacity-50">
                  {updateApt.isPending ? 'Saving...' : 'Save'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
