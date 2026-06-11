import React, { useState } from 'react';
import { getVnErrorMessage, formatVNDateTime } from '@gemek/ui';
import { useAuthStore } from '../store/authStore';
import { useMe, useChangePassword } from '../api/hooks';
import { t } from '../i18n/vi';

export function ProfilePage() {
  const user = useAuthStore((s) => s.user);
  const logout = useAuthStore((s) => s.logout);
  const { data: me } = useMe();
  const changePassword = useChangePassword();
  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [pwError, setPwError] = useState('');

  const handleChangePassword = async (e: React.FormEvent) => {
    e.preventDefault();
    setPwError('');
    if (!currentPassword || !newPassword) { setPwError('Vui lòng điền đầy đủ thông tin'); return; }
    if (newPassword !== confirmPassword) { setPwError('Mật khẩu mới không khớp'); return; }
    if (newPassword.length < 8) { setPwError('Mật khẩu mới phải có ít nhất 8 ký tự'); return; }
    try {
      await changePassword.mutateAsync({ currentPassword, newPassword });
      setCurrentPassword(''); setNewPassword(''); setConfirmPassword('');
    } catch (err: any) {
      setPwError(getVnErrorMessage((err as any)?.response?.data?.error));
    }
  };

  return (
    <div className="p-4 space-y-4">
      <h1 className="text-lg font-bold text-gray-900">{t('profile.title')}</h1>

      {/* Profile info */}
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
    </div>
  );
}
