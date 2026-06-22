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

/**
 * PARTIAL mirror of BE TicketDetailResponse — only the N3 P7 viewer-flag fields
 * are typed; the rest of the detail shape is still untyped any-debt (tracked in
 * PROGRESS.md). The index signature keeps existing untyped page access compiling.
 */
export interface TicketDetailItem {
  id: string;
  /** Creator-chosen community visibility flag. */
  isPublic: boolean | null;
  /** FOLLOWER-row flag for the calling resident; null on staff/mutation responses. */
  isFollowing: boolean | null;
  /** True only when the BE produced the redacted public view (G8). */
  redacted: boolean;
  [key: string]: any;
}

/** Mirrors BE NotificationResponse (module.notification.dto.NotificationResponse). */
export interface NotificationItem {
  id: string;
  title: string;
  body: string | null;
  type: string;
  /** UUID of the related entity, paired with referenceType. */
  referenceId: string | null;
  /** Entity-type label (e.g. "Announcement") — drives bell deep-link routing. */
  referenceType: string | null;
  isRead: boolean;
  createdAt: string;
}
