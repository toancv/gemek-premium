/**
 * FE mirrors of backend response DTOs used on typed data paths.
 * Field lists mirror the BE DTOs exactly — do not add FE-only fields here.
 */

/** Mirrors BE AnnouncementResponse (module.announcement.dto.AnnouncementResponse). */
export interface AnnouncementItem {
  id: string;
  title: string;
  content: string;
  type: 'GENERAL' | 'URGENT' | 'MAINTENANCE' | 'AMENITY' | 'EVENT';
  targetScope: 'ALL' | 'BLOCK' | 'FLOOR';
  targetBlock: { id: string; name: string } | null;
  targetFloor: number | null;
  sendPush: boolean;
  sendEmail: boolean;
  sendSms: boolean;
  createdBy: { id: string; fullName: string } | null;
  /** null = draft (residents only ever receive published items). */
  publishedAt: string | null;
  createdAt: string;
  readByCount: number;
  /** Per-user read state computed by the BE for the requesting user. */
  isRead: boolean;
}
