/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.announcement.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;

/**
 * Response DTO returned by the mark-read endpoint.
 *
 * <p>{@code alreadyRead} is {@code true} when a read record already existed before this call,
 * allowing the client to distinguish a fresh mark from an idempotent replay.
 */
@Getter
@Builder
public class MarkReadResponse {

    /**
     * {@code true} if the user had already read this announcement before this request.
     * {@code false} if this call created the read record for the first time.
     */
    private final boolean alreadyRead;

    /** Timestamp at which the read record was first created. */
    private final OffsetDateTime readAt;
}
