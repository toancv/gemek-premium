import React, { useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { getVnErrorMessage, PageSpinner } from '@gemek/ui';
import { useContractor, useUpdateContractor } from '../api/hooks';
import { useContractorForm, ContractorFormFields } from '../components/ContractorForm';
import { t } from '../i18n/vi';

/**
 * Loader/guard wrapper for the contractor edit page. Resolves the contractor before mounting the form so
 * the form's initial values are correct on first render (no async reset), mirroring AnnouncementEditPage.
 * Handles loading and not-found gracefully.
 */
export function ContractorEditPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { data, isLoading, isError } = useContractor(id!);

  if (isLoading) {
    return <PageSpinner />;
  }

  if (isError || !data) {
    return (
      <div className="w-full max-w-2xl">
        <div className="bg-red-50 border border-red-200 rounded-lg p-4 text-red-700 mb-4">Không tìm thấy nhà thầu.</div>
        <button type="button" onClick={() => navigate('/contractors')} className="px-4 py-2 text-sm border border-gray-300 rounded-md hover:bg-gray-50">Quay lại danh sách</button>
      </div>
    );
  }

  // key on the contractor id so navigating between two /:id/edit URLs remounts the form with fresh
  // initial values rather than reusing the previous contractor's state.
  return <ContractorEditForm key={data.id} contractor={data} />;
}

/**
 * The actual edit form, mounted only once the contractor is loaded. "Lưu" updates and STAYS on the page
 * (the edit page is where P3's documents/upload section will live), with the MutationCache top-right
 * toast (meta.successMessage on useUpdateContractor) confirming success; errors render inline.
 */
function ContractorEditForm({ contractor }: { contractor: any }) {
  const navigate = useNavigate();
  const update = useUpdateContractor();
  const [submitting, setSubmitting] = useState(false);

  const form = useContractorForm({
    companyName: contractor.companyName ?? '',
    contactPerson: contractor.contactPerson ?? '',
    phone: contractor.phone ?? '',
    email: contractor.email ?? '',
    specialty: contractor.specialty ?? 'OTHER',
    address: contractor.address ?? '',
    taxCode: contractor.taxCode ?? '',
    notes: contractor.notes ?? '',
  });

  const save = async () => {
    if (!form.validate()) return;
    setSubmitting(true);
    try {
      await update.mutateAsync({ id: contractor.id, data: form.toPayload() });
    } catch (err: any) {
      form.setFormError(getVnErrorMessage(err?.response?.data?.error));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="w-full max-w-2xl">
      <div className="mb-6">
        <button type="button" onClick={() => navigate('/contractors')} className="text-sm text-blue-600 hover:underline">← Quay lại danh sách</button>
        <h1 className="text-2xl font-bold text-gray-900 mt-2">{t('contractors.editTitle')}</h1>
      </div>

      {/* <form> so Enter in any text field submits (restores the retired modal's Enter-to-save). */}
      <form onSubmit={(e) => { e.preventDefault(); save(); }}>
        <ContractorFormFields form={form} />

        {/* P3 will append the contractor documents/upload section below the form fields here. */}

        {form.formError && <p className="mt-4 text-sm text-red-600 bg-red-50 px-3 py-2 rounded">{form.formError}</p>}

        <div className="flex gap-2 justify-end pt-6 mt-4 border-t border-gray-200">
          <button type="button" onClick={() => navigate('/contractors')} className="px-4 py-2 text-sm border border-gray-300 rounded-md hover:bg-gray-50">{t('common.cancel')}</button>
          <button type="submit" disabled={submitting} className="px-4 py-2 text-sm bg-blue-600 text-white rounded-md hover:bg-blue-700 disabled:opacity-50">
            {submitting ? t('common.saving') : t('common.save')}
          </button>
        </div>
      </form>
    </div>
  );
}
