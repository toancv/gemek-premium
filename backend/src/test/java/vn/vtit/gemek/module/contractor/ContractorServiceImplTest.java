/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.contractor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import vn.vtit.gemek.common.exception.AppException;
import vn.vtit.gemek.common.exception.ErrorCode;
import vn.vtit.gemek.module.contractor.dto.ContractResponse;
import vn.vtit.gemek.module.contractor.dto.CreateContractPaymentRequest;
import vn.vtit.gemek.module.contractor.dto.CreateContractRequest;
import vn.vtit.gemek.module.contractor.dto.UpdateContractRequest;
import vn.vtit.gemek.module.contractor.entity.Contract;
import vn.vtit.gemek.module.contractor.entity.ContractStatus;
import vn.vtit.gemek.module.contractor.entity.Contractor;
import vn.vtit.gemek.module.contractor.entity.ContractorSpecialty;
import vn.vtit.gemek.module.contractor.mapper.ContractorMapper;
import vn.vtit.gemek.module.contractor.repository.ContractPaymentRepository;
import vn.vtit.gemek.module.contractor.repository.ContractRepository;
import vn.vtit.gemek.module.contractor.repository.ContractorRepository;
import vn.vtit.gemek.module.contractor.repository.MaintenanceScheduleRepository;
import vn.vtit.gemek.module.user.repository.UserRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ContractorServiceImpl} — GAP-07 contract lifecycle gates.
 *
 * <p>Verifies PENDING→ACTIVE approval transition, ACTIVE→TERMINATED cancellation,
 * and NOT_FOUND guards for contractor and contract lookups.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ContractorServiceImplTest {

    @Mock private ContractorRepository contractorRepository;
    @Mock private ContractRepository contractRepository;
    @Mock private ContractPaymentRepository paymentRepository;
    @Mock private MaintenanceScheduleRepository scheduleRepository;
    @Mock private UserRepository userRepository;
    @Mock private ContractorMapper contractorMapper;

    private ContractorServiceImpl service;

    private UUID contractorId;
    private UUID contractId;
    private Contractor contractor;
    private Contract contract;

    @BeforeEach
    void setUp() {
        service = new ContractorServiceImpl(
                contractorRepository, contractRepository, paymentRepository,
                scheduleRepository, userRepository, contractorMapper);

        contractorId = UUID.randomUUID();
        contractId = UUID.randomUUID();

        contractor = new Contractor();
        contractor.setId(contractorId);
        contractor.setCompanyName("Gemek Electrical Ltd");
        contractor.setSpecialty(ContractorSpecialty.ELECTRICAL);
        contractor.setActive(true);

        contract = new Contract();
        contract.setId(contractId);
        contract.setContractor(contractor);
        contract.setTitle("Annual Electrical Service");
        contract.setStatus(ContractStatus.PENDING);
        contract.setStartDate(LocalDate.of(2026, 1, 1));
        contract.setEndDate(LocalDate.of(2026, 12, 31));
    }

    // =========================================================================
    // updateContract — status transition: PENDING → ACTIVE (approval gate)
    // =========================================================================

    @Test
    @DisplayName("updateContract — PENDING→ACTIVE transition updates and returns ACTIVE status")
    void updateContract_pendingToActive_statusChangedToActive() {
        when(contractRepository.findById(contractId)).thenReturn(Optional.of(contract));
        when(contractRepository.save(any(Contract.class))).thenAnswer(inv -> inv.getArgument(0));

        ContractResponse expectedResponse = new ContractResponse(
                contractId,
                new ContractResponse.ContractorRef(contractorId, "Gemek Electrical Ltd"),
                "Annual Electrical Service", null,
                null, "VND",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31),
                ContractStatus.ACTIVE, null, null, null, null, null);
        when(contractorMapper.toContractResponse(any(Contract.class), any())).thenReturn(expectedResponse);

        UpdateContractRequest request = new UpdateContractRequest(
                null, null, null, null, ContractStatus.ACTIVE, null);

        ContractResponse result = service.updateContract(contractId, request);

        assertThat(result.status()).isEqualTo(ContractStatus.ACTIVE);
        assertThat(contract.getStatus()).isEqualTo(ContractStatus.ACTIVE);
    }

    // =========================================================================
    // updateContract — status transition: ACTIVE → TERMINATED (cancellation)
    // =========================================================================

    @Test
    @DisplayName("updateContract — ACTIVE→TERMINATED transition updates and returns TERMINATED status")
    void updateContract_activeToTerminated_statusChangedToTerminated() {
        contract.setStatus(ContractStatus.ACTIVE);
        when(contractRepository.findById(contractId)).thenReturn(Optional.of(contract));
        when(contractRepository.save(any(Contract.class))).thenAnswer(inv -> inv.getArgument(0));

        ContractResponse expectedResponse = new ContractResponse(
                contractId,
                new ContractResponse.ContractorRef(contractorId, "Gemek Electrical Ltd"),
                "Annual Electrical Service", null,
                null, "VND",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31),
                ContractStatus.TERMINATED, null, null, null, null, null);
        when(contractorMapper.toContractResponse(any(Contract.class), any())).thenReturn(expectedResponse);

        UpdateContractRequest request = new UpdateContractRequest(
                null, null, null, null, ContractStatus.TERMINATED, null);

        ContractResponse result = service.updateContract(contractId, request);

        assertThat(result.status()).isEqualTo(ContractStatus.TERMINATED);
        assertThat(contract.getStatus()).isEqualTo(ContractStatus.TERMINATED);
    }

    // =========================================================================
    // updateContract — contract not found → NOT_FOUND
    // =========================================================================

    @Test
    @DisplayName("updateContract — unknown contract ID throws NOT_FOUND")
    void updateContract_contractNotFound_throwsNotFound() {
        UUID unknownId = UUID.randomUUID();
        when(contractRepository.findById(unknownId)).thenReturn(Optional.empty());

        UpdateContractRequest request = new UpdateContractRequest(
                null, null, null, null, ContractStatus.ACTIVE, null);

        assertThatThrownBy(() -> service.updateContract(unknownId, request))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.NOT_FOUND));
    }

    // =========================================================================
    // createContract — contractor not found → NOT_FOUND
    // =========================================================================

    @Test
    @DisplayName("createContract — unknown contractor ID throws NOT_FOUND")
    void createContract_contractorNotFound_throwsNotFound() {
        UUID unknownId = UUID.randomUUID();
        when(contractorRepository.findById(unknownId)).thenReturn(Optional.empty());

        CreateContractRequest request = new CreateContractRequest(
                unknownId, "Title", "Scope",
                new BigDecimal("1000000"), "VND",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), null);

        assertThatThrownBy(() -> service.createContract(unknownId, request, UUID.randomUUID()))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.NOT_FOUND));
    }

    // =========================================================================
    // listContracts — contractor not found → NOT_FOUND
    // =========================================================================

    @Test
    @DisplayName("listContracts — unknown contractor ID throws NOT_FOUND")
    void listContracts_contractorNotFound_throwsNotFound() {
        UUID unknownId = UUID.randomUUID();
        when(contractorRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.listContracts(unknownId,
                org.springframework.data.domain.Pageable.unpaged()))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.NOT_FOUND));
    }

    // =========================================================================
    // getContract — contract not found → NOT_FOUND
    // =========================================================================

    @Test
    @DisplayName("getContract — unknown contract ID throws NOT_FOUND")
    void getContract_contractNotFound_throwsNotFound() {
        UUID unknownId = UUID.randomUUID();
        when(contractRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getContract(unknownId))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.NOT_FOUND));
    }

    // =========================================================================
    // addPayment — contract not found → NOT_FOUND
    // =========================================================================

    @Test
    @DisplayName("addPayment — unknown contract ID throws NOT_FOUND")
    void addPayment_contractNotFound_throwsNotFound() {
        UUID unknownId = UUID.randomUUID();
        when(contractRepository.findById(unknownId)).thenReturn(Optional.empty());

        CreateContractPaymentRequest request = new CreateContractPaymentRequest(
                new BigDecimal("5000000"), LocalDate.of(2026, 3, 1),
                "First payment", "TXN-001");

        assertThatThrownBy(() -> service.addPayment(unknownId, request, UUID.randomUUID()))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.NOT_FOUND));
    }

    // =========================================================================
    // deactivateContractor — contractor not found → NOT_FOUND
    // =========================================================================

    @Test
    @DisplayName("deactivateContractor — unknown contractor ID throws NOT_FOUND")
    void deactivateContractor_contractorNotFound_throwsNotFound() {
        UUID unknownId = UUID.randomUUID();
        when(contractorRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deactivateContractor(unknownId))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.NOT_FOUND));
    }

    // =========================================================================
    // listContracts — N+1 guard: creator names resolved in ONE batch query
    // =========================================================================

    @Test
    @DisplayName("listContracts — resolves creator names via a single findAllById (no per-row findById)")
    void listContracts_resolvesCreatorNamesInBatch_noN1() {
        // Three contracts, each with a distinct creator UUID — a naive mapper would issue 3 lookups.
        Contract c1 = contractWithCreator(UUID.randomUUID());
        Contract c2 = contractWithCreator(UUID.randomUUID());
        Contract c3 = contractWithCreator(UUID.randomUUID());

        when(contractorRepository.findById(contractorId)).thenReturn(Optional.of(contractor));
        when(contractRepository.findByContractorId(contractorId))
                .thenReturn(java.util.List.of(c1, c2, c3));
        when(userRepository.findAllById(any())).thenReturn(java.util.List.of());
        when(contractorMapper.toContractResponse(any(Contract.class), any()))
                .thenReturn(stubResponse());

        service.listContracts(contractorId,
                org.springframework.data.domain.PageRequest.of(0, 10));

        // Batch resolution: exactly one user query for the whole page, never one per row.
        verify(userRepository, times(1)).findAllById(any());
        verify(userRepository, never()).findById(any());
    }

    /**
     * Builds a contract carrying the given creator actor UUID.
     *
     * @param creatorId the creator actor UUID.
     * @return a contract with id, contractor, and createdBy populated.
     */
    private Contract contractWithCreator(UUID creatorId) {
        Contract c = new Contract();
        c.setId(UUID.randomUUID());
        c.setContractor(contractor);
        c.setTitle("C");
        c.setStatus(ContractStatus.ACTIVE);
        c.setCreatedBy(creatorId);
        return c;
    }

    /**
     * Builds a throwaway {@link ContractResponse} for mapper stubbing.
     *
     * @return a minimal response.
     */
    private ContractResponse stubResponse() {
        return new ContractResponse(UUID.randomUUID(), null, "C", null, null, "VND",
                null, null, ContractStatus.ACTIVE, null, null, null, null, null);
    }
}
