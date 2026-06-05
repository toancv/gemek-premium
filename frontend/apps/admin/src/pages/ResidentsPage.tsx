import React, { useState } from 'react';
import { SearchableSelect } from '@gemek/ui';
import { useResidents, useCreateResident, useUsers, useApartments } from '../api/hooks';

export function ResidentsPage() {
  const [search, setSearch] = useState('');
  const [page, setPage] = useState(0);
  const [showCreate, setShowCreate] = useState(false);
  const [formError, setFormError] = useState('');
  const [selectedUserId, setSelectedUserId] = useState('');
  const [selectedApartmentId, setSelectedApartmentId] = useState('');
  const [userError, setUserError] = useState('');
  const [aptError, setAptError] = useState('');

  const { data, isLoading, isError } = useResidents({ page, size: 20, ...(search && { search }) });
  const createResident = useCreateResident();
  const { data: usersData, isLoading: usersLoading } = useUsers({ size: 200 });
  const { data: aptsData, isLoading: aptsLoading } = useApartments({ size: 200, sort: 'unitNumber' });

  const userOptions = (usersData?.data ?? []).map((u: any) => ({
    value: u.id,
    label: `${u.fullName} — ${u.email}`,
  }));

  const apartmentOptions = (aptsData?.data ?? []).map((a: any) => ({
    value: a.id,
    label: `${a.block?.name ?? ''} - ${a.unitNumber}`,
  }));

  function openCreate() {
    setSelectedUserId('');
    setSelectedApartmentId('');
    setUserError('');
    setAptError('');
    setFormError('');
    setShowCreate(true);
  }

  const handleCreate = async (e: React.FormEvent) => {
    e.preventDefault();
    setFormError('');
    let valid = true;
    if (!selectedUserId) { setUserError('Vui lòng chọn người dùng.'); valid = false; }
    if (!selectedApartmentId) { setAptError('Vui lòng chọn căn hộ.'); valid = false; }
    if (!valid) return;
    const fd = new FormData(e.target as HTMLFormElement);
    const moveInDate = fd.get('moveInDate') as string;
    if (!moveInDate) { setFormError('Vui lòng chọn ngày chuyển vào.'); return; }
    try {
      await createResident.mutateAsync({
        userId: selectedUserId,
        apartmentId: selectedApartmentId,
        type: fd.get('type'),
        moveInDate,
        isPrimaryContact: fd.get('isPrimaryContact') === 'true',
      });
      setShowCreate(false);
    } catch (err: any) {
      if (err?.response?.status === 409) {
        setFormError('Người dùng này đã là cư dân đang hoạt động của một căn hộ khác.');
      } else {
        setFormError(err?.response?.data?.message ?? 'Tạo cư dân thất bại.');
      }
    }
  };

  return (
    <div>
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-2xl font-bold text-gray-900">Residents</h1>
        <button onClick={openCreate} className="bg-blue-600 hover:bg-blue-700 text-white px-4 py-2 rounded-md text-sm font-medium">
          Add Resident
        </button>
      </div>

      <div className="bg-white rounded-lg border border-gray-200 p-4 mb-4">
        <input value={search} onChange={(e) => { setSearch(e.target.value); setPage(0); }}
          placeholder="Search by name or email..." className="border border-gray-300 rounded-md px-3 py-2 text-sm w-80 focus:outline-none focus:ring-2 focus:ring-blue-500" />
      </div>

      {isError && <div className="bg-red-50 border border-red-200 rounded-lg p-4 text-red-700 mb-4">Failed to load residents.</div>}

      <div className="bg-white rounded-lg border border-gray-200 overflow-hidden">
        <table className="w-full text-sm">
          <thead className="bg-gray-50 border-b border-gray-200">
            <tr>
              <th className="text-left px-4 py-3 font-medium text-gray-500">Name</th>
              <th className="text-left px-4 py-3 font-medium text-gray-500">Email</th>
              <th className="text-left px-4 py-3 font-medium text-gray-500">Apartment</th>
              <th className="text-left px-4 py-3 font-medium text-gray-500">Type</th>
              <th className="text-left px-4 py-3 font-medium text-gray-500">Move-in Date</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-100">
            {isLoading && <tr><td colSpan={5} className="text-center py-8 text-gray-400">Loading...</td></tr>}
            {!isLoading && !data?.data?.length && <tr><td colSpan={5} className="text-center py-8 text-gray-400">No residents found</td></tr>}
            {data?.data?.map((r: any) => (
              <tr key={r.id} className="hover:bg-gray-50">
                <td className="px-4 py-3 font-medium">{r.user?.fullName ?? r.fullName}</td>
                <td className="px-4 py-3 text-gray-500">{r.user?.email ?? r.email}</td>
                <td className="px-4 py-3">{r.apartment?.unitNumber}</td>
                <td className="px-4 py-3">
                  <span className={`inline-flex px-2 py-0.5 rounded-full text-xs font-medium ${r.type === 'OWNER' ? 'bg-purple-100 text-purple-700' : 'bg-blue-100 text-blue-700'}`}>{r.type}</span>
                </td>
                <td className="px-4 py-3">{r.moveInDate}</td>
              </tr>
            ))}
          </tbody>
        </table>
        <div className="flex items-center justify-between px-4 py-3 border-t border-gray-200">
          <span className="text-xs text-gray-500">Total: {data?.total ?? 0}</span>
          <div className="flex gap-2">
            <button onClick={() => setPage((p) => Math.max(0, p - 1))} disabled={page === 0} className="px-3 py-1 text-xs border border-gray-300 rounded hover:bg-gray-50 disabled:opacity-40">Prev</button>
            <span className="px-3 py-1 text-xs">{page + 1} / {data?.totalPages ?? 1}</span>
            <button onClick={() => setPage((p) => p + 1)} disabled={page + 1 >= (data?.totalPages ?? 1)} className="px-3 py-1 text-xs border border-gray-300 rounded hover:bg-gray-50 disabled:opacity-40">Next</button>
          </div>
        </div>
      </div>

      {showCreate && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
          <div className="absolute inset-0 bg-black/50" onClick={() => setShowCreate(false)} />
          <div className="relative bg-white rounded-lg shadow-xl w-full max-w-md p-6">
            <h2 className="text-lg font-semibold mb-4">Add Resident</h2>
            <form onSubmit={handleCreate} className="space-y-3">
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Người dùng <span className="text-red-500">*</span></label>
                <SearchableSelect
                  options={userOptions}
                  value={selectedUserId}
                  onChange={(v) => { setSelectedUserId(v); setUserError(''); }}
                  loading={usersLoading}
                  placeholder="Chọn người dùng..."
                  error={userError}
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Căn hộ <span className="text-red-500">*</span></label>
                <SearchableSelect
                  options={apartmentOptions}
                  value={selectedApartmentId}
                  onChange={(v) => { setSelectedApartmentId(v); setAptError(''); }}
                  loading={aptsLoading}
                  placeholder="Chọn căn hộ..."
                  error={aptError}
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Loại</label>
                <select name="type" className="block w-full border border-gray-300 rounded-md px-3 py-2 text-sm bg-white focus:outline-none focus:ring-2 focus:ring-blue-500">
                  <option value="OWNER">Owner</option><option value="TENANT">Tenant</option>
                </select>
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Ngày chuyển vào <span className="text-red-500">*</span></label>
                <input name="moveInDate" type="date" className="block w-full border border-gray-300 rounded-md px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Liên hệ chính</label>
                <select name="isPrimaryContact" className="block w-full border border-gray-300 rounded-md px-3 py-2 text-sm bg-white focus:outline-none focus:ring-2 focus:ring-blue-500">
                  <option value="false">No</option><option value="true">Yes</option>
                </select>
              </div>
              {formError && <p className="text-sm text-red-600 bg-red-50 px-3 py-2 rounded">{formError}</p>}
              <div className="flex gap-2 justify-end pt-2">
                <button type="button" onClick={() => setShowCreate(false)} className="px-4 py-2 text-sm border border-gray-300 rounded-md hover:bg-gray-50">Hủy</button>
                <button type="submit" disabled={createResident.isPending} className="px-4 py-2 text-sm bg-blue-600 text-white rounded-md hover:bg-blue-700 disabled:opacity-50">
                  {createResident.isPending ? 'Đang lưu...' : 'Tạo mới'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
