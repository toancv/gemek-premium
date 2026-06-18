/**
 * The authenticated user's own profile, as returned by GET /api/auth/me and by
 * PUT /api/auth/me/profile (the update echoes the same shape). Used by the
 * self-service profile page. `role` is read-only here — it is never editable on
 * the self-profile path (the BE rejects role changes on this endpoint).
 */
export interface MyProfile {
  id: string;
  email: string | null;
  fullName: string;
  phone: string;
  role: 'ADMIN' | 'BOARD_MEMBER' | 'TECHNICIAN' | 'RESIDENT';
  avatarUrl: string | null;
  isActive: boolean;
  lastLoginAt: string | null;
}
