/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.contractor.entity;

/**
 * Lifecycle states for a {@link Contract}, mapped to the PostgreSQL {@code contract_status} ENUM.
 */
public enum ContractStatus {
    /** Contract submitted but not yet activated. */
    PENDING,
    /** Contract is currently in force. */
    ACTIVE,
    /** Contract term has ended naturally. */
    EXPIRED,
    /** Contract was cancelled before natural expiry. */
    TERMINATED
}
