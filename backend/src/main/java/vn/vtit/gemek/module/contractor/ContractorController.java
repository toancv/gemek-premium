/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.contractor;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.vtit.gemek.common.model.PageResponse;
import vn.vtit.gemek.common.security.UserPrincipal;
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
 * REST controller for contractor and contract management endpoints.
 *
 * <p>Contractor CRUD is restricted to ADMIN for writes; ADMIN, TECHNICIAN, and
 * BOARD_MEMBER can read. Contract and payment operations are ADMIN-only for writes;
 * BOARD_MEMBER and TECHNICIAN can read specific sub-resources.
 */
@RestController
@Tag(name = "Contractors", description = "Contractor and contract management")
public class ContractorController {

    private final ContractorService contractorService;

    /**
     * Constructs the controller with its service dependency.
     *
     * @param contractorService the contractor service.
     */
    public ContractorController(ContractorService contractorService) {
        this.contractorService = contractorService;
    }

    // =========================================================================
    // Contractor endpoints — /api/contractors
    // =========================================================================

    /**
     * Returns a paginated list of contractors with optional filters.
     *
     * @param search    optional substring match on company name or contact person.
     * @param specialty optional specialty filter.
     * @param active    optional active-flag filter.
     * @param page      0-based page index (default 0).
     * @param size      page size (default 20, max 100).
     * @return paginated contractor list.
     */
    @GetMapping("/api/contractors")
    @PreAuthorize("hasAnyRole('ADMIN','TECHNICIAN','BOARD_MEMBER')")
    @Operation(summary = "List contractors with filters")
    public ResponseEntity<PageResponse<ContractorResponse>> listContractors(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) ContractorSpecialty specialty,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        int cappedSize = Math.min(size, 100);
        Pageable pageable = PageRequest.of(page, cappedSize,
                Sort.by(Sort.Order.asc("companyName"), Sort.Order.asc("id")));
        return ResponseEntity.ok(contractorService.listContractors(search, specialty, active, pageable));
    }

    /**
     * Creates a new contractor.
     *
     * @param request   the create request body.
     * @param principal the authenticated user principal.
     * @return 201 Created with the new contractor response.
     */
    @PostMapping("/api/contractors")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create a contractor")
    public ResponseEntity<ContractorResponse> createContractor(
            @Valid @RequestBody CreateContractorRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(contractorService.createContractor(request, principal.getId()));
    }

    /**
     * Returns full detail of a contractor.
     *
     * @param id the contractor UUID.
     * @return 200 OK with the contractor response.
     */
    @GetMapping("/api/contractors/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','TECHNICIAN','BOARD_MEMBER')")
    @Operation(summary = "Get contractor detail")
    public ResponseEntity<ContractorResponse> getContractor(@PathVariable UUID id) {
        return ResponseEntity.ok(contractorService.getContractor(id));
    }

    /**
     * Updates an existing contractor.
     *
     * @param id        the contractor UUID.
     * @param request   the update request body.
     * @param principal the authenticated user principal.
     * @return 200 OK with the updated contractor response.
     */
    @PutMapping("/api/contractors/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update a contractor")
    public ResponseEntity<ContractorResponse> updateContractor(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateContractorRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(contractorService.updateContractor(id, request, principal.getId()));
    }

    /**
     * Soft-deletes a contractor by setting its active flag to false.
     *
     * @param id the contractor UUID.
     * @return 204 No Content.
     */
    @DeleteMapping("/api/contractors/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Deactivate (soft-delete) a contractor")
    public ResponseEntity<Void> deactivateContractor(@PathVariable UUID id) {
        contractorService.deactivateContractor(id);
        return ResponseEntity.noContent().build();
    }

    // =========================================================================
    // Contract endpoints under contractor — /api/contractors/{id}/contracts
    // =========================================================================

    /**
     * Returns all contracts for the given contractor.
     *
     * @param id   the contractor UUID.
     * @param page 0-based page index (default 0).
     * @param size page size (default 20, max 100).
     * @return paginated contract list.
     */
    @GetMapping("/api/contractors/{id}/contracts")
    @PreAuthorize("hasAnyRole('ADMIN','BOARD_MEMBER')")
    @Operation(summary = "List contracts for a contractor")
    public ResponseEntity<PageResponse<ContractResponse>> listContracts(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        int cappedSize = Math.min(size, 100);
        Pageable pageable = PageRequest.of(page, cappedSize,
                Sort.by(Sort.Order.desc("startDate"), Sort.Order.asc("id")));
        return ResponseEntity.ok(contractorService.listContracts(id, pageable));
    }

    /**
     * Creates a new contract under the given contractor.
     *
     * @param id        the contractor UUID.
     * @param request   the create request body.
     * @param principal the authenticated user principal.
     * @return 201 Created with the new contract response.
     */
    @PostMapping("/api/contractors/{id}/contracts")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create a contract for a contractor")
    public ResponseEntity<ContractResponse> createContract(
            @PathVariable UUID id,
            @Valid @RequestBody CreateContractRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(contractorService.createContract(id, request, principal.getId()));
    }

    // =========================================================================
    // Contract endpoints — /api/contracts/{id}
    // =========================================================================

    /**
     * Returns full detail of a contract.
     *
     * @param id the contract UUID.
     * @return 200 OK with the contract response.
     */
    @GetMapping("/api/contracts/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','BOARD_MEMBER','TECHNICIAN')")
    @Operation(summary = "Get contract detail")
    public ResponseEntity<ContractResponse> getContract(@PathVariable UUID id) {
        return ResponseEntity.ok(contractorService.getContract(id));
    }

    /**
     * Updates an existing contract.
     *
     * @param id      the contract UUID.
     * @param request the update request body.
     * @return 200 OK with the updated contract response.
     */
    @PutMapping("/api/contracts/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update a contract")
    public ResponseEntity<ContractResponse> updateContract(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateContractRequest request) {
        return ResponseEntity.ok(contractorService.updateContract(id, request));
    }

    // =========================================================================
    // Payment endpoints — /api/contracts/{id}/payments
    // =========================================================================

    /**
     * Returns all payments recorded against a contract.
     *
     * @param id the contract UUID.
     * @return 200 OK with the list of payment responses.
     */
    @GetMapping("/api/contracts/{id}/payments")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "List payments for a contract")
    public ResponseEntity<List<ContractPaymentResponse>> listPayments(@PathVariable UUID id) {
        return ResponseEntity.ok(contractorService.listPayments(id));
    }

    /**
     * Records a new payment against a contract.
     *
     * @param id        the contract UUID.
     * @param request   the payment request body.
     * @param principal the authenticated user principal.
     * @return 201 Created with the new payment response.
     */
    @PostMapping("/api/contracts/{id}/payments")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Record a payment for a contract")
    public ResponseEntity<ContractPaymentResponse> addPayment(
            @PathVariable UUID id,
            @Valid @RequestBody CreateContractPaymentRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(contractorService.addPayment(id, request, principal.getId()));
    }

    // =========================================================================
    // Schedule endpoints — /api/contracts/{id}/schedules
    // =========================================================================

    /**
     * Returns all maintenance schedules for a contract.
     *
     * @param id the contract UUID.
     * @return 200 OK with the list of schedule responses.
     */
    @GetMapping("/api/contracts/{id}/schedules")
    @PreAuthorize("hasAnyRole('ADMIN','TECHNICIAN')")
    @Operation(summary = "List maintenance schedules for a contract")
    public ResponseEntity<List<MaintenanceScheduleResponse>> listSchedules(@PathVariable UUID id) {
        return ResponseEntity.ok(contractorService.listSchedules(id));
    }

    /**
     * Adds a maintenance schedule to a contract.
     *
     * @param id      the contract UUID.
     * @param request the schedule request body.
     * @return 201 Created with the new schedule response.
     */
    @PostMapping("/api/contracts/{id}/schedules")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Add a maintenance schedule to a contract")
    public ResponseEntity<MaintenanceScheduleResponse> addSchedule(
            @PathVariable UUID id,
            @Valid @RequestBody CreateMaintenanceScheduleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(contractorService.addSchedule(id, request));
    }
}
