import React from 'react';

type BadgeVariant = 'default' | 'success' | 'warning' | 'danger' | 'info' | 'purple';

interface BadgeProps {
  variant?: BadgeVariant;
  children: React.ReactNode;
  className?: string;
}

const variantClasses: Record<BadgeVariant, string> = {
  default: 'bg-gray-100 text-gray-700',
  success: 'bg-green-100 text-green-700',
  warning: 'bg-yellow-100 text-yellow-700',
  danger: 'bg-red-100 text-red-700',
  info: 'bg-blue-100 text-blue-700',
  purple: 'bg-purple-100 text-purple-700',
};

export function Badge({ variant = 'default', children, className = '' }: BadgeProps) {
  return (
    <span className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium ${variantClasses[variant]} ${className}`}>
      {children}
    </span>
  );
}

// Maps ticket status → badge variant
export function StatusBadge({ status }: { status: string }) {
  const map: Record<string, { variant: BadgeVariant; label: string }> = {
    NEW: { variant: 'info', label: 'New' },
    ASSIGNED: { variant: 'purple', label: 'Assigned' },
    IN_PROGRESS: { variant: 'warning', label: 'In Progress' },
    DONE: { variant: 'success', label: 'Done' },
    CANCELLED: { variant: 'default', label: 'Cancelled' },
    PENDING: { variant: 'warning', label: 'Pending' },
    APPROVED: { variant: 'success', label: 'Approved' },
    REJECTED: { variant: 'danger', label: 'Rejected' },
    COMPLETED: { variant: 'success', label: 'Completed' },
    AVAILABLE: { variant: 'success', label: 'Available' },
    OCCUPIED: { variant: 'danger', label: 'Occupied' },
    RESERVED: { variant: 'warning', label: 'Reserved' },
    ACTIVE: { variant: 'success', label: 'Active' },
    EXPIRED: { variant: 'default', label: 'Expired' },
    TERMINATED: { variant: 'danger', label: 'Terminated' },
  };
  const entry = map[status] ?? { variant: 'default' as BadgeVariant, label: status };
  return <Badge variant={entry.variant}>{entry.label}</Badge>;
}

export function CategoryBadge({ category }: { category: string }) {
  const map: Record<string, { variant: BadgeVariant; label: string }> = {
    MAINTENANCE_REPAIR: { variant: 'warning', label: 'Maintenance' },
    COMPLAINT: { variant: 'danger', label: 'Complaint' },
    ADMINISTRATIVE: { variant: 'info', label: 'Administrative' },
    SUGGESTION_FEEDBACK: { variant: 'purple', label: 'Suggestion' },
    OTHER: { variant: 'default', label: 'Other' },
  };
  const entry = map[category] ?? { variant: 'default' as BadgeVariant, label: category };
  return <Badge variant={entry.variant}>{entry.label}</Badge>;
}
