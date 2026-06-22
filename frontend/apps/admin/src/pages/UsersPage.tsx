import React, { useState } from 'react';
import { labelFor, getVnErrorMessage } from '@gemek/ui';
import { useUsers, useCreateUser, useUpdateUser, useDeactivateUser, useResetUserPassword } from '../api/hooks';
import { useAuthStore } from '../store/authStore';

/** A staff/account row as returned by GET /api/users. */
interface StaffUserItem {
  id: string;
  fullName: string;
  phone: string;
  email: string | null;
  role: 'ADMIN' | 'TECHNICIAN' | 'BOARD_MEMBER' | 'RESIDENT';
  // GET /api/users serializes the flag as `isActive` (UserResponse @JsonProperty); match it exactly.
  isActive: boolean;
}

// Roles selectable when creating/editing a STAFF account. RESIDENT is intentionally
// absent — residents are provisioned via ResidentsPage, not here.
const STAFF_ROLES: StaffUserItem['role'][] = ['ADMIN', 'TECHNICIAN', 'BOARD_MEMBER'];
// Roles offered in the list filter (all four — the list shows every account).
const FILTER_ROLES: StaffUserItem['role'][] = ['ADMIN', 'TECHNICIAN', 'BOARD_MEMBER', 'RESIDENT'];

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

const PASSWORD_HINT = 'Tối thiểu 8 ký tự, gồm chữ hoa, chữ thường, số và ký tự đặc biệt.';

type FormMode = 'create' | 'edit';

export function UsersPage() {
  const [search, setSearch] = useState('');
  const [roleFilter, setRoleFilter] = useState('');
  const [activeFilter, setActiveFilter] = useState('');
  const [page, setPage] = useState(0);

  // Form modal (create + edit share one modal)
  const [mode, setMode] = useState<FormMode>('create');
  const [editingId, setEditingId] = useState<string | null>(null);
  const [editingOriginalRole, setEditingOriginalRole] = useState<StaffUserItem['role'] | null>(null);
  const [showForm, setShowForm] = useState(false);

  const [fullName, setFullName] = useState('');
  const [phone, setPhone] = useState('');
  const [email, setEmail] = useState('');
  const [role, setRole] = useState<StaffUserItem['role']>('TECHNICIAN');
  const [password, setPassword] = useState('');
  const [isActive, setIsActive] = useState(true);
  const [generatedPassword, setGeneratedPassword] = useState('');

  const [fullNameError, setFullNameError] = useState('');
  const [phoneError, setPhoneError] = useState('');
  const [passwordError, setPasswordError] = useState('');
  const [formError, setFormError] = useState('');

  // ADMIN-role guardrail confirmation dialog
  const [showAdminConfirm, setShowAdminConfirm] = useState(false);

  // Reset-password modal
  const [resetTarget, setResetTarget] = useState<StaffUserItem | null>(null);
  const [resetPwValue, setResetPwValue] = useState('');
  const [resetError, setResetError] = useState('');

  // Read-only: the logged-in admin's id, used to hide self-deactivate (BE also guards it).
  const currentUserId = useAuthStore((s) => s.user?.id);

  const params: Record<string, unknown> = { page, size: 20 };
  if (search) params.search = search;
  if (roleFilter) params.role = roleFilter;
  if (activeFilter) params.isActive = activeFilter;
  const { data, isLoading, isError } = useUsers(params);

  const createUser = useCreateUser();
  const updateUser = useUpdateUser();
  const deactivateUser = useDeactivateUser();
  const resetPassword = useResetUserPassword();

  function resetFormState() {
    setFullName(''); setPhone(''); setEmail(''); setRole('TECHNICIAN'); setPassword(''); setIsActive(true);
    setGeneratedPassword('');
    setFullNameError(''); setPhoneError(''); setPasswordError(''); setFormError('');
  }

  function openCreate() {
    resetFormState();
    setMode('create');
    setEditingId(null);
    setEditingOriginalRole(null);
    setShowForm(true);
  }

  function openEdit(u: StaffUserItem) {
    resetFormState();
    setMode('edit');
    setEditingId(u.id);
    setEditingOriginalRole(u.role);
    setFullName(u.fullName);
    setPhone(u.phone);
    setEmail(u.email ?? '');
    setRole(u.role);
    setIsActive(u.isActive);
    setShowForm(true);
  }

  function handleGeneratePassword() {
    const pw = generatePassword();
    setPassword(pw);
    setGeneratedPassword(pw);
    setPasswordError('');
  }

  /** Validates the form; returns true when valid. */
  function validate(): boolean {
    let valid = true;
    if (!fullName.trim()) { setFullNameError('Vui lòng nhập họ tên.'); valid = false; }
    if (!phone.trim()) { setPhoneError('Vui lòng nhập số điện thoại.'); valid = false; }
    if (mode === 'create' && !password) { setPasswordError('Vui lòng nhập hoặc tạo mật khẩu.'); valid = false; }
    return valid;
  }

  /** True when this submit promotes/creates an ADMIN and needs explicit confirmation. */
  function needsAdminConfirm(): boolean {
    if (role !== 'ADMIN') return false;
    // Create with ADMIN, or edit that changes role TO ADMIN (promotion).
    return mode === 'create' || editingOriginalRole !== 'ADMIN';
  }

  const handleSubmitClick = (e: React.FormEvent) => {
    e.preventDefault();
    setFormError('');
    if (!validate()) return;
    // ADMIN guardrail: gate the mutation behind an explicit confirmation step.
    if (needsAdminConfirm()) { setShowAdminConfirm(true); return; }
    void doSubmit();
  };

  async function doSubmit() {
    setShowAdminConfirm(false);
    try {
      if (mode === 'create') {
        await createUser.mutateAsync({
          fullName: fullName.trim(),
          phone: phone.trim(),
          email: email.trim() || undefined,
          role,
          password,
        });
      } else if (editingId) {
        await updateUser.mutateAsync({
          id: editingId,
          data: { fullName: fullName.trim(), phone: phone.trim(), role, isActive },
        });
      }
      setShowForm(false);
    } catch (err: any) {
      const code = err?.response?.data?.error;
      if (code === 'PHONE_ALREADY_EXISTS') setPhoneError(getVnErrorMessage(code));
      else setFormError(getVnErrorMessage(code));
    }
  }

  async function handleDeactivate(u: StaffUserItem) {
    if (!window.confirm(`Vô hiệu hóa tài khoản "${u.fullName}"? Người dùng sẽ không thể đăng nhập.`)) return;
    try {
      await deactivateUser.mutateAsync(u.id);
    } catch (err: any) {
      // SELF_OPERATION_NOT_ALLOWED and others surface as a transient alert (no inline anchor on a row).
      window.alert(getVnErrorMessage(err?.response?.data?.error));
    }
  }

  function openReset(u: StaffUserItem) {
    setResetTarget(u);
    setResetPwValue('');
    setResetError('');
  }

  async function handleReset(e: React.FormEvent) {
    e.preventDefault();
    setResetError('');
    if (!resetPwValue) { setResetError('Vui lòng nhập mật khẩu mới.'); return; }
    if (!resetTarget) return;
    try {
      await resetPassword.mutateAsync({ id: resetTarget.id, newPassword: resetPwValue });
      setResetPwValue(''); // never retain the secret after submit
      setResetTarget(null);
    } catch (err: any) {
      setResetError(getVnErrorMessage(err?.response?.data?.error));
    }
  }

  const submitting = createUser.isPending || updateUser.isPending;

  return (
    <div>
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-2xl font-bold text-gray-900">Quản lý tài khoản</h1>
        <button onClick={openCreate} className="bg-blue-600 hover:bg-blue-700 text-white px-4 py-2 rounded-md text-sm font-medium">
          + Thêm tài khoản
        </button>
      </div>

      <div className="bg-white rounded-lg border border-gray-200 p-4 mb-4 flex flex-wrap gap-3">
        <input value={search} onChange={(e) => { setSearch(e.target.value); setPage(0); }}
          placeholder="Tìm theo tên hoặc email..." className="border border-gray-300 rounded-md px-3 py-2 text-sm w-72 focus:outline-none focus:ring-2 focus:ring-blue-500" />
        <select value={roleFilter} onChange={(e) => { setRoleFilter(e.target.value); setPage(0); }}
          className="border border-gray-300 rounded-md px-3 py-2 text-sm bg-white focus:outline-none focus:ring-2 focus:ring-blue-500">
          <option value="">Tất cả vai trò</option>
          {FILTER_ROLES.map((r) => <option key={r} value={r}>{labelFor('UserRole', r)}</option>)}
        </select>
        <select value={activeFilter} onChange={(e) => { setActiveFilter(e.target.value); setPage(0); }}
          className="border border-gray-300 rounded-md px-3 py-2 text-sm bg-white focus:outline-none focus:ring-2 focus:ring-blue-500">
          <option value="">Tất cả trạng thái</option>
          <option value="true">Đang hoạt động</option>
          <option value="false">Đã vô hiệu hóa</option>
        </select>
      </div>

      {isError && <div className="bg-red-50 border border-red-200 rounded-lg p-4 text-red-700 mb-4">Không thể tải danh sách tài khoản.</div>}

      <div className="bg-white rounded-lg border border-gray-200 overflow-hidden">
        <table className="w-full text-sm">
          <thead className="bg-gray-50 border-b border-gray-200">
            <tr>
              <th className="text-left px-4 py-3 font-medium text-gray-500">Họ tên</th>
              <th className="text-left px-4 py-3 font-medium text-gray-500">Số điện thoại</th>
              <th className="text-left px-4 py-3 font-medium text-gray-500">Email</th>
              <th className="text-left px-4 py-3 font-medium text-gray-500">Vai trò</th>
              <th className="text-left px-4 py-3 font-medium text-gray-500">Trạng thái</th>
              <th className="text-right px-4 py-3 font-medium text-gray-500">Thao tác</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-100">
            {isLoading && <tr><td colSpan={6} className="text-center py-8 text-gray-400">Đang tải...</td></tr>}
            {!isLoading && !data?.data?.length && <tr><td colSpan={6} className="text-center py-8 text-gray-400">Không tìm thấy tài khoản nào.</td></tr>}
            {data?.data?.map((u: StaffUserItem) => (
              <tr key={u.id} className="hover:bg-gray-50">
                <td className="px-4 py-3 font-medium">{u.fullName}</td>
                <td className="px-4 py-3 text-gray-500">{u.phone}</td>
                <td className="px-4 py-3 text-gray-500">{u.email ?? '—'}</td>
                <td className="px-4 py-3">{labelFor('UserRole', u.role)}</td>
                <td className="px-4 py-3">
                  <span className={`inline-flex px-2 py-0.5 rounded-full text-xs font-medium ${u.isActive ? 'bg-green-100 text-green-700' : 'bg-gray-100 text-gray-500'}`}>
                    {u.isActive ? 'Đang hoạt động' : 'Đã vô hiệu hóa'}
                  </span>
                </td>
                <td className="px-4 py-3 text-right whitespace-nowrap">
                  <button onClick={() => openEdit(u)} className="text-xs text-blue-600 hover:underline mr-3">Sửa</button>
                  <button onClick={() => openReset(u)} className="text-xs text-blue-600 hover:underline mr-3">Đặt lại mật khẩu</button>
                  {u.isActive && u.id !== currentUserId && <button onClick={() => handleDeactivate(u)} className="text-xs text-red-600 hover:underline">Vô hiệu hóa</button>}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        <div className="flex items-center justify-between px-4 py-3 border-t border-gray-200">
          <span className="text-xs text-gray-500">Tổng: {data?.total ?? 0}</span>
          <div className="flex gap-2">
            <button onClick={() => setPage((p) => Math.max(0, p - 1))} disabled={page === 0} className="px-3 py-1 text-xs border border-gray-300 rounded hover:bg-gray-50 disabled:opacity-40">Trước</button>
            <span className="px-3 py-1 text-xs">{page + 1} / {data?.totalPages ?? 1}</span>
            <button onClick={() => setPage((p) => p + 1)} disabled={page + 1 >= (data?.totalPages ?? 1)} className="px-3 py-1 text-xs border border-gray-300 rounded hover:bg-gray-50 disabled:opacity-40">Sau</button>
          </div>
        </div>
      </div>

      {showForm && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
          <div className="absolute inset-0 bg-black/50" onClick={() => setShowForm(false)} />
          <div className="relative bg-white rounded-lg shadow-xl w-full max-w-md p-6 max-h-[90vh] overflow-y-auto">
            <h2 className="text-lg font-semibold mb-4">{mode === 'create' ? 'Thêm tài khoản mới' : 'Sửa tài khoản'}</h2>
            <form onSubmit={handleSubmitClick} className="space-y-3">
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Họ tên <span className="text-red-500">*</span></label>
                <input value={fullName} onChange={(e) => { setFullName(e.target.value); setFullNameError(''); }}
                  placeholder="Nguyễn Văn A"
                  className={`block w-full border rounded-md px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 ${fullNameError ? 'border-red-400' : 'border-gray-300'}`} />
                {fullNameError && <p className="text-xs text-red-600 mt-1">{fullNameError}</p>}
              </div>

              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Số điện thoại <span className="text-red-500">*</span></label>
                <input value={phone} onChange={(e) => { setPhone(e.target.value); setPhoneError(''); }}
                  placeholder="0912345678"
                  className={`block w-full border rounded-md px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 ${phoneError ? 'border-red-400' : 'border-gray-300'}`} />
                {phoneError && <p className="text-xs text-red-600 mt-1">{phoneError}</p>}
              </div>

              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Email</label>
                <input type="email" value={email} onChange={(e) => setEmail(e.target.value)}
                  placeholder="email@example.com (không bắt buộc)"
                  className="block w-full border border-gray-300 rounded-md px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" />
              </div>

              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Vai trò <span className="text-red-500">*</span></label>
                <select value={role} onChange={(e) => setRole(e.target.value as StaffUserItem['role'])}
                  className="block w-full border border-gray-300 rounded-md px-3 py-2 text-sm bg-white focus:outline-none focus:ring-2 focus:ring-blue-500">
                  {STAFF_ROLES.map((r) => <option key={r} value={r}>{labelFor('UserRole', r)}</option>)}
                </select>
                {role === 'ADMIN' && <p className="text-xs text-amber-600 mt-1">Quản trị viên có toàn quyền trên hệ thống — sẽ yêu cầu xác nhận.</p>}
              </div>

              {mode === 'create' && (
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">Mật khẩu <span className="text-red-500">*</span></label>
                  <div className="flex gap-2">
                    <input type="text" value={password}
                      onChange={(e) => { setPassword(e.target.value); setPasswordError(''); setGeneratedPassword(''); }}
                      placeholder="Nhập hoặc tạo mật khẩu"
                      className={`flex-1 border rounded-md px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 font-mono ${passwordError ? 'border-red-400' : 'border-gray-300'}`} />
                    <button type="button" onClick={handleGeneratePassword}
                      className="px-3 py-2 text-xs bg-gray-100 hover:bg-gray-200 border border-gray-300 rounded-md whitespace-nowrap">Tạo mật khẩu</button>
                  </div>
                  <p className="text-xs text-gray-500 mt-1">{PASSWORD_HINT}</p>
                  {passwordError && <p className="text-xs text-red-600 mt-1">{passwordError}</p>}
                  {generatedPassword && (
                    <div className="mt-2 p-2 bg-yellow-50 border border-yellow-200 rounded text-xs text-yellow-800">
                      Mật khẩu đã tạo: <span className="font-mono font-bold select-all">{generatedPassword}</span>
                      <span className="text-yellow-600 ml-1">— lưu lại để cung cấp cho người dùng.</span>
                    </div>
                  )}
                </div>
              )}

              {mode === 'edit' && (
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">Trạng thái</label>
                  <select value={isActive ? 'true' : 'false'} onChange={(e) => setIsActive(e.target.value === 'true')}
                    className="block w-full border border-gray-300 rounded-md px-3 py-2 text-sm bg-white focus:outline-none focus:ring-2 focus:ring-blue-500">
                    <option value="true">Đang hoạt động</option>
                    <option value="false">Đã vô hiệu hóa</option>
                  </select>
                </div>
              )}

              {formError && <p className="text-sm text-red-600 bg-red-50 px-3 py-2 rounded">{formError}</p>}
              <div className="flex gap-2 justify-end pt-2">
                <button type="button" onClick={() => setShowForm(false)} className="px-4 py-2 text-sm border border-gray-300 rounded-md hover:bg-gray-50">Hủy</button>
                <button type="submit" disabled={submitting} className="px-4 py-2 text-sm bg-blue-600 text-white rounded-md hover:bg-blue-700 disabled:opacity-50">
                  {submitting ? 'Đang lưu...' : (mode === 'create' ? 'Tạo mới' : 'Lưu')}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {showAdminConfirm && (
        <div className="fixed inset-0 z-[60] flex items-center justify-center p-4">
          <div className="absolute inset-0 bg-black/50" onClick={() => setShowAdminConfirm(false)} />
          <div className="relative bg-white rounded-lg shadow-xl w-full max-w-sm p-6">
            <h3 className="text-lg font-semibold text-gray-900 mb-2">Xác nhận quyền Quản trị viên</h3>
            <p className="text-sm text-gray-600 mb-5">
              Tài khoản này sẽ có <span className="font-semibold">toàn quyền truy cập hệ thống</span> (quản lý người dùng,
              dữ liệu và cấu hình). Bạn có chắc chắn muốn cấp quyền Quản trị viên?
            </p>
            <div className="flex gap-2 justify-end">
              <button onClick={() => setShowAdminConfirm(false)} className="px-4 py-2 text-sm border border-gray-300 rounded-md hover:bg-gray-50">Hủy</button>
              <button onClick={() => void doSubmit()} disabled={submitting}
                className="px-4 py-2 text-sm bg-amber-600 text-white rounded-md hover:bg-amber-700 disabled:opacity-50">
                {submitting ? 'Đang lưu...' : 'Xác nhận cấp quyền'}
              </button>
            </div>
          </div>
        </div>
      )}

      {resetTarget && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
          <div className="absolute inset-0 bg-black/50" onClick={() => { setResetTarget(null); setResetPwValue(''); }} />
          <div className="relative bg-white rounded-lg shadow-xl w-full max-w-sm p-6">
            <h3 className="text-lg font-semibold mb-1">Đặt lại mật khẩu</h3>
            <p className="text-sm text-gray-500 mb-4">Tài khoản: <span className="font-medium text-gray-700">{resetTarget.fullName}</span></p>
            <form onSubmit={handleReset} className="space-y-3">
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Mật khẩu mới <span className="text-red-500">*</span></label>
                <input type="text" value={resetPwValue} onChange={(e) => { setResetPwValue(e.target.value); setResetError(''); }}
                  placeholder="Nhập mật khẩu mới"
                  className={`block w-full border rounded-md px-3 py-2 text-sm font-mono focus:outline-none focus:ring-2 focus:ring-blue-500 ${resetError ? 'border-red-400' : 'border-gray-300'}`} />
                <p className="text-xs text-gray-500 mt-1">{PASSWORD_HINT}</p>
                {resetError && <p className="text-xs text-red-600 mt-1">{resetError}</p>}
              </div>
              <div className="flex gap-2 justify-end pt-1">
                <button type="button" onClick={() => { setResetTarget(null); setResetPwValue(''); }} className="px-4 py-2 text-sm border border-gray-300 rounded-md hover:bg-gray-50">Hủy</button>
                <button type="submit" disabled={resetPassword.isPending} className="px-4 py-2 text-sm bg-blue-600 text-white rounded-md hover:bg-blue-700 disabled:opacity-50">
                  {resetPassword.isPending ? 'Đang lưu...' : 'Đặt lại'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
