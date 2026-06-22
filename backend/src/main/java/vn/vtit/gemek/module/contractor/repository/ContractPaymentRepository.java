/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.contractor.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.vtit.gemek.module.contractor.entity.ContractPayment;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for the {@link ContractPayment} entity.
 */
@Repository
public interface ContractPaymentRepository extends JpaRepository<ContractPayment, UUID> {

    /**
     * Returns all payments for the given contract, most recent first.
     *
     * @param contractId the contract UUID.
     * @return list of payments ordered by payment date descending.
     */
    List<ContractPayment> findByContractIdOrderByPaymentDateDesc(UUID contractId);
}
