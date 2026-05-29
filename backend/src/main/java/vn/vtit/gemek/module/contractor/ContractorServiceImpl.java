/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.contractor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.vtit.gemek.common.exception.AppException;
import vn.vtit.gemek.common.exception.ErrorCode;
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
import vn.vtit.gemek.module.contractor.entity.Contract;
import vn.vtit.gemek.module.contractor.entity.ContractPayment;
import vn.vtit.gemek.module.contractor.entity.ContractStatus;
import vn.vtit.gemek.module.contractor.entity.Contractor;
import vn.vtit.gemek.module.contractor.entity.ContractorSpecialty;
import vn.vtit.gemek.module.contractor.entity.MaintenanceSchedule;
import vn.vtit.gemek.module.contractor.mapper.ContractorMapper;
import vn.vtit.gemek.module.contractor.repository.ContractPaymentRepository;
import vn.vtit.gemek.module.contractor.repository.ContractRepository;
import vn.vtit.gemek.module.contractor.repository.ContractorRepository;
import vn.vtit.gemek.module.contractor.repository.MaintenanceScheduleRepository;
import vn.vtit.gemek.module.user.entity.User;
import vn.vtit.gemek.module.user.repository.UserRepository;

import java.util.List;
import java.util.UUID;

/**
 * Implementation of {@link ContractorService}.
 *
 * <p>All read methods are covered by the class-level {@code @Transactional(readOnly=true)}.
 * Write methods declare their own {@code @Transactional} to promote to a read-write transaction.
 */
@Service
@Transactional(readOnly = true)
public class ContractorServiceImpl implements ContractorService {

    private static final Logger log = LoggerFactory.getLogger(ContractorServiceImpl.class);

    private final ContractorRepository contractorRepository;
    private final ContractRepository contractRepository;
    private final ContractPaymentRepository paymentRepository;
    private final MaintenanceScheduleRepository scheduleRepository;
    private final UserRepository userRepository;
    private final ContractorMapper contractorMapper;

    /**
     * Constructs the service with all required dependencies via constructor injection.
     *
     * @param contractorRepository the contractor JPA repository.
     * @param contractRepository   the contract JPA repository.
     * @param paymentRepository    the contract payment JPA repository.
     * @param scheduleRepository   the maintenance schedule JPA repository.
     * @param userRepository       the user JPA repository.
     * @param contractorMapper     the MapStruct contractor mapper.
     */
    public ContractorServiceImpl(ContractorRepository contractorRepository,
                                 ContractRepository contractRepository,
                                 ContractPaymentRepository paymentRepository,
                                 MaintenanceScheduleRepository scheduleRepository,
                                 UserRepository userRepository,
                                 ContractorMapper contractorMapper) {
        this.contractorRepository = contractorRepository;
        this.contractRepository = contractRepository;
        this.paymentRepository = paymentRepository;
        this.scheduleRepository = scheduleRepository;
        this.userRepository = userRepository;
        this.contractorMapper = contractorMapper;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PageResponse<ContractorResponse> listContractors(
            String search, ContractorSpecialty specialty, Boolean active, Pageable pageable) {
        log.debug("Listing contractors — search={}, specialty={}, active={}", search, specialty, active);

        // Build a dynamic Specification from the optional filter parameters.
        Specification<Contractor> spec = Specification.where(null);

        if (search != null && !search.isBlank()) {
            String pattern = "%" + search.toLowerCase() + "%";
            // Match on company name OR contact person (case-insensitive).
            spec = spec.and((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("companyName")), pattern),
                    cb.like(cb.lower(root.get("contactPerson")), pattern)
            ));
        }
        if (specialty != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("specialty"), specialty));
        }
        if (active != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("active"), active));
        }

        Page<ContractorResponse> page = contractorRepository
                .findAll(spec, pageable)
                .map(contractorMapper::toContractorResponse);

        return PageResponse.of(page);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public ContractorResponse createContractor(CreateContractorRequest request, UUID principalId) {
        log.debug("Creating contractor — companyName={}, principalId={}", request.companyName(), principalId);

        Contractor contractor = new Contractor();
        contractor.setCompanyName(request.companyName());
        contractor.setContactPerson(request.contactPerson());
        contractor.setPhone(request.phone());
        contractor.setEmail(request.email());
        contractor.setAddress(request.address());
        contractor.setSpecialty(request.specialty());
        contractor.setTaxCode(request.taxCode());
        contractor.setNotes(request.notes());

        Contractor saved = contractorRepository.save(contractor);
        log.info("Contractor created — id={}, companyName={}", saved.getId(), saved.getCompanyName());
        return contractorMapper.toContractorResponse(saved);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ContractorResponse getContractor(UUID id) {
        log.debug("Getting contractor — id={}", id);
        Contractor contractor = loadContractorOrThrow(id);
        return contractorMapper.toContractorResponse(contractor);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public ContractorResponse updateContractor(UUID id, UpdateContractorRequest request, UUID principalId) {
        log.debug("Updating contractor — id={}, principalId={}", id, principalId);

        Contractor contractor = loadContractorOrThrow(id);

        // Apply only non-null fields from the request.
        if (request.companyName() != null) {
            contractor.setCompanyName(request.companyName());
        }
        if (request.contactPerson() != null) {
            contractor.setContactPerson(request.contactPerson());
        }
        if (request.phone() != null) {
            contractor.setPhone(request.phone());
        }
        if (request.email() != null) {
            contractor.setEmail(request.email());
        }
        if (request.address() != null) {
            contractor.setAddress(request.address());
        }
        if (request.specialty() != null) {
            contractor.setSpecialty(request.specialty());
        }
        if (request.taxCode() != null) {
            contractor.setTaxCode(request.taxCode());
        }
        if (request.notes() != null) {
            contractor.setNotes(request.notes());
        }

        Contractor saved = contractorRepository.save(contractor);
        log.info("Contractor updated — id={}", saved.getId());
        return contractorMapper.toContractorResponse(saved);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public void deactivateContractor(UUID id) {
        log.debug("Deactivating contractor — id={}", id);
        Contractor contractor = loadContractorOrThrow(id);
        contractor.setActive(false);
        contractorRepository.save(contractor);
        log.info("Contractor deactivated — id={}", id);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PageResponse<ContractResponse> listContracts(UUID contractorId, Pageable pageable) {
        log.debug("Listing contracts — contractorId={}", contractorId);

        // Verify the contractor exists before querying contracts.
        loadContractorOrThrow(contractorId);

        List<Contract> allContracts = contractRepository.findByContractorId(contractorId);

        // Apply manual pagination to the flat list.
        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), allContracts.size());
        List<Contract> pageContent = (start > allContracts.size())
                ? List.of()
                : allContracts.subList(start, end);

        Page<ContractResponse> page = new PageImpl<>(
                pageContent.stream().map(contractorMapper::toContractResponse).toList(),
                pageable,
                allContracts.size());

        return PageResponse.of(page);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public ContractResponse createContract(UUID contractorId, CreateContractRequest request, UUID principalId) {
        log.debug("Creating contract — contractorId={}, title={}", contractorId, request.title());

        Contractor contractor = loadContractorOrThrow(contractorId);
        User creator = userRepository.findById(principalId).orElse(null);

        Contract contract = new Contract();
        contract.setContractor(contractor);
        contract.setTitle(request.title());
        contract.setScope(request.scope());
        contract.setContractValue(request.contractValue());
        // Use the requested currency, or fall back to entity default "VND".
        if (request.currency() != null && !request.currency().isBlank()) {
            contract.setCurrency(request.currency());
        }
        contract.setStartDate(request.startDate());
        contract.setEndDate(request.endDate());
        contract.setNotes(request.notes());
        contract.setStatus(ContractStatus.PENDING);
        contract.setCreatedBy(creator);

        Contract saved = contractRepository.save(contract);
        log.info("Contract created — id={}, contractorId={}", saved.getId(), contractorId);
        return contractorMapper.toContractResponse(saved);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ContractResponse getContract(UUID id) {
        log.debug("Getting contract — id={}", id);
        Contract contract = loadContractOrThrow(id);
        return contractorMapper.toContractResponse(contract);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public ContractResponse updateContract(UUID id, UpdateContractRequest request) {
        log.debug("Updating contract — id={}", id);

        Contract contract = loadContractOrThrow(id);

        // Apply only non-null fields.
        if (request.title() != null) {
            contract.setTitle(request.title());
        }
        if (request.scope() != null) {
            contract.setScope(request.scope());
        }
        if (request.contractValue() != null) {
            contract.setContractValue(request.contractValue());
        }
        if (request.endDate() != null) {
            contract.setEndDate(request.endDate());
        }
        if (request.status() != null) {
            contract.setStatus(request.status());
        }
        if (request.notes() != null) {
            contract.setNotes(request.notes());
        }

        Contract saved = contractRepository.save(contract);
        log.info("Contract updated — id={}", saved.getId());
        return contractorMapper.toContractResponse(saved);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public ContractPaymentResponse addPayment(UUID contractId, CreateContractPaymentRequest request, UUID principalId) {
        log.debug("Adding payment — contractId={}, amount={}", contractId, request.amount());

        Contract contract = loadContractOrThrow(contractId);
        User recorder = userRepository.findById(principalId).orElse(null);

        ContractPayment payment = new ContractPayment();
        payment.setContract(contract);
        payment.setAmount(request.amount());
        payment.setPaymentDate(request.paymentDate());
        payment.setDescription(request.description());
        payment.setReferenceNumber(request.referenceNumber());
        payment.setRecordedBy(recorder);

        ContractPayment saved = paymentRepository.save(payment);
        log.info("Payment recorded — id={}, contractId={}, amount={}", saved.getId(), contractId, saved.getAmount());
        return contractorMapper.toPaymentResponse(saved);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<ContractPaymentResponse> listPayments(UUID contractId) {
        log.debug("Listing payments — contractId={}", contractId);
        // Verify the contract exists before querying payments.
        loadContractOrThrow(contractId);
        return paymentRepository.findByContractIdOrderByPaymentDateDesc(contractId)
                .stream()
                .map(contractorMapper::toPaymentResponse)
                .toList();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public MaintenanceScheduleResponse addSchedule(UUID contractId, CreateMaintenanceScheduleRequest request) {
        log.debug("Adding maintenance schedule — contractId={}, title={}", contractId, request.title());

        Contract contract = loadContractOrThrow(contractId);

        MaintenanceSchedule schedule = new MaintenanceSchedule();
        schedule.setContract(contract);
        schedule.setTitle(request.title());
        schedule.setFrequency(request.frequency());
        schedule.setNextDueDate(request.nextDueDate());
        schedule.setNotes(request.notes());
        schedule.setActive(true);

        MaintenanceSchedule saved = scheduleRepository.save(schedule);
        log.info("Maintenance schedule created — id={}, contractId={}", saved.getId(), contractId);
        return contractorMapper.toScheduleResponse(saved);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<MaintenanceScheduleResponse> listSchedules(UUID contractId) {
        log.debug("Listing maintenance schedules — contractId={}", contractId);
        // Verify the contract exists before querying schedules.
        loadContractOrThrow(contractId);
        return scheduleRepository.findByContractId(contractId)
                .stream()
                .map(contractorMapper::toScheduleResponse)
                .toList();
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Loads a contractor by ID or throws {@link AppException} with {@code NOT_FOUND}.
     *
     * @param id the contractor UUID.
     * @return the loaded {@link Contractor}.
     */
    private Contractor loadContractorOrThrow(UUID id) {
        return contractorRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Contractor not found: " + id));
    }

    /**
     * Loads a contract by ID or throws {@link AppException} with {@code NOT_FOUND}.
     *
     * @param id the contract UUID.
     * @return the loaded {@link Contract}.
     */
    private Contract loadContractOrThrow(UUID id) {
        return contractRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Contract not found: " + id));
    }
}
