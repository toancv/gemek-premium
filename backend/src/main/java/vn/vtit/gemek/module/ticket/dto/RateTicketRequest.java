/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.ticket.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Request body for a resident submitting a satisfaction rating on a completed ticket.
 *
 * <p>Rating is only accepted when the ticket status is {@code DONE} and no prior rating exists.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RateTicketRequest {

    /** Satisfaction score from 1 (worst) to 5 (best). */
    @NotNull(message = "rating is required.")
    @Min(value = 1, message = "rating must be at least 1.")
    @Max(value = 5, message = "rating must be at most 5.")
    private Integer rating;

    /** Optional written comment to accompany the rating. */
    @Size(max = 500, message = "comment must not exceed 500 characters.")
    private String comment;
}
