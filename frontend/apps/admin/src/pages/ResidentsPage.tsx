import React, { useState, useCallback } from 'react';
import { SearchableSelect, labelFor, formatVNDate, VNDatePicker, getVnErrorMessage, toISODateLocal } from '@gemek/ui';
import type { SearchableOption } from '@gemek/ui';
import { useResidents, useCreateResident, useMoveOutResident } from '../api/hooks';
import { apiClient } from '../api/client';
import { t } from '../i18n/vi';

interface ResidentItem {
  id: string;
  user: { fullName: string; phone: string; email: string | null };
  apartment: { unitNumber: string; block?: { name: string } };
  type: 'OWNER' | 'TENANT';
  moveInDate: string;
  // null = đang cư trú; non-null ISO date = đã chuyển đi (soft end-of-residency).
  moveOutDate: string | null;
}

/** Generates a random password satisfying: ≥8 chars, upper+lower+digit+special. */
function generatePassword(): string {
  const upper = 'ABCDEFGHJKLMNPQRSTUVWXYZ';
  const lower = 'abcdefghjkmnpqrstuvwxyz';
  const digits = '23456789';
  const special = '!@#$%&*';
  const all = upper + lower + digits + special;
  const rand = (s: string) => s[Math.floor(Math.random() * s.length)];
  const core = [rand(upper), rand(lower), rand(digits), rand(special)];
  for (let i = 0; i < 8; i++) core.push(rand(all));
  return core.sort(() => Math.random() - 0.5).join('').slice(0, 12);
}

export function ResidentsPage() {
  const [search, setSearch] = useState('');
  const [page, setPage] = useState(0);
  const [showCreate, setShowCreate] = useState(false);
  const [formError, setFormError] = useState('');

  // New-user inline fields
  const [fullName, setFullName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [phone, setPhone] = useState('');
  const [dateOfBirth, setDateOfBirth] = useState('');
  // ISO yyyy-mm-dd state — VNDatePicker shows dd/mm but the payload stays ISO
  const [moveInDate, setMoveInDate] = useState('');
  const [generatedPassword, setGeneratedPassword] = useState('');

  // Field errors
  const [fullNameError, setFullNameError] = useState('');
  const [emailError, setEmailError] = useState('');
  const [passwordError, setPasswordError] = useState('');
  const [phoneError, setPhoneError] = useState('');
  const [dobError, setDobError] = useState('');

  // Resident fields
  const [selectedApartmentId, setSelectedApartmentId] = useState('');
  const [aptError, setAptError] = useState('');

  // Move-out ("Kết thúc cư trú") dialog state. moveOutDate stays ISO (yyyy-mm-dd),
  // defaulted to today; VNDatePicker shows dd/mm but the payload is a pure LocalDate.
  const [moveOutTarget, setMoveOutTarget] = useState<ResidentItem | null>(null);
  const [moveOutDate, setMoveOutDate] = useState('');
  const [moveOutNotes, setMoveOutNotes] = useState('');
  const [moveOutError, setMoveOutError] = useState('');

  const { data, isLoading, isError } = useResidents({ page, size: 20, ...(search && { search }) });
  const createResident = useCreateResident();
  const moveOutResident = useMoveOutResident();

  function openMoveOut(resident: ResidentItem) {
    setMoveOutTarget(resident);
    setMoveOutDate(toISODateLocal(new Date())); // default = hôm nay, vẫn sửa được
    setMoveOutNotes('');
    setMoveOutError('');
  }

  const handleMoveOut = async () => {
    if (!moveOutTarget) return;
    setMoveOutError('');
    if (!moveOutDate) { setMoveOutError('Vui lòng chọn ngày kết thúc cư trú.'); return; }
    try {
      await moveOutResident.mutateAsync({
        id: moveOutTarget.id,
        moveOutDate,
        ...(moveOutNotes.trim() && { notes: moveOutNotes.trim() }),
      });
      setMoveOutTarget(null); // success toast + residents refetch handled by the hook
    } catch (err: any) {
      setMoveOutError(getVnErrorMessage(err?.response?.data?.error));
    }
  };

  const loadApartmentOptions = useCallback(async (query: string): Promise<SearchableOption[]> => {
    const params: Record<string, unknown> = { size: 10, sort: 'unitNumber', direction: 'asc' };
    if (query) params.search = query;
    const res = await apiClient.get('/apartments', { params });
    return (res.data?.data ?? []).map((a: any) => ({
      value: a.id,
      label: `${a.block?.name ?? ''} - ${a.unitNumber}`,
    }));
  }, []);

  function openCreate() {
    setFullName(''); setEmail(''); setPassword(''); setPhone(''); setDateOfBirth(''); setMoveInDate('');
    setGeneratedPassword('');
    setFullNameError(''); setEmailError(''); setPasswordError(''); setPhoneError(''); setDobError('');
    setSelectedApartmentId(''); setAptError('');
    setFormError('');
    setShowCreate(true);
  }

  function handleGeneratePassword() {
    const pw = generatePassword();
    setPassword(pw);
    setGeneratedPassword(pw);
    setPasswordError('');
  }

  const handleCreate = async (e: React.FormEvent) => {
    e.preventDefault();
    setFormError('');
    let valid = true;
    if (!fullName.trim()) { setFullNameError('Vui lòng nhập họ tên.'); valid = false; }
    if (!email.trim()) { setEmailError('Vui lòng nhập email.'); valid = false; }
    if (!password) { setPasswordError('Vui lòng nhập hoặc tạo mật khẩu.'); valid = false; }
    if (!phone.trim()) { setPhoneError('Vui lòng nhập số điện thoại.'); valid = false; }
    if (!dateOfBirth) { setDobError('Vui lòng nhập ngày sinh.'); valid = false; }
    if (!selectedApartmentId) { setAptError('Vui lòng chọn căn hộ.'); valid = false; }
    if (!valid) return;

    const fd = new FormData(e.target as HTMLFormElement);
    // moveInDate now comes from controlled ISO state (VNDatePicker), not FormData
    if (!moveInDate) { setFormError('Vui lòng chọn ngày chuyển vào.'); return; }

    try {
      await createResident.mutateAsync({
        fullName: fullName.trim(),
        email: email.trim(),
        password,
        phone: phone.trim(),
        dateOfBirth,
        apartmentId: selectedApartmentId,
        type: fd.get('type'),
        moveInDate,
        isPrimaryContact: fd.get('isPrimaryContact') === 'true',
      });
      setShowCreate(false);
    } catch (err: any) {
      const errorCode = err?.response?.data?.error;
      if (err?.response?.status === 409) {
        if (errorCode === 'PHONE_ALREADY_EXISTS') {
          setPhoneError('Số điện thoại đã được sử dụng.');
        } else if (errorCode === 'EMAIL_ALREADY_EXISTS') {
          setEmailError('Email đã được sử dụng.');
        } else {
          setFormError('Có lỗi xảy ra, vui lòng thử lại.');
        }
      } else if (err?.response?.status === 404) {
        setFormError('Căn hộ không tồn tại.');
      } else {
        setFormError('Có lỗi xảy ra, vui lòng thử lại.');
      }
    }
  };

  return (
    <div>
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-2xl font-bold text-gray-900">{t('residents.title')}</h1>
        <button onClick={openCreate} className="bg-blue-600 hover:bg-blue-700 text-white px-4 py-2 rounded-md text-sm font-medium">
          {t('residents.add')}
        </button>
      </div>

      <div className="bg-white rounded-lg border border-gray-200 p-4 mb-4">
        <input value={search} onChange={(e) => { setSearch(e.target.value); setPage(0); }}
          placeholder={t('residents.searchPlaceholder')} className="border border-gray-300 rounded-md px-3 py-2 text-sm w-80 focus:outline-none focus:ring-2 focus:ring-blue-500" />
      </div>

      {isError && <div className="bg-red-50 border border-red-200 rounded-lg p-4 text-red-700 mb-4">{t('residents.loadError')}</div>}

      <div className="bg-white rounded-lg border border-gray-200 overflow-hidden">
        <table className="w-full text-sm">
          <thead className="bg-gray-50 border-b border-gray-200">
            <tr>
              <th className="text-left px-4 py-3 font-medium text-gray-500">{t('residents.name')}</th>
              <th className="text-left px-4 py-3 font-medium text-gray-500">Số điện thoại</th>
              <th className="text-left px-4 py-3 font-medium text-gray-500">Email</th>
              <th className="text-left px-4 py-3 font-medium text-gray-500">{t('residents.apartment')}</th>
              <th className="text-left px-4 py-3 font-medium text-gray-500">{t('residents.type')}</th>
              <th className="text-left px-4 py-3 font-medium text-gray-500">{t('residents.moveInDate')}</th>
              <th className="text-left px-4 py-3 font-medium text-gray-500">Trạng thái cư trú</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-100">
            {isLoading && <tr><td colSpan={7} className="text-center py-8 text-gray-400">{t('common.loading')}</td></tr>}
            {!isLoading && !data?.data?.length && <tr><td colSpan={7} className="text-center py-8 text-gray-400">{t('common.emptyFound', { item: 'cư dân' })}</td></tr>}
            {data?.data?.map((r: ResidentItem) => (
              <tr key={r.id} className="hover:bg-gray-50">
                <td className="px-4 py-3 font-medium">{r.user?.fullName}</td>
                <td className="px-4 py-3 text-gray-500">{r.user?.phone}</td>
                <td className="px-4 py-3 text-gray-500">{r.user?.email ?? '—'}</td>
                <td className="px-4 py-3">{r.apartment?.unitNumber}</td>
                <td className="px-4 py-3">
                  <span className={`inline-flex px-2 py-0.5 rounded-full text-xs font-medium ${r.type === 'OWNER' ? 'bg-purple-100 text-purple-700' : 'bg-blue-100 text-blue-700'}`}>{labelFor('ResidentType', r.type)}</span>
                </td>
                <td className="px-4 py-3">{formatVNDate(r.moveInDate)}</td>
                <td className="px-4 py-3">
                  {r.moveOutDate ? (
                    // Đã chuyển đi: badge + ngày rời đi; không cho kết thúc lại (khớp guard BE).
                    <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-xs font-medium bg-gray-200 text-gray-700">
                      Đã chuyển đi · {formatVNDate(r.moveOutDate)}
                    </span>
                  ) : (
                    <button
                      onClick={() => openMoveOut(r)}
                      className="px-3 py-1 text-xs border border-red-300 text-red-700 rounded hover:bg-red-50 font-medium"
                    >
                      Kết thúc cư trú
                    </button>
                  )}
                </td>
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

      {showCreate && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
          <div className="absolute inset-0 bg-black/50" onClick={() => setShowCreate(false)} />
          <div className="relative bg-white rounded-lg shadow-xl w-full max-w-md p-6 max-h-[90vh] overflow-y-auto">
            <h2 className="text-lg font-semibold mb-4">Thêm cư dân mới</h2>
            <form onSubmit={handleCreate} className="space-y-3">

              <p className="text-xs text-gray-500 font-medium uppercase tracking-wide pt-1">Thông tin tài khoản</p>

              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Họ tên <span className="text-red-500">*</span></label>
                <input
                  value={fullName}
                  onChange={(e) => { setFullName(e.target.value); setFullNameError(''); }}
                  placeholder="Nguyễn Văn A"
                  className={`block w-full border rounded-md px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 ${fullNameError ? 'border-red-400' : 'border-gray-300'}`}
                />
                {fullNameError && <p className="text-xs text-red-600 mt-1">{fullNameError}</p>}
              </div>

              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Email <span className="text-red-500">*</span></label>
                <input
                  type="email"
                  value={email}
                  onChange={(e) => { setEmail(e.target.value); setEmailError(''); }}
                  placeholder="email@example.com"
                  className={`block w-full border rounded-md px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 ${emailError ? 'border-red-400' : 'border-gray-300'}`}
                />
                {emailError && <p className="text-xs text-red-600 mt-1">{emailError}</p>}
              </div>

              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Mật khẩu <span className="text-red-500">*</span></label>
                <div className="flex gap-2">
                  <input
                    type="text"
                    value={password}
                    onChange={(e) => { setPassword(e.target.value); setPasswordError(''); setGeneratedPassword(''); }}
                    placeholder="Nhập hoặc tạo mật khẩu"
                    className={`flex-1 border rounded-md px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 font-mono ${passwordError ? 'border-red-400' : 'border-gray-300'}`}
                  />
                  <button
                    type="button"
                    onClick={handleGeneratePassword}
                    className="px-3 py-2 text-xs bg-gray-100 hover:bg-gray-200 border border-gray-300 rounded-md whitespace-nowrap"
                  >
                    Tạo mật khẩu
                  </button>
                </div>
                {passwordError && <p className="text-xs text-red-600 mt-1">{passwordError}</p>}
                {generatedPassword && (
                  <div className="mt-2 p-2 bg-yellow-50 border border-yellow-200 rounded text-xs text-yellow-800">
                    Mật khẩu đã tạo: <span className="font-mono font-bold select-all">{generatedPassword}</span>
                    <span className="text-yellow-600 ml-1">— lưu lại để cung cấp cho cư dân.</span>
                  </div>
                )}
              </div>

              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Số điện thoại <span className="text-red-500">*</span></label>
                <input
                  value={phone}
                  onChange={(e) => { setPhone(e.target.value); setPhoneError(''); }}
                  placeholder="0912345678"
                  className={`block w-full border rounded-md px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 ${phoneError ? 'border-red-400' : 'border-gray-300'}`}
                />
                {phoneError && <p className="text-xs text-red-600 mt-1">{phoneError}</p>}
              </div>

              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Ngày sinh <span className="text-red-500">*</span></label>
                <VNDatePicker value={dateOfBirth} onChange={(iso) => { setDateOfBirth(iso); setDobError(''); }} />
                {dobError && <p className="text-xs text-red-600 mt-1">{dobError}</p>}
              </div>

              <p className="text-xs text-gray-500 font-medium uppercase tracking-wide pt-2">Thông tin cư trú</p>

              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Căn hộ <span className="text-red-500">*</span></label>
                <SearchableSelect
                  loadOptions={loadApartmentOptions}
                  value={selectedApartmentId}
                  onChange={(v) => { setSelectedApartmentId(v); setAptError(''); }}
                  placeholder="Chọn căn hộ..."
                  error={aptError}
                />
              </div>

              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Loại</label>
                <select name="type" className="block w-full border border-gray-300 rounded-md px-3 py-2 text-sm bg-white focus:outline-none focus:ring-2 focus:ring-blue-500">
                  <option value="OWNER">Chủ sở hữu</option>
                  <option value="TENANT">Người thuê</option>
                </select>
              </div>

              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Ngày chuyển vào <span className="text-red-500">*</span></label>
                <VNDatePicker value={moveInDate} onChange={setMoveInDate} />
              </div>

              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Liên hệ chính</label>
                <select name="isPrimaryContact" className="block w-full border border-gray-300 rounded-md px-3 py-2 text-sm bg-white focus:outline-none focus:ring-2 focus:ring-blue-500">
                  <option value="false">Không</option>
                  <option value="true">Có</option>
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

      {moveOutTarget && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
          <div className="absolute inset-0 bg-black/50" onClick={() => setMoveOutTarget(null)} />
          <div className="relative bg-white rounded-lg shadow-xl w-full max-w-md p-6">
            <h2 className="text-lg font-semibold mb-3">Kết thúc cư trú</h2>

            <p className="text-sm text-gray-700 mb-4">
              Kết thúc cư trú của <span className="font-semibold">{moveOutTarget.user?.fullName}</span> tại căn{' '}
              <span className="font-semibold">{moveOutTarget.apartment?.unitNumber}</span>? Cư dân sẽ được đánh dấu đã rời đi
              và gỡ vai trò liên hệ chính. Thao tác này <span className="font-semibold">KHÔNG</span> khoá tài khoản đăng nhập
              và <span className="font-semibold">KHÔNG thể hoàn tác</span> từ giao diện.
            </p>

            <div className="mb-3">
              <label className="block text-sm font-medium text-gray-700 mb-1">Ngày kết thúc cư trú <span className="text-red-500">*</span></label>
              <VNDatePicker value={moveOutDate} onChange={(iso) => { setMoveOutDate(iso); setMoveOutError(''); }} />
            </div>

            <div className="mb-3">
              <label className="block text-sm font-medium text-gray-700 mb-1">Ghi chú <span className="text-gray-400">(tùy chọn)</span></label>
              <textarea
                value={moveOutNotes}
                onChange={(e) => setMoveOutNotes(e.target.value)}
                rows={2}
                placeholder="Lý do hoặc ghi chú thêm..."
                className="block w-full border border-gray-300 rounded-md px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
              />
            </div>

            {moveOutError && <p className="text-sm text-red-600 bg-red-50 px-3 py-2 rounded mb-3">{moveOutError}</p>}

            <div className="flex gap-2 justify-end">
              <button type="button" onClick={() => setMoveOutTarget(null)} className="px-4 py-2 text-sm border border-gray-300 rounded-md hover:bg-gray-50">Hủy</button>
              <button
                type="button"
                onClick={handleMoveOut}
                disabled={moveOutResident.isPending}
                className="px-4 py-2 text-sm bg-red-600 text-white rounded-md hover:bg-red-700 disabled:opacity-50"
              >
                {moveOutResident.isPending ? 'Đang xử lý...' : 'Xác nhận kết thúc'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
