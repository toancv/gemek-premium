/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.amenity.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.vtit.gemek.module.amenity.entity.BookingStatus;

/**
 * Request DTO for the approve-or-reject booking action.
 *
 * <p>Only {@link BookingStatus#APPROVED} and {@link BookingStatus#REJECTED} are valid values
 * for {@code status}; validation is enforced in the service layer.
 * When {@code status = REJECTED}, {@code rejectionReason} should be provided.
 */
@Getter
@Setter
@NoArgsConstructor
public class ApproveRejectRequest {

    /** The target status; must be APPROVED or REJECTED. */
    @NotNull(message = "status is required")
    private BookingStatus status;

    /**
     * Reason for rejection.
     * Required when {@code status = REJECTED}; ignored for approvals.
     */
    private String rejectionReason;
}
