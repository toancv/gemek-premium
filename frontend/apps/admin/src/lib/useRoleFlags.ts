import { useAuthStore } from '../store/authStore';

/**
 * Role flags derived from the authenticated user, for conditional rendering of
 * write controls. Each flag mirrors a BE {@code @PreAuthorize} allowed set so the
 * FE hides a write control exactly when the BE would reject it with 403
 * (Direction A — backlog (c) BOARD_MEMBER 403 fix; see
 * reports/c-boardmember-403-diagnosis.md). BOARD_MEMBER is a read/oversight role
 * and therefore sees no write control on the admin pages.
 */
export function useRoleFlags() {
  const role = useAuthStore((s) => s.user?.role);
  return {
    role,
    isAdmin: role === 'ADMIN',
    isTechnician: role === 'TECHNICIAN',
    isBoardMember: role === 'BOARD_MEMBER',
  };
}
