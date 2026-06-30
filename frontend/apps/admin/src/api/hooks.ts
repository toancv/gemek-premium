import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { apiClient } from './client';
import type { MyProfile } from '../types/profile';

const get = (url: string, params?: Record<string, unknown>) =>
  apiClient.get(url, { params }).then((r) => r.data);
const post = (url: string, data?: unknown) => apiClient.post(url, data).then((r) => r.data);
const put = (url: string, data?: unknown) => apiClient.put(url, data).then((r) => r.data);
const del = (url: string) => apiClient.delete(url).then((r) => r.data);

// Self-service profile (the authenticated user's own account)
// useMe is the canonical read of the current user's full profile (incl. email,
// which authStore's lighter AuthUser does not hold). The profile page reads it
// and refetches it after an update so the displayed values never go stale.
export const useMe = () =>
  useQuery<MyProfile>({ queryKey: ['me'], queryFn: () => get('/auth/me') });

export const useUpdateOwnProfile = () => {
  const qc = useQueryClient();
  return useMutation({
    // Identity is server-derived from the principal; the body carries only the
    // three editable fields. Phone/email uniqueness excludes the caller's own row.
    mutationFn: (data: { fullName: string; phone: string; email: string | null }) =>
      put('/auth/me/profile', data) as Promise<MyProfile>,
    meta: { skipErrorToast: true, successMessage: 'Đã cập nhật thông tin cá nhân.' },
    onSuccess: () => qc.invalidateQueries({ queryKey: ['me'] }),
  });
};

export const useChangeOwnPassword = () =>
  useMutation({
    // Separate endpoint from the profile update — never merged. Verifies the
    // current password; wrong value → WRONG_CURRENT_PASSWORD (422). Token survives.
    mutationFn: (data: { currentPassword: string; newPassword: string }) =>
      put('/auth/me/password', data),
    meta: { skipErrorToast: true, successMessage: 'Đã đổi mật khẩu.' },
  });

// Dashboard
export const useDashboard = () =>
  useQuery({ queryKey: ['dashboard'], queryFn: () => get('/reports/dashboard') });

// Apartments
export const useApartments = (params?: Record<string, unknown>) =>
  useQuery({ queryKey: ['apartments', params], queryFn: () => get('/apartments', params) });

export const useApartment = (id: string) =>
  useQuery({ queryKey: ['apartments', id], queryFn: () => get(`/apartments/${id}`), enabled: !!id });

export const useCreateApartment = () => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (data: unknown) => post('/apartments', data),
    meta: { skipErrorToast: true, successMessage: 'Thêm căn hộ thành công' },
    onSuccess: () => qc.invalidateQueries({ queryKey: ['apartments'] }),
  });
};

export const useUpdateApartment = () => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, data }: { id: string; data: unknown }) => put(`/apartments/${id}`, data),
    meta: { skipErrorToast: true, successMessage: 'Cập nhật căn hộ thành công' },
    onSuccess: () => qc.invalidateQueries({ queryKey: ['apartments'] }),
  });
};

export const useBlocks = () =>
  useQuery({ queryKey: ['blocks'], queryFn: () => get('/blocks', { size: 200 }) });

// Users
export const useUsers = (params?: Record<string, unknown>) =>
  useQuery({ queryKey: ['users', params], queryFn: () => get('/users', params) });

export const useCreateUser = () => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (data: unknown) => post('/users', data),
    meta: { skipErrorToast: true, successMessage: 'Đã tạo tài khoản.' },
    // refetchType:'all' so an INACTIVE users list (Account tab not currently mounted) is refetched
    // too, not just marked stale — fixes the stale-status-until-F5 bug. See
    // reports/stale-ui-after-mutation-diagnosis.md.
    onSuccess: () => qc.invalidateQueries({ queryKey: ['users'], refetchType: 'all' }),
  });
};

export const useUpdateUser = () => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, data }: { id: string; data: unknown }) => put(`/users/${id}`, data),
    meta: { skipErrorToast: true, successMessage: 'Đã cập nhật tài khoản.' },
    // refetchType:'all' so an INACTIVE users list (Account tab not currently mounted) is refetched
    // too, not just marked stale — fixes the stale-status-until-F5 bug. See
    // reports/stale-ui-after-mutation-diagnosis.md.
    onSuccess: () => qc.invalidateQueries({ queryKey: ['users'], refetchType: 'all' }),
  });
};

export const useDeactivateUser = () => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => del(`/users/${id}`),
    meta: { skipErrorToast: true, successMessage: 'Đã vô hiệu hóa tài khoản.' },
    // refetchType:'all' so an INACTIVE users list (Account tab not currently mounted) is refetched
    // too, not just marked stale — fixes the stale-status-until-F5 bug. See
    // reports/stale-ui-after-mutation-diagnosis.md.
    onSuccess: () => qc.invalidateQueries({ queryKey: ['users'], refetchType: 'all' }),
  });
};

export const useResetUserPassword = () => {
  return useMutation({
    mutationFn: ({ id, newPassword }: { id: string; newPassword: string }) =>
      put(`/users/${id}/reset-password`, { newPassword }),
    meta: { skipErrorToast: true, successMessage: 'Đã đặt lại mật khẩu.' },
  });
};

// Residents
export const useResidents = (params?: Record<string, unknown>) =>
  useQuery({ queryKey: ['residents', params], queryFn: () => get('/residents', params) });

export const useCreateResident = () => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (data: unknown) => post('/residents', data),
    meta: { skipErrorToast: true, successMessage: 'Tạo cư dân thành công' },
    onSuccess: () => qc.invalidateQueries({ queryKey: ['residents'] }),
  });
};

/** Move-out request body. moveOutDate is a pure ISO date (yyyy-mm-dd); notes optional. */
export interface MoveOutPayload {
  id: string;
  moveOutDate: string;
  notes?: string;
}

export const useMoveOutResident = () => {
  const qc = useQueryClient();
  return useMutation({
    // Soft end-of-residency: sets moveOutDate, clears primary-contact flag, logs MOVED_OUT.
    mutationFn: ({ id, moveOutDate, notes }: MoveOutPayload) =>
      post(`/residents/${id}/move-out`, { moveOutDate, ...(notes ? { notes } : {}) }),
    meta: { skipErrorToast: true, successMessage: 'Đã kết thúc cư trú của cư dân.' },
    onSuccess: () => qc.invalidateQueries({ queryKey: ['residents'] }),
  });
};

// Tickets
export const useTickets = (params?: Record<string, unknown>) =>
  useQuery({ queryKey: ['tickets', params], queryFn: () => get('/tickets', params) });

// Ticket stat count — derived from the role-scoped list endpoint. Reads PageResponse.total
// (whole-dataset count for the caller's authorized scope), NOT page rows; size:1 minimizes
// payload. Technician-safe (list @PreAuthorize admits TECHNICIAN) and ticket-only. See
// reports/c-p2.5-ticketstats-source.md.
export const useTicketCount = (filter: Record<string, unknown>) =>
  useQuery({
    queryKey: ['ticket-count', filter],
    queryFn: () => get('/tickets', { ...filter, page: 0, size: 1 }).then((r) => r.total as number),
  });

export const useTicket = (id: string) =>
  useQuery({ queryKey: ['tickets', id], queryFn: () => get(`/tickets/${id}`), enabled: !!id });

export const useAssignTicket = () => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, data }: { id: string; data: unknown }) => put(`/tickets/${id}/assign`, data),
    meta: { skipErrorToast: true, successMessage: 'Đã phân công yêu cầu.' },
    onSuccess: (_d, v) => qc.invalidateQueries({ queryKey: ['tickets', v.id] }),
  });
};

export const useUpdateTicketStatus = () => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, data }: { id: string; data: unknown }) => put(`/tickets/${id}/status`, data),
    meta: { skipErrorToast: true, successMessage: 'Đã cập nhật trạng thái.' },
    onSuccess: (_d, v) => { qc.invalidateQueries({ queryKey: ['tickets', v.id] }); qc.invalidateQueries({ queryKey: ['tickets'] }); },
  });
};

// Contractors
export const useContractors = (params?: Record<string, unknown>) =>
  useQuery({ queryKey: ['contractors', params], queryFn: () => get('/contractors', params) });

// Single-contractor detail. Used by the edit page to populate the form. Same key family as the list
// (['contractors', ...]) so a create/update invalidation refreshes both list and detail.
export const useContractor = (id: string) =>
  useQuery({ queryKey: ['contractors', id], queryFn: () => get(`/contractors/${id}`), enabled: !!id });

export const useCreateContractor = () => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (data: unknown) => post('/contractors', data),
    meta: { skipErrorToast: true, successMessage: 'Thêm nhà thầu thành công' },
    onSuccess: () => qc.invalidateQueries({ queryKey: ['contractors'] }),
  });
};

export const useUpdateContractor = () => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, data }: { id: string; data: unknown }) => put(`/contractors/${id}`, data),
    meta: { skipErrorToast: true, successMessage: 'Cập nhật nhà thầu thành công' },
    onSuccess: () => qc.invalidateQueries({ queryKey: ['contractors'] }),
  });
};

// Contractor documents (P1 endpoints, staff-only forced-download). Unlike announcement attachments
// (embedded in the draft detail manifest), the contractor document list is a SEPARATE GET endpoint, so
// the manager owns its own query (['contractors', id, 'documents']) and the mutations invalidate it.
// refetchType:'all' matches the announcement managers (the query may be inactive at invalidation time).

// Lists one contractor's documents, each with a freshly-minted forced-download `downloadUrl`.
export const useContractorDocuments = (id: string) =>
  useQuery({
    queryKey: ['contractors', id, 'documents'],
    queryFn: () => get(`/contractors/${id}/documents`),
    enabled: !!id,
  });

// Upload one document (multipart `file` only — Tika magic-byte validates type on the BE).
export const useUploadContractorDocument = () => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, file }: { id: string; file: File }) => {
      const fd = new FormData();
      fd.append('file', file);
      return post(`/contractors/${id}/documents`, fd);
    },
    meta: { skipErrorToast: true, successMessage: 'Đã tải lên tài liệu.' },
    onSuccess: (_res, { id }) =>
      qc.invalidateQueries({ queryKey: ['contractors', id, 'documents'], refetchType: 'all' }),
  });
};

// Delete one document row (204).
export const useDeleteContractorDocument = () => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, documentId }: { id: string; documentId: string }) =>
      del(`/contractors/${id}/documents/${documentId}`),
    meta: { skipErrorToast: true, successMessage: 'Đã xoá tài liệu.' },
    onSuccess: (_res, { id }) =>
      qc.invalidateQueries({ queryKey: ['contractors', id, 'documents'], refetchType: 'all' }),
  });
};

// Announcements
export const useAnnouncements = (params?: Record<string, unknown>) =>
  useQuery({ queryKey: ['announcements', params], queryFn: () => get('/announcements', params) });

// Single-announcement detail (incl. full content + media manifest). Used by the edit page to
// populate the form. Same key family as the list (['announcements', ...]) so a write invalidates both.
export const useAnnouncement = (id: string) =>
  useQuery({ queryKey: ['announcements', id], queryFn: () => get(`/announcements/${id}`), enabled: !!id });

export const useCreateAnnouncement = () => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (data: unknown) => post('/announcements', data),
    meta: { skipErrorToast: true, successMessage: 'Đã tạo thông báo.' },
    onSuccess: () => qc.invalidateQueries({ queryKey: ['announcements'] }),
  });
};

// Update a DRAFT announcement (BE PUT /announcements/{id}, ADMIN, draft-only — published is immutable).
// Invalidates the whole ['announcements'] family so both the list and this draft's detail refetch.
export const useUpdateAnnouncement = () => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, data }: { id: string; data: unknown }) => put(`/announcements/${id}`, data),
    meta: { skipErrorToast: true, successMessage: 'Đã cập nhật thông báo.' },
    onSuccess: () => qc.invalidateQueries({ queryKey: ['announcements'] }),
  });
};

export const usePublishAnnouncement = () => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => post(`/announcements/${id}/publish`),
    meta: { skipErrorToast: true, successMessage: 'Đã đăng thông báo.' },
    onSuccess: () => qc.invalidateQueries({ queryKey: ['announcements'] }),
  });
};

// Announcement media (C2.2 endpoints, draft-only, ADMIN). Used by the media manager on /:id/edit.
// Both mutations refetch the draft detail (['announcements', id]) so the manifest — thumbnails AND
// the preview's presigned URLs — refreshes after every upload/delete. refetchType:'all' is required
// because the detail query may be inactive at the moment of invalidation (same stale-after-mutation
// fix applied to the user/ticket lists).

// Upload one image. FormData carries `file` + `kind` (COVER|INLINE — case-insensitive on the BE);
// axios sets the multipart boundary automatically for a FormData body. Returns the created media
// row (incl. `id`) so the caller can drop an `announcement-media:{id}` placeholder at the cursor.
export const useUploadAnnouncementMedia = () => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, file, kind }: { id: string; file: File; kind: 'COVER' | 'INLINE' }) => {
      const fd = new FormData();
      fd.append('file', file);
      fd.append('kind', kind);
      return post(`/announcements/${id}/media`, fd) as Promise<{ id: string; kind: 'COVER' | 'INLINE' }>;
    },
    meta: { skipErrorToast: true },
    onSuccess: (_res, { id }) =>
      qc.invalidateQueries({ queryKey: ['announcements', id], refetchType: 'all' }),
  });
};

// Delete one media row (204). Body text is NOT auto-rewritten — a placeholder left behind simply
// resolves to nothing (C2.3a unknown-id behaviour). Refetches the draft detail to refresh the grid.
export const useDeleteAnnouncementMedia = () => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, mediaId }: { id: string; mediaId: string }) =>
      del(`/announcements/${id}/media/${mediaId}`),
    meta: { skipErrorToast: true },
    onSuccess: (_res, { id }) =>
      qc.invalidateQueries({ queryKey: ['announcements', id], refetchType: 'all' }),
  });
};

// Upload one document attachment (C3). FormData carries `file` only (no kind — attachments are a flat
// list, not cover/inline). Type is validated by Tika magic-byte on the BE (pdf/docx/xlsx/pptx/txt).
// Refetches the draft detail so the attachments[] list (incl. forced-download URLs) refreshes.
export const useUploadAnnouncementAttachment = () => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, file }: { id: string; file: File }) => {
      const fd = new FormData();
      fd.append('file', file);
      return post(`/announcements/${id}/attachments`, fd);
    },
    meta: { skipErrorToast: true },
    onSuccess: (_res, { id }) =>
      qc.invalidateQueries({ queryKey: ['announcements', id], refetchType: 'all' }),
  });
};

// Delete one attachment row (204, draft-only). No body placeholder to strip (attachments are never
// inline). Refetches the draft detail to refresh the list.
export const useDeleteAnnouncementAttachment = () => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, attachmentId }: { id: string; attachmentId: string }) =>
      del(`/announcements/${id}/attachments/${attachmentId}`),
    meta: { skipErrorToast: true },
    onSuccess: (_res, { id }) =>
      qc.invalidateQueries({ queryKey: ['announcements', id], refetchType: 'all' }),
  });
};

// Amenities
export const useAmenities = () =>
  useQuery({ queryKey: ['amenities'], queryFn: () => get('/amenities') });

export const useCreateAmenity = () => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (data: unknown) => post('/amenities', data),
    meta: { skipErrorToast: true, successMessage: 'Tạo tiện ích thành công.' },
    onSuccess: () => qc.invalidateQueries({ queryKey: ['amenities'] }),
  });
};

export const useUpdateAmenity = () => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, data }: { id: string; data: unknown }) => put(`/amenities/${id}`, data),
    meta: { skipErrorToast: true, successMessage: 'Cập nhật tiện ích thành công.' },
    onSuccess: () => qc.invalidateQueries({ queryKey: ['amenities'] }),
  });
};

export const useAmenityBookings = (params?: Record<string, unknown>) =>
  useQuery({ queryKey: ['amenity-bookings', params], queryFn: () => get('/amenity-bookings', params) });

export const useApproveBooking = () => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => put(`/amenity-bookings/${id}/approve`, { status: 'APPROVED' }),
    meta: { successMessage: 'Đã duyệt đặt chỗ', skipErrorToast: true },
    onSuccess: () => qc.invalidateQueries({ queryKey: ['amenity-bookings'] }),
  });
};

export const useRejectBooking = () => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, reason }: { id: string; reason: string }) =>
      put(`/amenity-bookings/${id}/approve`, { status: 'REJECTED', rejectionReason: reason }),
    meta: { successMessage: 'Đã từ chối đặt chỗ', skipErrorToast: true },
    onSuccess: () => qc.invalidateQueries({ queryKey: ['amenity-bookings'] }),
  });
};

// Parking
export const useParkingSlots = (params?: Record<string, unknown>) =>
  useQuery({ queryKey: ['parking-slots', params], queryFn: () => get('/parking/slots', params) });

export const useGuestVehicles = (params?: Record<string, unknown>) =>
  useQuery({ queryKey: ['guest-vehicles', params], queryFn: () => get('/parking/guest-vehicles', params) });

export const useCreateParkingAssignment = () => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (data: any) => post(`/parking/slots/${data.parkingSlotId}/assign`, data),
    meta: { skipErrorToast: true, successMessage: 'Đã phân công chỗ đậu xe.' },
    onSuccess: () => qc.invalidateQueries({ queryKey: ['parking-slots'] }),
  });
};

export const useEndParkingAssignment = () => {
  const qc = useQueryClient();
  return useMutation({
    // id must be the SLOT UUID (not assignment UUID); BE: POST /parking/slots/{id}/unassign
    mutationFn: ({ id, data }: { id: string; data: unknown }) => post(`/parking/slots/${id}/unassign`, data),
    meta: { skipErrorToast: true, successMessage: 'Đã kết thúc phân công chỗ đậu xe' },
    onSuccess: () => qc.invalidateQueries({ queryKey: ['parking-slots'] }),
  });
};

// Vehicles
export const useVehicles = (params?: Record<string, unknown>) =>
  useQuery({ queryKey: ['vehicles', params], queryFn: () => get('/vehicles', params) });

export const useCreateVehicle = () => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (data: unknown) => post('/vehicles', data),
    meta: { skipErrorToast: true, successMessage: 'Đã thêm phương tiện.' },
    onSuccess: () => qc.invalidateQueries({ queryKey: ['vehicles'] }),
  });
};

// Reports
export const useTicketReport = (params?: Record<string, unknown>) =>
  useQuery({ queryKey: ['report-tickets', params], queryFn: () => get('/reports/tickets', params) });

export const useAmenityReport = (params?: Record<string, unknown>) =>
  useQuery({ queryKey: ['report-amenities', params], queryFn: () => get('/reports/amenity-usage', params) });

export const useContractsExpiringReport = (params?: Record<string, unknown>) =>
  useQuery({ queryKey: ['report-contracts', params], queryFn: () => get('/reports/contracts-expiring', params) });

// Notifications
export const useNotifications = () =>
  useQuery({ queryKey: ['notifications'], queryFn: () => get('/notifications', { size: 20 }) });

export const useUnreadCount = () =>
  useQuery({ queryKey: ['unread-count'], queryFn: () => get('/notifications/unread-count') });

export const useMarkAllRead = () => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: () => post('/notifications/read-all'),
    meta: { skipSuccessToast: true },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['notifications'] });
      qc.invalidateQueries({ queryKey: ['unread-count'] });
    },
  });
};

export const useMarkNotificationRead = () => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => post(`/notifications/${id}/read`),
    meta: { skipSuccessToast: true },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['notifications'] });
      qc.invalidateQueries({ queryKey: ['unread-count'] });
    },
  });
};
