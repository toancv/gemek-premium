import React, { useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useQueryClient } from '@tanstack/react-query';
import { getVnErrorMessage, toast } from '@gemek/ui';
import { useCreateContractor, useUploadContractorDocument } from '../api/hooks';
import { useContractorForm, ContractorFormFields } from '../components/ContractorForm';
import { ContractorDocumentsManager } from '../components/ContractorDocumentsManager';
import { t } from '../i18n/vi';

/**
 * Admin contractor create page (replaces the retired list-page modal). On success it redirects to the
 * new record's edit page — NOT back to the list — so P3's documents/upload section (which lives on the
 * edit page) is immediately reachable for the just-created contractor (CTO-approved save flow). Success
 * feedback is the MutationCache top-right toast (meta.successMessage on useCreateContractor); errors
 * render inline via getVnErrorMessage. Mirrors AnnouncementCreatePage's save-first flow + guards.
 *
 * P3b LAZY-SAVE (ensure-record-then-upload): the documents manager is mounted here too. With no contractor
 * id yet, the FIRST file pick validates the form → creates the contractor (immediately active, NO draft) →
 * uploads the file → navigate(replace) to /:id/edit (where the P3a manager owns all further uploads). A
 * synchronous in-flight ref guarantees exactly ONE create even under a double-click or a quick second pick;
 * a create failure surfaces inline and leaves NO phantom id (a retry re-attempts create, never double-creates).
 */
export function ContractorCreatePage() {
  const navigate = useNavigate();
  const qc = useQueryClient();
  const form = useContractorForm();
  const create = useCreateContractor();
  const uploadDoc = useUploadContractorDocument();
  const [submitting, setSubmitting] = useState(false);
  // True while a lazy create+upload is in flight — drives the manager's disabled upload trigger and the
  // save button, so the explicit-save path and the lazy path can never run at the same time.
  const [lazyBusy, setLazyBusy] = useState(false);
  // Hard single-create guard (synchronous ref, NOT state): a double-click, Enter-then-click, or a quick
  // second picked file can fire before `submitting`/`lazyBusy` re-render the disabled controls, so without
  // this two POSTs would create two duplicate contractors. The ref updates synchronously → the second call
  // bails. ONE of {explicit save, lazy upload} runs at a time. Same guarantee AnnouncementCreatePage gives.
  const inFlight = useRef(false);
  const busy = submitting || lazyBusy;

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

  // VN notice for an upload failure AFTER the contractor was created — carried to the edit page (the record
  // survived; the admin retries there). A 413 has no coded body, so map it to the same size message the
  // manager's errorText uses (DECISIONS 2026-06-28: 413-only, no 400 size branch).
  const uploadErrNotice = (err: any): string => {
    const detail = err?.response?.status === 413
      ? 'Tệp quá lớn (tối đa 10MB mỗi tệp).'
      : getVnErrorMessage(err?.response?.data?.error);
    return `Nhà thầu đã được tạo nhưng tải lên tài liệu thất bại: ${detail}`;
  };

  // Lazy-save orchestrator (ensure-record-then-upload). The file-size/total pre-checks have ALREADY run in
  // the manager before this point, so an oversize file never reaches here and never creates a contractor.
  //  - invalid form → toast + inline error, NO create, NO upload (the inline error sits above the manager
  //    and may be off-screen, so a toast carries the WHY too — mirrors AnnouncementCreatePage).
  //  - create fails → inline error, reset guards, NO phantom id (a retry re-attempts create).
  //  - create OK → seed cache → upload → navigate(replace) to /:id/edit. Upload fails → still navigate
  //    (the record IS saved) carrying the error as a notice. navigate(replace) unmounts /new → no reset.
  const ensureContractorThenUpload = async (file: File): Promise<void> => {
    if (inFlight.current) return;          // single-create concurrency guard (synchronous)
    if (!form.validate()) {
      toast.error('Vui lòng nhập tên công ty trước khi tải lên tài liệu.');
      return;                              // invalid → no create, no upload
    }
    inFlight.current = true;
    setLazyBusy(true);
    let created: any;
    try {
      created = await create.mutateAsync(form.toPayload());
    } catch (err: any) {
      form.setFormError(getVnErrorMessage(err?.response?.data?.error));
      inFlight.current = false;
      setLazyBusy(false);
      return;                              // create failed → no orphan id, no upload; retry re-creates
    }
    qc.setQueryData(['contractors', created.id], created);
    try {
      await uploadDoc.mutateAsync({ id: created.id, file });
      navigate(`/contractors/${created.id}/edit`, { replace: true });
    } catch (upErr: any) {
      navigate(`/contractors/${created.id}/edit`, {
        replace: true,
        state: { notice: uploadErrNotice(upErr) },
      });
    }
    // navigate(replace) unmounts /new — no inFlight/lazyBusy reset needed (this component is gone).
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
          <button type="submit" disabled={busy} className="px-4 py-2 text-sm bg-blue-600 text-white rounded-md hover:bg-blue-700 disabled:opacity-50">
            {submitting ? t('common.saving') : t('common.save')}
          </button>
        </div>
      </form>

      {/* Documents section — lazy-save (no contractorId yet): the first pick creates the contractor then
          uploads, then redirects to the edit page where the P3a manager takes over. externalBusy disables
          the trigger while that create+upload runs. */}
      <ContractorDocumentsManager onLazyUpload={ensureContractorThenUpload} externalBusy={busy} />
    </div>
  );
}
