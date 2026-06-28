import React, { useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useQueryClient } from '@tanstack/react-query';
import { getVnErrorMessage } from '@gemek/ui';
import { useCreateContractor } from '../api/hooks';
import { useContractorForm, ContractorFormFields } from '../components/ContractorForm';
import { t } from '../i18n/vi';

/**
 * Admin contractor create page (replaces the retired list-page modal). On success it redirects to the
 * new record's edit page — NOT back to the list — so P3's documents/upload section (which lives on the
 * edit page) is immediately reachable for the just-created contractor (CTO-approved save flow). Success
 * feedback is the MutationCache top-right toast (meta.successMessage on useCreateContractor); errors
 * render inline via getVnErrorMessage. Mirrors AnnouncementCreatePage's save-first flow + guards.
 */
export function ContractorCreatePage() {
  const navigate = useNavigate();
  const qc = useQueryClient();
  const form = useContractorForm();
  const create = useCreateContractor();
  const [submitting, setSubmitting] = useState(false);
  // Hard single-submit guard (synchronous ref, NOT state): a double-click or Enter-then-click in the
  // same tick can fire before `submitting` re-renders the disabled button, so without this two POSTs
  // would create two duplicate contractors. The ref updates synchronously → the second call bails.
  // Same guarantee AnnouncementCreatePage's `inFlight` ref provides.
  const inFlight = useRef(false);

  const submit = async () => {
    if (inFlight.current) return;
    if (!form.validate()) return;
    inFlight.current = true;
    setSubmitting(true);
    try {
      const created: any = await create.mutateAsync(form.toPayload());
      // Seed the detail cache so the edit page renders instantly from the create response instead of
      // round-tripping GET /contractors/{id} (and never flashes the transient not-found state on a slow
      // network). Mirrors AnnouncementCreatePage's qc.setQueryData before navigate.
      qc.setQueryData(['contractors', created.id], created);
      // Redirect to the new record's edit page (sets up P3 documents/upload), not back to the list.
      navigate(`/contractors/${created.id}/edit`);
    } catch (err: any) {
      form.setFormError(getVnErrorMessage(err?.response?.data?.error));
      // Only reset the guard on failure — success unmounts this page via navigate.
      inFlight.current = false;
      setSubmitting(false);
    }
  };

  return (
    <div className="w-full max-w-2xl">
      <div className="mb-6">
        <button type="button" onClick={() => navigate('/contractors')} className="text-sm text-blue-600 hover:underline">← Quay lại danh sách</button>
        <h1 className="text-2xl font-bold text-gray-900 mt-2">{t('contractors.addTitle')}</h1>
      </div>

      {/* <form> so Enter in any text field submits (restores the retired modal's Enter-to-save). */}
      <form onSubmit={(e) => { e.preventDefault(); submit(); }}>
        <ContractorFormFields form={form} />

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
