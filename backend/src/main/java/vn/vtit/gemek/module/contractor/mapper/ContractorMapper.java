/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.contractor.mapper;

import org.mapstruct.Context;
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

import java.util.Map;
import java.util.UUID;

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
     * <p>{@code createdBy} is now a plain actor UUID (Spring Data auditing), not a
     * {@code User} association. The creator's display name is resolved from the
     * supplied {@code creatorNames} map — built ONCE per page by the caller via a
     * single {@code findAllById} — so a list mapping issues no per-row user query
     * (no N+1).
     *
     * @param contract     the contract entity.
     * @param creatorNames id&rarr;fullName map covering the createdBy UUIDs being mapped.
     * @return the contract response DTO.
     */
    @Mapping(target = "contractor.id",          source = "contractor.id")
    @Mapping(target = "contractor.companyName", source = "contractor.companyName")
    @Mapping(target = "createdBy",              source = "createdBy")
    ContractResponse toContractResponse(Contract contract, @Context Map<UUID, String> creatorNames);

    /**
     * Builds the slim creator reference from an actor UUID, resolving the display
     * name from the page-scoped {@code creatorNames} map.
     *
     * @param createdBy    the creator actor UUID; may be {@code null}.
     * @param creatorNames id&rarr;fullName map for the current page.
     * @return the user reference, or {@code null} when {@code createdBy} is null.
     */
    default ContractResponse.UserRef mapCreator(UUID createdBy, @Context Map<UUID, String> creatorNames) {
        // Null actor (system / seed / deleted user) -> no creator reference.
        if (createdBy == null) {
            return null;
        }
        return new ContractResponse.UserRef(createdBy, creatorNames.get(createdBy));
    }

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
