import { toast } from '../components/Toast';

export const mutationCacheHandlers = {
  onSuccess: (_data: unknown, _variables: unknown, _context: unknown, mutation: unknown) => {
    const meta = (mutation as any)?.meta as Record<string, unknown> | undefined;
    if (meta?.skipSuccessToast) return;
    const msg = (meta?.successMessage as string) ?? 'Thao tác thành công';
    toast.success(msg);
  },
  onError: (error: unknown, _variables: unknown, _context: unknown, mutation: unknown) => {
    const meta = (mutation as any)?.meta as Record<string, unknown> | undefined;
    if (meta?.skipErrorToast) return;
    const err = error as { response?: { status?: number; data?: { message?: string } } };
    const status = err?.response?.status;
    const serverMsg = err?.response?.data?.message;
    if (!status) {
      toast.error('Không thể kết nối đến máy chủ, vui lòng thử lại.');
      return;
    }
    if (status >= 500) {
      toast.error('Đã xảy ra lỗi hệ thống, vui lòng thử lại.');
      return;
    }
    if (status === 401) { toast.error('Phiên đăng nhập đã hết hạn, vui lòng đăng nhập lại.'); return; }
    if (status === 403) { toast.error('Bạn không có quyền thực hiện thao tác này.'); return; }
    toast.error(serverMsg ?? 'Đã xảy ra lỗi, vui lòng thử lại.');
  },
};
