import React, { useState, useEffect } from 'react';
import { getVnErrorMessage, formatVNDateTime, Modal } from '@gemek/ui';
import { useAuthStore } from '../store/authStore';
import { useMe, useChangePassword, useUpdateOwnProfile } from '../api/hooks';
import { t } from '../i18n/vi';

/** Self-profile shape from GET /api/auth/me (subset this page renders/edits). */
interface MeProfile {
  fullName: string;
  phone: string;
  email: string | null;
  role: string;
  lastLoginAt: string | null;
}

/** Minimal shape of an axios error carrying the backend ErrorCode. */
interface ApiError {
  response?: { data?: { error?: string } };
}

export function ProfilePage() {
  const user = useAuthStore((s) => s.user);
  const logout = useAuthStore((s) => s.logout);
  const { data: meData } = useMe();
  const me = meData as MeProfile | undefined;
  const changePassword = useChangePassword();
  const updateProfile = useUpdateOwnProfile();

  // Edit-info form state — seeded from `me` (see effect below).
  const [fullName, setFullName] = useState('');
  const [phone, setPhone] = useState('');
  const [email, setEmail] = useState('');
  const [profileError, setProfileError] = useState('');
  const [confirmOpen, setConfirmOpen] = useState(false);

  // Change-password form state.
  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [pwError, setPwError] = useState('');

  // Seed the edit form from server values; re-seeds after a successful save
  // refetches ['me'], keeping the form in sync with persisted data (no stale).
  useEffect(() => {
    if (me) {
      setFullName(me.fullName ?? '');
      setPhone(me.phone ?? '');
      setEmail(me.email ?? '');
    }
  }, [me?.fullName, me?.phone, me?.email]);

  const submitProfile = async () => {
    setProfileError('');
    try {
      await updateProfile.mutateAsync({ fullName: fullName.trim(), phone: phone.trim(), email: email.trim() || undefined });
      setConfirmOpen(false);
    } catch (err) {
      setConfirmOpen(false);
      setProfileError(getVnErrorMessage((err as ApiError)?.response?.data?.error));
    }
  };

  const handleSaveProfile = (e: React.FormEvent) => {
    e.preventDefault();
    setProfileError('');
    if (!fullName.trim()) { setProfileError('Vui lòng nhập họ tên'); return; }
    if (!phone.trim()) { setProfileError('Vui lòng nhập số điện thoại'); return; }
    // Phone is the login id — confirm before changing it. Unchanged phone submits directly.
    if (me && phone.trim() !== me.phone) { setConfirmOpen(true); return; }
    void submitProfile();
  };

  const handleChangePassword = async (e: React.FormEvent) => {
    e.preventDefault();
    setPwError('');
    if (!currentPassword || !newPassword) { setPwError('Vui lòng điền đầy đủ thông tin'); return; }
    if (newPassword !== confirmPassword) { setPwError('Mật khẩu mới không khớp'); return; }
    if (newPassword.length < 8) { setPwError('Mật khẩu mới phải có ít nhất 8 ký tự'); return; }
    try {
      await changePassword.mutateAsync({ currentPassword, newPassword });
      setCurrentPassword(''); setNewPassword(''); setConfirmPassword('');
    } catch (err) {
      setPwError(getVnErrorMessage((err as ApiError)?.response?.data?.error));
    }
  };

  return (
    <div className="p-4 space-y-4">
      <h1 className="text-lg font-bold text-gray-900">{t('profile.title')}</h1>

      {/* Profile info (view) */}
      <div className="bg-white rounded-xl border border-gray-200 p-4">
        <div className="flex items-center gap-3 mb-4">
          <div className="w-12 h-12 rounded-full bg-blue-100 flex items-center justify-center">
            <span className="text-blue-600 font-bold text-lg">{user?.fullName?.charAt(0).toUpperCase()}</span>
          </div>
          <div>
            <p className="font-semibold text-gray-900">{user?.fullName}</p>
            <p className="text-sm text-gray-500">{user?.phone}</p>
          </div>
        </div>
        <div className="space-y-2 text-sm">
          <div className="flex gap-2"><span className="text-gray-500 w-20">{t('profile.role')}</span><span className="font-medium">{user?.role}</span></div>
          {me?.phone && <div className="flex gap-2"><span className="text-gray-500 w-20">Số điện thoại:</span><span>{me.phone}</span></div>}
          {me?.email && <div className="flex gap-2"><span className="text-gray-500 w-20">Email:</span><span>{me.email}</span></div>}
          {me?.lastLoginAt && <div className="flex gap-2"><span className="text-gray-500 w-20">{t('profile.lastLogin')}</span><span>{formatVNDateTime(me.lastLoginAt)}</span></div>}
        </div>
      </div>

      {/* Edit info — fullName / phone / email (apartment is NOT editable and not in the me payload) */}
      <div className="bg-white rounded-xl border border-gray-200 p-4">
        <h2 className="font-semibold text-gray-900 mb-3 text-sm">{t('profile.editInfo')}</h2>
        <form onSubmit={handleSaveProfile} className="space-y-3">
          <div><label className="block text-sm font-medium text-gray-700 mb-1">{t('profile.fullName')}</label>
            <input type="text" value={fullName} onChange={(e) => setFullName(e.target.value)} className="block w-full border border-gray-300 rounded-lg px-3 py-2.5 text-sm" autoComplete="name" /></div>
          <div><label className="block text-sm font-medium text-gray-700 mb-1">{t('profile.phone')}</label>
            <input type="tel" value={phone} onChange={(e) => setPhone(e.target.value)} className="block w-full border border-gray-300 rounded-lg px-3 py-2.5 text-sm" autoComplete="tel" /></div>
          <div><label className="block text-sm font-medium text-gray-700 mb-1">{t('profile.email')}</label>
            <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} placeholder={t('profile.emailPlaceholder')} className="block w-full border border-gray-300 rounded-lg px-3 py-2.5 text-sm" autoComplete="email" /></div>
          {profileError && <p className="text-sm text-red-600 bg-red-50 px-3 py-2 rounded-lg">{profileError}</p>}
          <button type="submit" disabled={updateProfile.isPending} className="w-full bg-blue-600 text-white rounded-lg py-2.5 text-sm font-medium disabled:opacity-50">
            {updateProfile.isPending ? t('profile.saving') : t('profile.save')}
          </button>
        </form>
      </div>

      {/* Change password */}
      <div className="bg-white rounded-xl border border-gray-200 p-4">
        <h2 className="font-semibold text-gray-900 mb-3 text-sm">{t('profile.changePassword')}</h2>
        <form onSubmit={handleChangePassword} className="space-y-3">
          <div><label className="block text-sm font-medium text-gray-700 mb-1">{t('profile.currentPassword')}</label>
            <input type="password" value={currentPassword} onChange={(e) => setCurrentPassword(e.target.value)} className="block w-full border border-gray-300 rounded-lg px-3 py-2.5 text-sm" autoComplete="current-password" /></div>
          <div><label className="block text-sm font-medium text-gray-700 mb-1">{t('profile.newPassword')}</label>
            <input type="password" value={newPassword} onChange={(e) => setNewPassword(e.target.value)} className="block w-full border border-gray-300 rounded-lg px-3 py-2.5 text-sm" autoComplete="new-password" /></div>
          <div><label className="block text-sm font-medium text-gray-700 mb-1">{t('profile.confirmNewPassword')}</label>
            <input type="password" value={confirmPassword} onChange={(e) => setConfirmPassword(e.target.value)} className="block w-full border border-gray-300 rounded-lg px-3 py-2.5 text-sm" autoComplete="new-password" /></div>
          {pwError && <p className="text-sm text-red-600 bg-red-50 px-3 py-2 rounded-lg">{pwError}</p>}
          <button type="submit" disabled={changePassword.isPending} className="w-full bg-blue-600 text-white rounded-lg py-2.5 text-sm font-medium disabled:opacity-50">
            {changePassword.isPending ? t('profile.changing') : t('profile.changePassword')}
          </button>
        </form>
      </div>

      {/* Logout */}
      <button onClick={logout} className="w-full bg-red-50 text-red-600 border border-red-200 rounded-xl py-3 text-sm font-medium hover:bg-red-100 transition-colors">
        {t('profile.signOut')}
      </button>

      {/* Phone-change confirm — phone is the login identifier */}
      <Modal open={confirmOpen} onClose={() => setConfirmOpen(false)} title={t('profile.phoneConfirmTitle')} size="sm">
        <p className="text-sm text-gray-700 mb-4">{t('profile.phoneConfirmBody', { phone: phone.trim() })}</p>
        <div className="flex gap-2">
          <button onClick={() => setConfirmOpen(false)} className="flex-1 border border-gray-300 text-gray-700 rounded-lg py-2.5 text-sm font-medium">
            {t('profile.phoneConfirmCancel')}
          </button>
          <button onClick={() => void submitProfile()} disabled={updateProfile.isPending} className="flex-1 bg-blue-600 text-white rounded-lg py-2.5 text-sm font-medium disabled:opacity-50">
            {updateProfile.isPending ? t('profile.saving') : t('profile.phoneConfirmOk')}
          </button>
        </div>
      </Modal>
    </div>
  );
}
