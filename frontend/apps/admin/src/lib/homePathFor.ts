/**
 * Role-aware landing path for the admin portal (backlog (c) P2 STEP B, dashboard Option 2).
 *
 * TECHNICIAN's only admin surface is the Tickets page (`/dashboard` is guarded to
 * [ADMIN, BOARD_MEMBER] because it bundles contract-expiry + occupancy business data). Every
 * redirect that previously hardcoded `/dashboard` (index, catch-all, deferred page redirects, the
 * RequireRole forbidden-fallback, the LoginPage post-login navigate) routes through this helper so a
 * TECHNICIAN — once admitted to the portal in P3 — never lands on a route their role cannot reach
 * (which would otherwise bounce back to the same forbidden route → redirect loop).
 *
 * @param role The authenticated user's role, or undefined when no user is loaded.
 * @return `/tickets` for TECHNICIAN; `/dashboard` for everyone else.
 */
export function homePathFor(role: string | undefined): string {
  // TECHNICIAN → tickets-only surface; ADMIN / BOARD_MEMBER (and the undefined no-user fallback) → dashboard.
  return role === 'TECHNICIAN' ? '/tickets' : '/dashboard';
}
