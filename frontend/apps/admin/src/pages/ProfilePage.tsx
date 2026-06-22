import React, { useEffect, useState } from 'react';
import { labelFor, getVnErrorMessage } from '@gemek/ui';
import { useMe, useUpdateOwnProfile, useChangeOwnPassword } from '../api/hooks';
import { useAuthStore } from '../store/authStore';
import type { MyProfile } from '../types/profile';

// Mirrors the BE password policy (≥8 chars, upper+lower+digit+special). Shown as
// helper text and enforced client-side before the request; the BE re-validates.
const PASSWORD_HINT = 'Tối thiểu 8 ký tự, gồm chữ hoa, chữ thường, số và ký tự đặc biệt.';
const PASSWORD_RE = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[^A-Za-z0-9]).{8,}$/;

/**
 * Self-service profile page reachable by every authenticated admin-portal role
 * (ADMIN / BOARD_MEMBER / TECHNICIAN). Three independent areas: read-only view,
 * profile update (fullName/phone/email), and password change. Profile-update and
 * password-change are SEPARATE endpoints and SEPARATE submits — never merged.
 */
export function ProfilePage() {
  const { data: me, isLoading, isError } = useMe();
  const setUser = useAuthStore((s) => s.setUser);
  const updateProfile = useUpdateOwnProfile();
  const changePassword = useChangeOwnPassword();

  // ---- Area B: profile update form ----
  const [fullName, setFullName] = useState('');
  const [phone, setPhone] = useState('');
  const [email, setEmail] = useState('');
  const [fullNameError, setFullNameError] = useState('');
  const [phoneError, setPhoneError] = useState('');
  const [emailError, setEmailError] = useState('');
  const [profileFormError, setProfileFormError] = useState('');
  // Phone-change confirmation gate (ruling §C.4): only fires when phone actually changes.
  const [showPhoneConfirm, setShowPhoneConfirm] = useState(false);

  // Seed the editable fields once the profile loads. Keyed on the loaded id so a
  // refetch with the same identity does not clobber an in-flight edit.
  useEffect(() => {
    if (me) {
      setFullName(me.fullName);
      setPhone(me.phone);
      setEmail(me.email ?? '');
    }
  }, [me?.id]);

  function validateProfile(): boolean {
    let valid = true;
    setFullNameError(''); setPhoneError(''); setEmailError('');
    if (!fullName.trim()) { setFullNameError('Vui lòng nhập họ tên.'); valid = false; }
    if (!phone.trim()) { setPhoneError('Vui lòng nhập số điện thoại.'); valid = false; }
    return valid;
  }

  const handleProfileSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setProfileFormError('');
    if (!validateProfile()) return;
    // Phone-change confirmation: ONLY when the phone differs from the loaded value.
    // Email/fullName changes need no confirmation.
    if (me && phone.trim() !== me.phone) { setShowPhoneConfirm(true); return; }
    void doUpdateProfile();
  };

  async function doUpdateProfile() {
    setShowPhoneConfirm(false);
    try {
      const updated: MyProfile = await updateProfile.mutateAsync({
        fullName: fullName.trim(),
        phone: phone.trim(),
        email: email.trim() || null,
      });
      // Keep the held user (sidebar/header name + login phone) in sync. Token is NOT
      // rotated by a phone change, so the session stays valid — no logout.
      setUser({
        id: updated.id,
        phone: updated.phone,
        fullName: updated.fullName,
        role: updated.role,
        avatarUrl: updated.avatarUrl,
      });
    } catch (err: any) {
      const code = err?.response?.data?.error;
      // Field-anchored mapping for the two uniqueness conflicts; everything else inline at form level.
      if (code === 'PHONE_ALREADY_EXISTS') setPhoneError(getVnErrorMessage(code));
      else if (code === 'EMAIL_ALREADY_EXISTS') setEmailError(getVnErrorMessage(code));
      else setProfileFormError(getVnErrorMessage(code));
    }
  }

  // ---- Area C: password change form (independent of the profile update) ----
  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [currentPwError, setCurrentPwError] = useState('');
  const [newPwError, setNewPwError] = useState('');
  const [confirmPwError, setConfirmPwError] = useState('');
  const [pwFormError, setPwFormError] = useState('');

  function validatePassword(): boolean {
    let valid = true;
    setCurrentPwError(''); setNewPwError(''); setConfirmPwError('');
    if (!currentPassword) { setCurrentPwError('Vui lòng nhập mật khẩu hiện tại.'); valid = false; }
    if (!newPassword) { setNewPwError('Vui lòng nhập mật khẩu mới.'); valid = false; }
    else if (!PASSWORD_RE.test(newPassword)) { setNewPwError(PASSWORD_HINT); valid = false; }
    if (confirmPassword !== newPassword) { setConfirmPwError('Mật khẩu xác nhận không khớp.'); valid = false; }
    return valid;
  }

  async function handlePasswordSubmit(e: React.FormEvent) {
    e.preventDefault();
    setPwFormError('');
    if (!validatePassword()) return;
    try {
      await changePassword.mutateAsync({ currentPassword, newPassword });
      // Never retain secrets after a successful change. Session is NOT invalidated.
      setCurrentPassword(''); setNewPassword(''); setConfirmPassword('');
    } catch (err: any) {
      const code = err?.response?.data?.error;
      if (code === 'WRONG_CURRENT_PASSWORD') setCurrentPwError(getVnErrorMessage(code));
      else if (code === 'PASSWORD_POLICY_VIOLATION' || code === 'VALIDATION_ERROR') setNewPwError(getVnErrorMessage(code));
      else setPwFormError(getVnErrorMessage(code));
    }
  }

  if (isLoading) {
    return <div className="text-center py-12 text-gray-400">Đang tải...</div>;
  }
  if (isError || !me) {
    return (
      <div className="bg-red-50 border border-red-200 rounded-lg p-4 text-red-700">
        Không thể tải thông tin cá nhân. Vui lòng thử lại.
      </div>
    );
  }

  const inputClass = (hasError: string) =>
    `block w-full border rounded-md px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 ${hasError ? 'border-red-400' : 'border-gray-300'}`;

  return (
    <div className="max-w-2xl">
      <h1 className="text-2xl font-bold text-gray-900 mb-6">Trang cá nhân</h1>

      {/* Area A — read-only overview */}
      <div className="bg-white rounded-lg border border-gray-200 p-6 mb-6">
        <h2 className="text-lg font-semibold mb-4">Thông tin tài khoản</h2>
        <dl className="grid grid-cols-3 gap-y-3 text-sm">
          <dt className="text-gray-500">Họ tên</dt>
          <dd className="col-span-2 font-medium text-gray-900">{me.fullName}</dd>
          <dt className="text-gray-500">Số điện thoại</dt>
          <dd className="col-span-2 font-medium text-gray-900">{me.phone}</dd>
          <dt className="text-gray-500">Email</dt>
          <dd className="col-span-2 font-medium text-gray-900">{me.email ?? '—'}</dd>
          <dt className="text-gray-500">Vai trò</dt>
          <dd className="col-span-2 font-medium text-gray-900">{labelFor('UserRole', me.role)}</dd>
        </dl>
      </div>

      {/* Area B — update profile (fullName / phone / email) */}
      <div className="bg-white rounded-lg border border-gray-200 p-6 mb-6">
        <h2 className="text-lg font-semibold mb-4">Cập nhật thông tin</h2>
        <form onSubmit={handleProfileSubmit} className="space-y-3">
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">Họ tên <span className="text-red-500">*</span></label>
            <input value={fullName} onChange={(e) => { setFullName(e.target.value); setFullNameError(''); }}
              placeholder="Nguyễn Văn A" className={inputClass(fullNameError)} />
            {fullNameError && <p className="text-xs text-red-600 mt-1">{fullNameError}</p>}
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">Số điện thoại <span className="text-red-500">*</span></label>
            <input value={phone} onChange={(e) => { setPhone(e.target.value); setPhoneError(''); }}
              placeholder="0912345678" className={inputClass(phoneError)} />
            <p className="text-xs text-gray-500 mt-1">Số điện thoại cũng là tên đăng nhập của bạn.</p>
            {phoneError && <p className="text-xs text-red-600 mt-1">{phoneError}</p>}
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">Email</label>
            <input type="email" value={email} onChange={(e) => { setEmail(e.target.value); setEmailError(''); }}
              placeholder="email@example.com (không bắt buộc)" className={inputClass(emailError)} />
            {emailError && <p className="text-xs text-red-600 mt-1">{emailError}</p>}
          </div>
          {profileFormError && <p className="text-sm text-red-600 bg-red-50 px-3 py-2 rounded">{profileFormError}</p>}
          <div className="flex justify-end pt-1">
            <button type="submit" disabled={updateProfile.isPending}
              className="px-4 py-2 text-sm bg-blue-600 text-white rounded-md hover:bg-blue-700 disabled:opacity-50">
              {updateProfile.isPending ? 'Đang lưu...' : 'Lưu thay đổi'}
            </button>
          </div>
        </form>
      </div>

      {/* Area C — change password (separate endpoint, separate submit) */}
      <div className="bg-white rounded-lg border border-gray-200 p-6">
        <h2 className="text-lg font-semibold mb-4">Đổi mật khẩu</h2>
        <form onSubmit={handlePasswordSubmit} className="space-y-3">
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">Mật khẩu hiện tại <span className="text-red-500">*</span></label>
            <input type="password" autoComplete="current-password" value={currentPassword}
              onChange={(e) => { setCurrentPassword(e.target.value); setCurrentPwError(''); }}
              className={inputClass(currentPwError)} />
            {currentPwError && <p className="text-xs text-red-600 mt-1">{currentPwError}</p>}
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">Mật khẩu mới <span className="text-red-500">*</span></label>
            <input type="password" autoComplete="new-password" value={newPassword}
              onChange={(e) => { setNewPassword(e.target.value); setNewPwError(''); }}
              className={inputClass(newPwError)} />
            <p className="text-xs text-gray-500 mt-1">{PASSWORD_HINT}</p>
            {newPwError && <p className="text-xs text-red-600 mt-1">{newPwError}</p>}
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">Xác nhận mật khẩu mới <span className="text-red-500">*</span></label>
            <input type="password" autoComplete="new-password" value={confirmPassword}
              onChange={(e) => { setConfirmPassword(e.target.value); setConfirmPwError(''); }}
              className={inputClass(confirmPwError)} />
            {confirmPwError && <p className="text-xs text-red-600 mt-1">{confirmPwError}</p>}
          </div>
          {pwFormError && <p className="text-sm text-red-600 bg-red-50 px-3 py-2 rounded">{pwFormError}</p>}
          <div className="flex justify-end pt-1">
            <button type="submit" disabled={changePassword.isPending}
              className="px-4 py-2 text-sm bg-blue-600 text-white rounded-md hover:bg-blue-700 disabled:opacity-50">
              {changePassword.isPending ? 'Đang lưu...' : 'Đổi mật khẩu'}
            </button>
          </div>
        </form>
      </div>

      {/* Phone-change confirmation — a real gate before the mutation fires. */}
      {showPhoneConfirm && (
        <div className="fixed inset-0 z-[60] flex items-center justify-center p-4">
          <div className="absolute inset-0 bg-black/50" onClick={() => setShowPhoneConfirm(false)} />
          <div className="relative bg-white rounded-lg shadow-xl w-full max-w-sm p-6">
            <h3 className="text-lg font-semibold text-gray-900 mb-2">Xác nhận đổi số điện thoại</h3>
            <p className="text-sm text-gray-600 mb-5">
              Bạn sắp đổi số điện thoại đăng nhập thành <span className="font-semibold">{phone.trim()}</span>.
              Lần sau hãy dùng số này để đăng nhập.
            </p>
            <div className="flex gap-2 justify-end">
              <button onClick={() => setShowPhoneConfirm(false)}
                className="px-4 py-2 text-sm border border-gray-300 rounded-md hover:bg-gray-50">Hủy</button>
              <button onClick={() => void doUpdateProfile()} disabled={updateProfile.isPending}
                className="px-4 py-2 text-sm bg-blue-600 text-white rounded-md hover:bg-blue-700 disabled:opacity-50">
                {updateProfile.isPending ? 'Đang lưu...' : 'Xác nhận đổi'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
