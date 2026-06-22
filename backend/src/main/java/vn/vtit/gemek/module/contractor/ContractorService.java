/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.contractor;

import org.springframework.data.domain.Pageable;
import vn.vtit.gemek.common.model.PageResponse;
import vn.vtit.gemek.module.contractor.dto.ContractPaymentResponse;
import vn.vtit.gemek.module.contractor.dto.ContractResponse;
import vn.vtit.gemek.module.contractor.dto.ContractorResponse;
import vn.vtit.gemek.module.contractor.dto.CreateContractPaymentRequest;
import vn.vtit.gemek.module.contractor.dto.CreateContractRequest;
import vn.vtit.gemek.module.contractor.dto.CreateContractorRequest;
import vn.vtit.gemek.module.contractor.dto.CreateMaintenanceScheduleRequest;
import vn.vtit.gemek.module.contractor.dto.MaintenanceScheduleResponse;
import vn.vtit.gemek.module.contractor.dto.UpdateContractRequest;
import vn.vtit.gemek.module.contractor.dto.UpdateContractorRequest;
import vn.vtit.gemek.module.contractor.entity.ContractorSpecialty;

import java.util.List;
import java.util.UUID;

/**
 * Service interface for contractor and contract management.
 *
 * <p>Implementations must enforce role-based access rules at the service layer
 * in addition to the {@code @PreAuthorize} annotations on the controller.
 */
public interface ContractorService {

    /**
     * Returns a paginated list of contractors with optional filters.
     *
     * @param search    optional case-insensitive substring match on company name or contact person.
     * @param specialty optional specialty filter.
     * @param active    optional active-flag filter; {@code null} returns all.
     * @param pageable  pagination and sorting parameters.
     * @return paginated contractor response list.
     */
    PageResponse<ContractorResponse> listContractors(
            String search, ContractorSpecialty specialty, Boolean active, Pageable pageable);

    /**
     * Creates a new contractor record.
     *
     * @param request     the create request body.
     * @param principalId the UUID of the creating user.
     * @return the created contractor response.
     */
    ContractorResponse createContractor(CreateContractorRequest request, UUID principalId);

    /**
     * Returns the full detail of a contractor by ID.
     *
     * @param id the contractor UUID.
     * @return the contractor response.
     * @throws vn.vtit.gemek.common.exception.AppException with {@code NOT_FOUND} if absent.
     */
    ContractorResponse getContractor(UUID id);

    /**
     * Updates mutable fields on an existing contractor.
     *
     * @param id          the contractor UUID.
     * @param request     the update request body.
     * @param principalId the UUID of the updating user.
     * @return the updated contractor response.
     */
    ContractorResponse updateContractor(UUID id, UpdateContractorRequest request, UUID principalId);

    /**
     * Soft-deletes a contractor by setting its {@code active} flag to {@code false}.
     *
     * @param id the contractor UUID.
     */
    void deactivateContractor(UUID id);

    /**
     * Returns all contracts for the given contractor.
     *
     * @param contractorId the contractor UUID.
     * @param pageable     pagination parameters.
     * @return paginated contract response list.
     */
    PageResponse<ContractResponse> listContracts(UUID contractorId, Pageable pageable);

    /**
     * Creates a new contract under the given contractor.
     *
     * @param contractorId the contractor UUID.
     * @param request      the create request body.
     * @param principalId  the UUID of the creating user.
     * @return the created contract response.
     */
    ContractResponse createContract(UUID contractorId, CreateContractRequest request, UUID principalId);

    /**
     * Returns the full detail of a contract by ID.
     *
     * @param id the contract UUID.
     * @return the contract response.
     * @throws vn.vtit.gemek.common.exception.AppException with {@code NOT_FOUND} if absent.
     */
    ContractResponse getContract(UUID id);

    /**
     * Updates mutable fields on an existing contract.
     *
     * @param id      the contract UUID.
     * @param request the update request body.
     * @return the updated contract response.
     */
    ContractResponse updateContract(UUID id, UpdateContractRequest request);

    /**
     * Records a payment against a contract.
     *
     * @param contractId  the contract UUID.
     * @param request     the payment request body.
     * @param principalId the UUID of the recording user.
     * @return the created payment response.
     */
    ContractPaymentResponse addPayment(UUID contractId, CreateContractPaymentRequest request, UUID principalId);

    /**
     * Returns all payments recorded against a contract, most recent first.
     *
     * @param contractId the contract UUID.
     * @return list of payment responses.
     */
    List<ContractPaymentResponse> listPayments(UUID contractId);

    /**
     * Adds a maintenance schedule to a contract.
     *
     * @param contractId the contract UUID.
     * @param request    the schedule request body.
     * @return the created schedule response.
     */
    MaintenanceScheduleResponse addSchedule(UUID contractId, CreateMaintenanceScheduleRequest request);

    /**
     * Returns all maintenance schedules for the given contract.
     *
     * @param contractId the contract UUID.
     * @return list of schedule responses.
     */
    List<MaintenanceScheduleResponse> listSchedules(UUID contractId);
}
