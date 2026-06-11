import React, { useState } from 'react';
import { getVnErrorMessage, labelFor } from '@gemek/ui';
import { useContractors, useCreateContractor, useUpdateContractor } from '../api/hooks';
import { t } from '../i18n/vi';

const SPECIALTIES = ['CLEANING','SECURITY','ELEVATOR','FIRE_SAFETY','LANDSCAPING','PEST_CONTROL','ELECTRICAL','PLUMBING','OTHER'];

export function ContractorsPage() {
  const [search, setSearch] = useState('');
  const [page, setPage] = useState(0);
  const [modal, setModal] = useState<null | 'create' | any>(null);
  const [formError, setFormError] = useState('');

  const { data, isLoading, isError } = useContractors({ page, size: 20, ...(search && { search }) });
  const createContractor = useCreateContractor();
  const updateContractor = useUpdateContractor();
  const isEdit = modal && modal !== 'create';

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setFormError('');
    const fd = new FormData(e.target as HTMLFormElement);
    const payload = { companyName: fd.get('companyName'), contactPerson: fd.get('contactPerson'), phone: fd.get('phone'), email: fd.get('email'), specialty: fd.get('specialty'), address: fd.get('address'), taxCode: fd.get('taxCode') || null, notes: fd.get('notes') || null };
    if (!payload.companyName) { setFormError('Tên công ty là bắt buộc.'); return; }
    try {
      if (isEdit) await updateContractor.mutateAsync({ id: modal.id, data: payload });
      else await createContractor.mutateAsync(payload);
      setModal(null);
    } catch (err: any) { setFormError(getVnErrorMessage(err?.response?.data?.error)); }
  };

  return (
    <div>
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-2xl font-bold text-gray-900">{t('contractors.title')}</h1>
        <button onClick={() => { setModal('create'); setFormError(''); }} className="bg-blue-600 hover:bg-blue-700 text-white px-4 py-2 rounded-md text-sm font-medium">{t('contractors.add')}</button>
      </div>
      <div className="bg-white rounded-lg border border-gray-200 p-4 mb-4">
        <input value={search} onChange={(e) => { setSearch(e.target.value); setPage(0); }} placeholder={t('contractors.searchPlaceholder')} className="border border-gray-300 rounded-md px-3 py-2 text-sm w-80 focus:outline-none focus:ring-2 focus:ring-blue-500" />
      </div>
      {isError && <div className="bg-red-50 border border-red-200 rounded-lg p-4 text-red-700 mb-4">{t('contractors.loadError')}</div>}
      <div className="bg-white rounded-lg border border-gray-200 overflow-hidden">
        <table className="w-full text-sm">
          <thead className="bg-gray-50 border-b border-gray-200">
            <tr>
              <th className="text-left px-4 py-3 font-medium text-gray-500">{t('contractors.company')}</th>
              <th className="text-left px-4 py-3 font-medium text-gray-500">{t('contractors.contact')}</th>
              <th className="text-left px-4 py-3 font-medium text-gray-500">{t('contractors.phone')}</th>
              <th className="text-left px-4 py-3 font-medium text-gray-500">{t('contractors.specialty')}</th>
              <th className="text-left px-4 py-3 font-medium text-gray-500">{t('contractors.rating')}</th>
              <th className="text-left px-4 py-3 font-medium text-gray-500">{t('common.status')}</th>
              <th className="text-left px-4 py-3 font-medium text-gray-500">{t('common.actions')}</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-100">
            {isLoading && <tr><td colSpan={7} className="text-center py-8 text-gray-400">{t('common.loading')}</td></tr>}
            {!isLoading && !data?.data?.length && <tr><td colSpan={7} className="text-center py-8 text-gray-400">{t('common.emptyFound', { item: 'nhà thầu' })}</td></tr>}
            {data?.data?.map((c: any) => (
              <tr key={c.id} className="hover:bg-gray-50">
                <td className="px-4 py-3 font-medium">{c.companyName}</td>
                <td className="px-4 py-3 text-gray-500">{c.contactPerson ?? '—'}</td>
                <td className="px-4 py-3">{c.phone ?? '—'}</td>
                <td className="px-4 py-3"><span className="inline-flex px-2 py-0.5 rounded-full text-xs font-medium bg-blue-100 text-blue-700">{labelFor('ContractorSpecialty', c.specialty)}</span></td>
                <td className="px-4 py-3">{c.rating ? `${'★'.repeat(Math.round(c.rating))} ${c.rating.toFixed(1)}` : '—'}</td>
                <td className="px-4 py-3"><span className={`inline-flex px-2 py-0.5 rounded-full text-xs font-medium ${c.isActive ? 'bg-green-100 text-green-700' : 'bg-gray-100 text-gray-500'}`}>{labelFor('ActiveStatus', c.isActive ? 'ACTIVE' : 'INACTIVE')}</span></td>
                <td className="px-4 py-3"><button onClick={() => { setModal(c); setFormError(''); }} className="text-blue-600 hover:underline text-xs">{t('common.edit')}</button></td>
              </tr>
            ))}
          </tbody>
        </table>
        <div className="flex items-center justify-between px-4 py-3 border-t border-gray-200">
          <span className="text-xs text-gray-500">{t('common.total')} {data?.total ?? 0}</span>
          <div className="flex gap-2">
            <button onClick={() => setPage((p) => Math.max(0, p - 1))} disabled={page === 0} className="px-3 py-1 text-xs border border-gray-300 rounded hover:bg-gray-50 disabled:opacity-40">{t('common.prev')}</button>
            <span className="px-3 py-1 text-xs">{page + 1} / {data?.totalPages ?? 1}</span>
            <button onClick={() => setPage((p) => p + 1)} disabled={page + 1 >= (data?.totalPages ?? 1)} className="px-3 py-1 text-xs border border-gray-300 rounded hover:bg-gray-50 disabled:opacity-40">{t('common.next')}</button>
          </div>
        </div>
      </div>

      {modal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
          <div className="absolute inset-0 bg-black/50" onClick={() => setModal(null)} />
          <div className="relative bg-white rounded-lg shadow-xl w-full max-w-md p-6">
            <h2 className="text-lg font-semibold mb-4">{isEdit ? t('contractors.editTitle') : t('contractors.addTitle')}</h2>
            <form onSubmit={handleSubmit} className="space-y-3">
              <div><label className="block text-sm font-medium text-gray-700 mb-1">{t('contractors.companyName')} <span className="text-red-500">*</span></label>
                <input name="companyName" defaultValue={isEdit ? modal.companyName : ''} className="block w-full border border-gray-300 rounded-md px-3 py-2 text-sm" /></div>
              <div><label className="block text-sm font-medium text-gray-700 mb-1">{t('contractors.contactPerson')}</label>
                <input name="contactPerson" defaultValue={isEdit ? modal.contactPerson ?? '' : ''} className="block w-full border border-gray-300 rounded-md px-3 py-2 text-sm" /></div>
              <div className="grid grid-cols-2 gap-3">
                <div><label className="block text-sm font-medium text-gray-700 mb-1">{t('contractors.phone')}</label>
                  <input name="phone" defaultValue={isEdit ? modal.phone ?? '' : ''} className="block w-full border border-gray-300 rounded-md px-3 py-2 text-sm" /></div>
                <div><label className="block text-sm font-medium text-gray-700 mb-1">{t('contractors.email')}</label>
                  <input name="email" type="email" defaultValue={isEdit ? modal.email ?? '' : ''} className="block w-full border border-gray-300 rounded-md px-3 py-2 text-sm" /></div>
              </div>
              <div><label className="block text-sm font-medium text-gray-700 mb-1">{t('contractors.specialty')}</label>
                <select name="specialty" defaultValue={isEdit ? modal.specialty : 'OTHER'} className="block w-full border border-gray-300 rounded-md px-3 py-2 text-sm bg-white">
                  {SPECIALTIES.map((s) => <option key={s} value={s}>{labelFor('ContractorSpecialty', s)}</option>)}
                </select></div>
              <div><label className="block text-sm font-medium text-gray-700 mb-1">Địa chỉ</label>
                <input name="address" defaultValue={isEdit ? modal.address ?? '' : ''} className="block w-full border border-gray-300 rounded-md px-3 py-2 text-sm" /></div>
              <div><label className="block text-sm font-medium text-gray-700 mb-1">Mã số thuế</label>
                <input name="taxCode" defaultValue={isEdit ? modal.taxCode ?? '' : ''} className="block w-full border border-gray-300 rounded-md px-3 py-2 text-sm" /></div>
              <div><label className="block text-sm font-medium text-gray-700 mb-1">Ghi chú</label>
                <textarea name="notes" defaultValue={isEdit ? modal.notes ?? '' : ''} rows={3} className="block w-full border border-gray-300 rounded-md px-3 py-2 text-sm resize-none" /></div>
              {formError && <p className="text-sm text-red-600 bg-red-50 px-3 py-2 rounded">{formError}</p>}
              <div className="flex gap-2 justify-end pt-2">
                <button type="button" onClick={() => setModal(null)} className="px-4 py-2 text-sm border border-gray-300 rounded-md hover:bg-gray-50">{t('common.cancel')}</button>
                <button type="submit" disabled={createContractor.isPending || updateContractor.isPending} className="px-4 py-2 text-sm bg-blue-600 text-white rounded-md hover:bg-blue-700 disabled:opacity-50">
                  {createContractor.isPending || updateContractor.isPending ? t('common.saving') : t('common.save')}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
