/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.parking.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * Request DTO for ending a parking slot assignment.
 *
 * <p>If {@code endDate} is not supplied the service defaults it to {@link LocalDate#now()}.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class UnassignRequest {

    /**
     * Date on which the assignment ends.
     * Defaults to today if {@code null}.
     */
    private LocalDate endDate;
}
