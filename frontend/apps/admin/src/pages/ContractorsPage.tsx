import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { labelFor } from '@gemek/ui';
import { useContractors } from '../api/hooks';
import { useRoleFlags } from '../lib/useRoleFlags';
import { t } from '../i18n/vi';

export function ContractorsPage() {
  const navigate = useNavigate();
  const [search, setSearch] = useState('');
  const [page, setPage] = useState(0);

  // Contractor create/edit are ADMIN-only on the BE (POST + PUT /contractors = hasRole('ADMIN')).
  // Hide both write controls from BOARD_MEMBER (read/oversight) — backlog (c) 403 fix, Direction A.
  const { isAdmin } = useRoleFlags();

  const { data, isLoading, isError } = useContractors({ page, size: 20, ...(search && { search }) });

  return (
    <div>
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-2xl font-bold text-gray-900">{t('contractors.title')}</h1>
        {isAdmin && (
          <button onClick={() => navigate('/contractors/new')} className="bg-blue-600 hover:bg-blue-700 text-white px-4 py-2 rounded-md text-sm font-medium">{t('contractors.add')}</button>
        )}
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
                <td className="px-4 py-3">{isAdmin && (<button onClick={() => navigate(`/contractors/${c.id}/edit`)} className="text-blue-600 hover:underline text-xs">{t('common.edit')}</button>)}</td>
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
    </div>
  );
}
