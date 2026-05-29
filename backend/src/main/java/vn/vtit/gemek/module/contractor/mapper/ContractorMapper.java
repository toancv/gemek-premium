/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.contractor.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import vn.vtit.gemek.module.contractor.dto.ContractPaymentResponse;
import vn.vtit.gemek.module.contractor.dto.ContractResponse;
import vn.vtit.gemek.module.contractor.dto.ContractorResponse;
import vn.vtit.gemek.module.contractor.dto.MaintenanceScheduleResponse;
import vn.vtit.gemek.module.contractor.entity.Contract;
import vn.vtit.gemek.module.contractor.entity.ContractPayment;
import vn.vtit.gemek.module.contractor.entity.Contractor;
import vn.vtit.gemek.module.contractor.entity.MaintenanceSchedule;

/**
 * MapStruct mapper for the contractor module.
 *
 * <p>Maps entities to their read-only response DTOs.
 * Spring component model is applied via the global compiler argument
 * {@code -Amapstruct.defaultComponentModel=spring}.
 */
@Mapper
public interface ContractorMapper {

    /**
     * Maps a {@link Contractor} entity to a {@link ContractorResponse}.
     *
     * @param contractor the contractor entity.
     * @return the contractor response DTO.
     */
    @Mapping(target = "active", source = "active")
    ContractorResponse toContractorResponse(Contractor contractor);

    /**
     * Maps a {@link Contract} entity to a {@link ContractResponse}.
     *
     * <p>The nested {@code contractor} and {@code createdBy} references are derived
     * from the entity associations and must be join-fetched before this method is called.
     *
     * @param contract the contract entity (contractor and createdBy must be loaded).
     * @return the contract response DTO.
     */
    @Mapping(target = "contractor.id",          source = "contractor.id")
    @Mapping(target = "contractor.companyName", source = "contractor.companyName")
    @Mapping(target = "createdBy.id",           source = "createdBy.id")
    @Mapping(target = "createdBy.fullName",     source = "createdBy.fullName")
    ContractResponse toContractResponse(Contract contract);

    /**
     * Maps a {@link ContractPayment} entity to a {@link ContractPaymentResponse}.
     *
     * @param payment the payment entity (contract and recordedBy must be loaded).
     * @return the payment response DTO.
     */
    @Mapping(target = "contract.id",    source = "contract.id")
    @Mapping(target = "contract.title", source = "contract.title")
    @Mapping(target = "recordedBy.id",       source = "recordedBy.id")
    @Mapping(target = "recordedBy.fullName", source = "recordedBy.fullName")
    ContractPaymentResponse toPaymentResponse(ContractPayment payment);

    /**
     * Maps a {@link MaintenanceSchedule} entity to a {@link MaintenanceScheduleResponse}.
     *
     * @param schedule the schedule entity (contract must be loaded).
     * @return the schedule response DTO.
     */
    @Mapping(target = "contract.id",    source = "contract.id")
    @Mapping(target = "contract.title", source = "contract.title")
    @Mapping(target = "active",         source = "active")
    MaintenanceScheduleResponse toScheduleResponse(MaintenanceSchedule schedule);
}
