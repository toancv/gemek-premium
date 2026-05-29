/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.resident.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * Request body for recording a resident move-out.
 *
 * <p>Sets the {@code moveOutDate} on the resident record and appends a MOVED_OUT
 * history entry. If the resident was the primary contact the flag is cleared.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class MoveOutRequest {

    /** The date on which the resident moved out. Must not be {@code null}. */
    @NotNull(message = "moveOutDate is required.")
    private LocalDate moveOutDate;

    /** Optional notes to record alongside the move-out event. */
    private String notes;
}
