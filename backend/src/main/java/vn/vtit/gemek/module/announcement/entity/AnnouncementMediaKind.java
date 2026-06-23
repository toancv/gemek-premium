/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.announcement.entity;

/**
 * Kind of an announcement media object.
 *
 * <p>{@link #COVER} — at most ONE per announcement (a second cover upload REPLACES the first).
 * {@link #INLINE} — many, bounded only by the per-announcement count / total-size caps.
 */
public enum AnnouncementMediaKind {

    /** The single hero/cover image of an announcement. */
    COVER,

    /** An in-body image referenced from the Markdown content. */
    INLINE
}
