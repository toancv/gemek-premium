import React, { useState } from 'react';
import { labelFor } from '@gemek/ui';
import { t } from '../i18n/vi';

// Contractor specialty options — kept in sync with the BE ContractorSpecialty enum (moved here from the
// retired list-page modal unchanged). 'OTHER' is the create default, same as the old modal.
export const CONTRACTOR_SPECIALTIES = [
  'CLEANING', 'SECURITY', 'ELEVATOR', 'FIRE_SAFETY', 'LANDSCAPING',
  'PEST_CONTROL', 'ELECTRICAL', 'PLUMBING', 'OTHER',
];

/** Editable contractor form fields, held as form-local strings (parsed on submit). */
export interface ContractorFormValue {
  companyName: string;
  contactPerson: string;
  phone: string;
  email: string;
  specialty: string;
  address: string;
  taxCode: string;
  notes: string;
}

const EMPTY: ContractorFormValue = {
  companyName: '',
  contactPerson: '',
  phone: '',
  email: '',
  specialty: 'OTHER',
  address: '',
  taxCode: '',
  notes: '',
};

/**
 * Owns all contractor compose state, validation, and the request-body builder. Shared by the create
 * (/contractors/new) and edit (/contractors/:id/edit) pages so the field set, validation, and payload
 * shape live in exactly one place (no drift between the two pages) — the same single-source pattern
 * useAnnouncementForm provides for announcements.
 */
export function useContractorForm(initial?: Partial<ContractorFormValue>) {
  const [companyName, setCompanyName] = useState(initial?.companyName ?? EMPTY.companyName);
  const [contactPerson, setContactPerson] = useState(initial?.contactPerson ?? EMPTY.contactPerson);
  const [phone, setPhone] = useState(initial?.phone ?? EMPTY.phone);
  const [email, setEmail] = useState(initial?.email ?? EMPTY.email);
  const [specialty, setSpecialty] = useState(initial?.specialty ?? EMPTY.specialty);
  const [address, setAddress] = useState(initial?.address ?? EMPTY.address);
  const [taxCode, setTaxCode] = useState(initial?.taxCode ?? EMPTY.taxCode);
  const [notes, setNotes] = useState(initial?.notes ?? EMPTY.notes);
  const [formError, setFormError] = useState('');

  // Validates the single rule the retired modal enforced: company name is mandatory. Same VN message.
  const validate = (): boolean => {
    setFormError('');
    if (!companyName.trim()) { setFormError('Tên công ty là bắt buộc.'); return false; }
    return true;
  };

  // Builds the create/update request body — identical shape to the retired modal (empty taxCode/notes
  // collapse to null so the BE stores absence rather than an empty string).
  const toPayload = () => ({
    companyName: companyName.trim(),
    contactPerson,
    phone,
    email,
    specialty,
    address,
    taxCode: taxCode.trim() || null,
    notes: notes.trim() || null,
  });

  return {
    companyName, setCompanyName,
    contactPerson, setContactPerson,
    phone, setPhone,
    email, setEmail,
    specialty, setSpecialty,
    address, setAddress,
    taxCode, setTaxCode,
    notes, setNotes,
    formError, setFormError,
    validate,
    toPayload,
  };
}

export type ContractorForm = ReturnType<typeof useContractorForm>;

/**
 * The contractor form fields shared by both authoring pages — the exact field set, labels, and layout
 * of the retired list-page modal, now controlled (state lives in useContractorForm). No documents/upload
 * UI here: the documents manager is appended by the edit page only (P3).
 */
export function ContractorFormFields({ form }: { form: ContractorForm }) {
  return (
    <div className="space-y-3">
      <div>
        <label className="block text-sm font-medium text-gray-700 mb-1">{t('contractors.companyName')} <span className="text-red-500">*</span></label>
        <input value={form.companyName} onChange={(e) => form.setCompanyName(e.target.value)} className="block w-full border border-gray-300 rounded-md px-3 py-2 text-sm" />
      </div>
      <div>
        <label className="block text-sm font-medium text-gray-700 mb-1">{t('contractors.contactPerson')}</label>
        <input value={form.contactPerson} onChange={(e) => form.setContactPerson(e.target.value)} className="block w-full border border-gray-300 rounded-md px-3 py-2 text-sm" />
      </div>
      <div className="grid grid-cols-2 gap-3">
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">{t('contractors.phone')}</label>
          <input value={form.phone} onChange={(e) => form.setPhone(e.target.value)} className="block w-full border border-gray-300 rounded-md px-3 py-2 text-sm" />
        </div>
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">{t('contractors.email')}</label>
          <input value={form.email} onChange={(e) => form.setEmail(e.target.value)} type="email" className="block w-full border border-gray-300 rounded-md px-3 py-2 text-sm" />
        </div>
      </div>
      <div>
        <label className="block text-sm font-medium text-gray-700 mb-1">{t('contractors.specialty')}</label>
        <select value={form.specialty} onChange={(e) => form.setSpecialty(e.target.value)} className="block w-full border border-gray-300 rounded-md px-3 py-2 text-sm bg-white">
          {CONTRACTOR_SPECIALTIES.map((s) => <option key={s} value={s}>{labelFor('ContractorSpecialty', s)}</option>)}
        </select>
      </div>
      <div>
        <label className="block text-sm font-medium text-gray-700 mb-1">Địa chỉ</label>
        <input value={form.address} onChange={(e) => form.setAddress(e.target.value)} className="block w-full border border-gray-300 rounded-md px-3 py-2 text-sm" />
      </div>
      <div>
        <label className="block text-sm font-medium text-gray-700 mb-1">Mã số thuế</label>
        <input value={form.taxCode} onChange={(e) => form.setTaxCode(e.target.value)} className="block w-full border border-gray-300 rounded-md px-3 py-2 text-sm" />
      </div>
      <div>
        <label className="block text-sm font-medium text-gray-700 mb-1">Ghi chú</label>
        <textarea value={form.notes} onChange={(e) => form.setNotes(e.target.value)} rows={3} className="block w-full border border-gray-300 rounded-md px-3 py-2 text-sm resize-none" />
      </div>
    </div>
  );
}
