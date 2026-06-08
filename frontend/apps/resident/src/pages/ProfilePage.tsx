import React, { useState } from 'react';
import { useAuthStore } from '../store/authStore';
import { useMe, useChangePassword } from '../api/hooks';

export function ProfilePage() {
  const user = useAuthStore((s) => s.user);
  const logout = useAuthStore((s) => s.logout);
  const { data: me } = useMe();
  const changePassword = useChangePassword();
  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [pwError, setPwError] = useState('');
  const [pwSuccess, setPwSuccess] = useState(false);

  const handleChangePassword = async (e: React.FormEvent) => {
    e.preventDefault();
    setPwError('');
    setPwSuccess(false);
    if (!currentPassword || !newPassword) { setPwError('All fields are required'); return; }
    if (newPassword !== confirmPassword) { setPwError('New passwords do not match'); return; }
    if (newPassword.length < 8) { setPwError('Password must be at least 8 characters'); return; }
    try {
      await changePassword.mutateAsync({ currentPassword, newPassword });
      setPwSuccess(true);
      setCurrentPassword(''); setNewPassword(''); setConfirmPassword('');
    } catch (err: any) {
      setPwError(err?.response?.data?.message ?? 'Failed to change password');
    }
  };

  return (
    <div className="p-4 space-y-4">
      <h1 className="text-lg font-bold text-gray-900">My Profile</h1>

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
          <div className="flex gap-2"><span className="text-gray-500 w-20">Role:</span><span className="font-medium">{user?.role}</span></div>
          {me?.phone && <div className="flex gap-2"><span className="text-gray-500 w-20">Số điện thoại:</span><span>{me.phone}</span></div>}
          {me?.email && <div className="flex gap-2"><span className="text-gray-500 w-20">Email:</span><span>{me.email}</span></div>}
          {me?.lastLoginAt && <div className="flex gap-2"><span className="text-gray-500 w-20">Last login:</span><span>{new Date(me.lastLoginAt).toLocaleString()}</span></div>}
        </div>
      </div>

      {/* Change password */}
      <div className="bg-white rounded-xl border border-gray-200 p-4">
        <h2 className="font-semibold text-gray-900 mb-3 text-sm">Change Password</h2>
        <form onSubmit={handleChangePassword} className="space-y-3">
          <div><label className="block text-sm font-medium text-gray-700 mb-1">Current Password</label>
            <input type="password" value={currentPassword} onChange={(e) => setCurrentPassword(e.target.value)} className="block w-full border border-gray-300 rounded-lg px-3 py-2.5 text-sm" autoComplete="current-password" /></div>
          <div><label className="block text-sm font-medium text-gray-700 mb-1">New Password</label>
            <input type="password" value={newPassword} onChange={(e) => setNewPassword(e.target.value)} className="block w-full border border-gray-300 rounded-lg px-3 py-2.5 text-sm" autoComplete="new-password" /></div>
          <div><label className="block text-sm font-medium text-gray-700 mb-1">Confirm New Password</label>
            <input type="password" value={confirmPassword} onChange={(e) => setConfirmPassword(e.target.value)} className="block w-full border border-gray-300 rounded-lg px-3 py-2.5 text-sm" autoComplete="new-password" /></div>
          {pwError && <p className="text-sm text-red-600 bg-red-50 px-3 py-2 rounded-lg">{pwError}</p>}
          {pwSuccess && <p className="text-sm text-green-600 bg-green-50 px-3 py-2 rounded-lg">Password changed successfully</p>}
          <button type="submit" disabled={changePassword.isPending} className="w-full bg-blue-600 text-white rounded-lg py-2.5 text-sm font-medium disabled:opacity-50">
            {changePassword.isPending ? 'Changing...' : 'Change Password'}
          </button>
        </form>
      </div>

      {/* Logout */}
      <button onClick={logout} className="w-full bg-red-50 text-red-600 border border-red-200 rounded-xl py-3 text-sm font-medium hover:bg-red-100 transition-colors">
        Sign out
      </button>
    </div>
  );
}
