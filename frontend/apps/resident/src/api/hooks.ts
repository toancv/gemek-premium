import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { apiClient } from './client';
import type { AnnouncementItem, TicketDetailItem } from './types';

const get = (url: string, params?: Record<string, unknown>) =>
  apiClient.get(url, { params }).then((r) => r.data);
const post = (url: string, data?: unknown) => apiClient.post(url, data).then((r) => r.data);
const put = (url: string, data?: unknown) => apiClient.put(url, data).then((r) => r.data);
const del = (url: string) => apiClient.delete(url).then((r) => r.data);

export const useMe = () =>
  useQuery({ queryKey: ['me'], queryFn: () => get('/auth/me') });

export const useMyTickets = (params?: Record<string, unknown>) =>
  useQuery({ queryKey: ['tickets', params], queryFn: () => get('/tickets', params) });

// /residents/me returns ALL active residencies (multi-residency): 0, 1, or 2+, primary first.
// Typed as an array so consumers cannot accidentally treat it as a single object.
export const useMyResident = () =>
  useQuery<any[]>({ queryKey: ['my-resident'], queryFn: () => get('/residents/me') });

export const useTicket = (id: string) =>
  useQuery<TicketDetailItem>({ queryKey: ['tickets', id], queryFn: () => get(`/tickets/${id}`), enabled: !!id });

export const useFollowTicket = () => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => post(`/tickets/${id}/follow`),
    meta: { skipErrorToast: true, successMessage: 'Đã theo dõi phản ánh.' },
    onSuccess: (_d, id) => qc.invalidateQueries({ queryKey: ['tickets', id] }),
  });
};

export const useUnfollowTicket = () => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => del(`/tickets/${id}/follow`),
    meta: { skipErrorToast: true, successMessage: 'Đã bỏ theo dõi phản ánh.' },
    onSuccess: (_d, id) => qc.invalidateQueries({ queryKey: ['tickets', id] }),
  });
};

export const useCreateTicket = () => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (data: unknown) => post('/tickets', data),
    meta: { skipErrorToast: true, successMessage: 'Đã gửi phản ánh.' },
    onSuccess: () => qc.invalidateQueries({ queryKey: ['tickets'] }),
  });
};

export const useRateTicket = () => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, data }: { id: string; data: unknown }) =>
      post(`/tickets/${id}/rate`, data),
    meta: { skipErrorToast: true },
    onSuccess: (_d, v) => qc.invalidateQueries({ queryKey: ['tickets', v.id] }),
  });
};

export const useAmenities = () =>
  useQuery({ queryKey: ['amenities'], queryFn: () => get('/amenities') });

export const useMyBookings = (params?: Record<string, unknown>) =>
  useQuery({ queryKey: ['my-bookings', params], queryFn: () => get('/amenity-bookings', params) });

export const useCreateBooking = () => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (data: unknown) => post('/amenity-bookings', data),
    meta: { skipErrorToast: true, skipSuccessToast: true },
    onSuccess: () => qc.invalidateQueries({ queryKey: ['my-bookings'] }),
  });
};

export const useCancelBooking = () => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => put(`/amenity-bookings/${id}/cancel`, {}),
    meta: { skipErrorToast: true, successMessage: 'Đã hủy đặt chỗ' },
    onSuccess: () => qc.invalidateQueries({ queryKey: ['my-bookings'] }),
  });
};

export const useParkingAssignments = () =>
  useQuery({ queryKey: ['my-parking'], queryFn: () => get('/parking/assignments', { isActive: true }) });

export const useAnnouncements = (params?: Record<string, unknown>) =>
  useQuery({ queryKey: ['announcements', params], queryFn: () => get('/announcements', params) });

export const useAnnouncement = (id: string) =>
  useQuery<AnnouncementItem>({ queryKey: ['announcements', id], queryFn: () => get(`/announcements/${id}`), enabled: !!id });

export const useMarkAnnouncementRead = () => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => post(`/announcements/${id}/read`),
    meta: { skipSuccessToast: true },
    onSuccess: () => qc.invalidateQueries({ queryKey: ['announcements'] }),
  });
};

export const useCreateVehicle = () => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (data: unknown) => post('/vehicles', data),
    meta: { skipErrorToast: true, successMessage: 'Đã đăng ký phương tiện.' },
    onSuccess: () => qc.invalidateQueries({ queryKey: ['my-vehicles'] }),
  });
};

export const useChangePassword = () =>
  useMutation({ mutationFn: (data: unknown) => put('/auth/me/password', data), meta: { skipErrorToast: true, successMessage: 'Đổi mật khẩu thành công.' } });

export const useUpdateOwnProfile = () => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (data: { fullName: string; phone: string; email?: string }) => put('/auth/me/profile', data),
    // Errors shown inline on the form (skipErrorToast); success → center toast.
    meta: { skipErrorToast: true, successMessage: 'Cập nhật thông tin thành công.' },
    // Refetch the same ['me'] key useMe registers so the view reflects new values (no stale).
    onSuccess: () => qc.invalidateQueries({ queryKey: ['me'] }),
  });
};

export const useNotifications = () =>
  useQuery({ queryKey: ['notifications'], queryFn: () => get('/notifications', { size: 20 }) });

export const useUnreadCount = () =>
  useQuery({ queryKey: ['unread-count'], queryFn: () => get('/notifications/unread-count') });

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
