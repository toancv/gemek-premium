/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.parking.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * Request DTO for recording a guest vehicle exit.
 *
 * <p>If {@code exitTime} is {@code null} the service defaults it to {@link OffsetDateTime#now()}.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class CheckoutRequest {

    /**
     * Timestamp when the guest vehicle exited the premises.
     * Defaults to server current time if {@code null}.
     */
    private OffsetDateTime exitTime;
}
