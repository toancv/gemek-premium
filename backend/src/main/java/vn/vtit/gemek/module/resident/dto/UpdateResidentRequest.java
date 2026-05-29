/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.resident.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import vn.vtit.gemek.module.resident.entity.ResidentType;

/**
 * Request body for updating mutable fields of an existing resident record.
 *
 * <p>All fields are optional. Omitting a field (leaving it {@code null}) signals
 * no change for that field. The service layer writes a history entry when
 * {@code type} changes or {@code isPrimaryContact} transitions to {@code true}.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class UpdateResidentRequest {

    /**
     * New resident type.
     * When non-{@code null} and different from the current type, a TYPE_CHANGED
     * history entry is written.
     */
    private ResidentType type;

    /**
     * New primary contact flag.
     * When set to {@code true}, all other active residents in the same apartment
     * have their flag cleared and a PRIMARY_CONTACT_SET history entry is written.
     */
    private Boolean isPrimaryContact;

    /** Optional notes to replace the current notes value. */
    private String notes;
}
