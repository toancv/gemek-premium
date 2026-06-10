import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { apiClient } from './client';

const get = (url: string, params?: Record<string, unknown>) =>
  apiClient.get(url, { params }).then((r) => r.data);
const post = (url: string, data?: unknown) => apiClient.post(url, data).then((r) => r.data);
const put = (url: string, data?: unknown) => apiClient.put(url, data).then((r) => r.data);

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

// Tickets
export const useTickets = (params?: Record<string, unknown>) =>
  useQuery({ queryKey: ['tickets', params], queryFn: () => get('/tickets', params) });

export const useTicket = (id: string) =>
  useQuery({ queryKey: ['tickets', id], queryFn: () => get(`/tickets/${id}`), enabled: !!id });

export const useCreateTicket = () => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (data: unknown) => post('/tickets', data),
    meta: { skipErrorToast: true },
    onSuccess: () => qc.invalidateQueries({ queryKey: ['tickets'] }),
  });
};

export const useAssignTicket = () => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, data }: { id: string; data: unknown }) => put(`/tickets/${id}/assign`, data),
    meta: { skipErrorToast: true },
    onSuccess: (_d, v) => qc.invalidateQueries({ queryKey: ['tickets', v.id] }),
  });
};

export const useUpdateTicketStatus = () => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, data }: { id: string; data: unknown }) => put(`/tickets/${id}/status`, data),
    meta: { skipErrorToast: true },
    onSuccess: (_d, v) => { qc.invalidateQueries({ queryKey: ['tickets', v.id] }); qc.invalidateQueries({ queryKey: ['tickets'] }); },
  });
};

// Contractors
export const useContractors = (params?: Record<string, unknown>) =>
  useQuery({ queryKey: ['contractors', params], queryFn: () => get('/contractors', params) });

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

// Announcements
export const useAnnouncements = (params?: Record<string, unknown>) =>
  useQuery({ queryKey: ['announcements', params], queryFn: () => get('/announcements', params) });

export const useCreateAnnouncement = () => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (data: unknown) => post('/announcements', data),
    meta: { skipErrorToast: true, successMessage: 'Đã tạo thông báo.' },
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
    meta: { skipErrorToast: true },
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

export const useMarkAllRead = () => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: () => put('/notifications/read-all'),
    meta: { skipSuccessToast: true },
    onSuccess: () => qc.invalidateQueries({ queryKey: ['notifications'] }),
  });
};
