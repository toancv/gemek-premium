/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.contractor;

import org.apache.tika.Tika;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import vn.vtit.gemek.common.exception.AppException;
import vn.vtit.gemek.common.exception.ErrorCode;
import vn.vtit.gemek.common.model.PageResponse;
import vn.vtit.gemek.common.storage.ContentDispositionUtil;
import vn.vtit.gemek.common.storage.FileStorageService;
import vn.vtit.gemek.common.storage.ObjectKeysObsoleteEvent;
import vn.vtit.gemek.module.contractor.dto.ContractPaymentResponse;
import vn.vtit.gemek.module.contractor.dto.ContractResponse;
import vn.vtit.gemek.module.contractor.dto.ContractorDocumentResponse;
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
import vn.vtit.gemek.module.contractor.entity.ContractorDocument;
import vn.vtit.gemek.module.contractor.entity.ContractorSpecialty;
import vn.vtit.gemek.module.contractor.entity.MaintenanceSchedule;
import vn.vtit.gemek.module.contractor.mapper.ContractorMapper;
import vn.vtit.gemek.module.contractor.repository.ContractPaymentRepository;
import vn.vtit.gemek.module.contractor.repository.ContractRepository;
import vn.vtit.gemek.module.contractor.repository.ContractorDocumentRepository;
import vn.vtit.gemek.module.contractor.repository.ContractorRepository;
import vn.vtit.gemek.module.contractor.repository.MaintenanceScheduleRepository;
import vn.vtit.gemek.module.user.entity.User;
import vn.vtit.gemek.module.user.repository.UserRepository;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

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

    /** Object-key prefix for contractor documents: {@code contractors/{contractorId}/documents/{uuid}}. */
    static final String DOCUMENT_KEY_PREFIX = "contractors/";

    /** Object-key segment that follows the contractor id (so the gate can validate the key shape). */
    private static final String DOCUMENT_KEY_SEGMENT = "documents/";

    /** Maximum document rows per contractor (mirrors the C3 attachment cap). */
    private static final int MAX_DOCUMENTS_PER_CONTRACTOR = 5;

    /** Maximum total document bytes per contractor (50 MB, mirrors the C3 attachment cap). */
    private static final long MAX_DOCUMENT_TOTAL_BYTES = 50L * 1024 * 1024;

    /** Maximum bytes for a single document (10 MB, mirrors the C3 attachment per-file cap). */
    private static final long MAX_DOCUMENT_FILE_BYTES = 10L * 1024 * 1024;

    /**
     * Allowed document content types, validated by Tika on the BYTES — pdf/docx/xlsx/pptx/txt ONLY.
     * These VALUES intentionally duplicate the announcement attachment set (C3); extracting a shared
     * constant is deferred debt (see reports/contractor-documents-p1.md), mirroring prior C3 DRY debt.
     */
    private static final String MIME_PDF = "application/pdf";
    private static final String MIME_DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    private static final String MIME_XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final String MIME_PPTX = "application/vnd.openxmlformats-officedocument.presentationml.presentation";
    private static final String MIME_TXT = "text/plain";

    private static final Set<String> ALLOWED_DOCUMENT_MIME_TYPES =
            Set.of(MIME_PDF, MIME_DOCX, MIME_XLSX, MIME_PPTX, MIME_TXT);

    /** Forced-download content type override applied to every document presigned URL. */
    private static final String DOWNLOAD_RESPONSE_CONTENT_TYPE = "application/octet-stream";

    /** Tika instance for magic-byte content-type detection (thread-safe). */
    private static final Tika TIKA = new Tika();

    private final ContractorRepository contractorRepository;
    private final ContractRepository contractRepository;
    private final ContractPaymentRepository paymentRepository;
    private final MaintenanceScheduleRepository scheduleRepository;
    private final UserRepository userRepository;
    private final ContractorMapper contractorMapper;
    private final ContractorDocumentRepository documentRepository;
    private final FileStorageService fileStorageService;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Constructs the service with all required dependencies via constructor injection.
     *
     * @param contractorRepository the contractor JPA repository.
     * @param contractRepository   the contract JPA repository.
     * @param paymentRepository    the contract payment JPA repository.
     * @param scheduleRepository   the maintenance schedule JPA repository.
     * @param userRepository       the user JPA repository.
     * @param contractorMapper     the MapStruct contractor mapper.
     * @param documentRepository   the contractor document JPA repository.
     * @param fileStorageService   the generic MinIO-backed storage service (reused as-is).
     * @param eventPublisher       publisher for after-commit MinIO object cleanup events.
     */
    public ContractorServiceImpl(ContractorRepository contractorRepository,
                                 ContractRepository contractRepository,
                                 ContractPaymentRepository paymentRepository,
                                 MaintenanceScheduleRepository scheduleRepository,
                                 UserRepository userRepository,
                                 ContractorMapper contractorMapper,
                                 ContractorDocumentRepository documentRepository,
                                 FileStorageService fileStorageService,
                                 ApplicationEventPublisher eventPublisher) {
        this.contractorRepository = contractorRepository;
        this.contractRepository = contractRepository;
        this.paymentRepository = paymentRepository;
        this.scheduleRepository = scheduleRepository;
        this.userRepository = userRepository;
        this.contractorMapper = contractorMapper;
        this.documentRepository = documentRepository;
        this.fileStorageService = fileStorageService;
        this.eventPublisher = eventPublisher;
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
            // Match on company name OR contact person OR phone (case-insensitive).
            // phone is a nullable column; LIKE over NULL yields NULL (no match), never an error — pattern is non-null.
            spec = spec.and((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("companyName")), pattern),
                    cb.like(cb.lower(root.get("contactPerson")), pattern),
                    cb.like(cb.lower(root.get("phone")), pattern)
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

        // Resolve all creator names for the page in ONE query, then map from the map (no N+1).
        Map<UUID, String> names = creatorNames(pageContent);
        Page<ContractResponse> page = new PageImpl<>(
                pageContent.stream().map(c -> contractorMapper.toContractResponse(c, names)).toList(),
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
        // createdBy is set by Spring Data auditing from the authenticated actor — no manual write.

        Contract saved = contractRepository.save(contract);
        log.info("Contract created — id={}, contractorId={}", saved.getId(), contractorId);
        return contractorMapper.toContractResponse(saved, creatorNames(List.of(saved)));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ContractResponse getContract(UUID id) {
        log.debug("Getting contract — id={}", id);
        Contract contract = loadContractOrThrow(id);
        return contractorMapper.toContractResponse(contract, creatorNames(List.of(contract)));
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
        return contractorMapper.toContractResponse(saved, creatorNames(List.of(saved)));
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
    // =========================================================================
    // Contractor documents (staff-only forced-download; DECISIONS 2026-06-28)
    // =========================================================================

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public ContractorDocumentResponse uploadDocument(UUID contractorId, MultipartFile file, UUID principalId) {
        log.debug("uploadDocument — contractorId={}", contractorId);

        Contractor contractor = loadContractorOrThrow(contractorId);

        long fileSize = file.getSize();
        // Per-file cap FIRST (cheap) — coded 413 rather than relying on the servlet limit alone.
        if (fileSize > MAX_DOCUMENT_FILE_BYTES) {
            throw new AppException(ErrorCode.CONTRACTOR_DOCUMENT_TOO_LARGE,
                    "Tệp tài liệu vượt quá 10MB.");
        }

        // Magic-byte validation: trust the bytes (pdf/docx/xlsx/pptx/txt), never the filename/header.
        String detectedMime = detectDocumentMime(file);

        long currentCount = documentRepository.countByContractorId(contractorId);
        long currentBytes = documentRepository.sumSizeBytesByContractorId(contractorId);
        if (currentCount + 1 > MAX_DOCUMENTS_PER_CONTRACTOR) {
            throw new AppException(ErrorCode.CONTRACTOR_DOCUMENT_LIMIT_EXCEEDED,
                    "Tối đa " + MAX_DOCUMENTS_PER_CONTRACTOR + " tài liệu mỗi nhà thầu.");
        }
        if (currentBytes + fileSize > MAX_DOCUMENT_TOTAL_BYTES) {
            throw new AppException(ErrorCode.CONTRACTOR_DOCUMENT_LIMIT_EXCEEDED,
                    "Tổng dung lượng tài liệu của nhà thầu vượt quá 50MB.");
        }

        // Key convention contractors/{contractorId}/documents/{uuid}{ext} — the contractor id is the
        // first segment after the prefix so the presign scope gate parses it from the key alone.
        String objectKey = DOCUMENT_KEY_PREFIX + contractorId + "/" + DOCUMENT_KEY_SEGMENT
                + UUID.randomUUID() + documentExtensionFor(detectedMime);

        try {
            fileStorageService.upload(objectKey, file.getInputStream(), detectedMime, fileSize);
        } catch (IOException e) {
            log.error("Failed to read contractor document upload stream: {}", e.getMessage(), e);
            throw new AppException(ErrorCode.INTERNAL_ERROR, "Không thể đọc tệp tải lên.");
        }

        ContractorDocument document = new ContractorDocument();
        document.setContractor(contractor);
        document.setObjectKey(objectKey);
        document.setContentType(detectedMime);
        document.setSizeBytes(fileSize);
        document.setDisplayFilename(sanitizeDisplayFilename(file.getOriginalFilename()));
        ContractorDocument saved = documentRepository.save(document);

        log.info("Contractor {} document uploaded — type={}, size={}B, key={}",
                contractorId, detectedMime, fileSize, objectKey);
        return toDocumentResponse(saved, null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<ContractorDocumentResponse> listDocuments(UUID contractorId, UUID principalId, String role) {
        log.debug("listDocuments — contractorId={}", contractorId);

        // Existence check so a missing contractor is a clean 404 rather than an empty list.
        loadContractorOrThrow(contractorId);
        List<ContractorDocument> rows = documentRepository.findByContractorIdOrderByCreatedAtAsc(contractorId);
        if (rows.isEmpty()) {
            return List.of();
        }
        // Gate once via the staff-only access rule; denial → empty list (no leak), not a 500/403 body.
        try {
            assertContractorDocumentPresignAccess(rows.get(0).getObjectKey(), principalId, role);
        } catch (AppException denied) {
            return List.of();
        }
        return rows.stream()
                .map(d -> toDocumentResponse(d, fileStorageService.presign(d.getObjectKey(),
                        ContentDispositionUtil.attachment(d.getDisplayFilename()),
                        DOWNLOAD_RESPONSE_CONTENT_TYPE)))
                .collect(Collectors.toList());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public void deleteDocument(UUID contractorId, UUID documentId) {
        log.debug("deleteDocument — contractorId={}, documentId={}", contractorId, documentId);

        // Existence check so a missing contractor is a clean 404.
        loadContractorOrThrow(contractorId);
        // Dual-key lookup: the row must belong to the contractor in the path (no cross-contractor delete).
        ContractorDocument document = documentRepository
                .findByContractorIdAndId(contractorId, documentId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND,
                        "Contractor document not found: " + documentId));

        String objectKey = document.getObjectKey();
        documentRepository.delete(document);
        scheduleObjectCleanup(List.of(objectKey));
        log.info("Contractor {} document deleted — documentId={}", contractorId, documentId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void assertContractorDocumentPresignAccess(String objectKey, UUID principalId, String role) {
        // Parse the owning contractor id FIRST — a malformed key is denied for every role
        // (FORBIDDEN, never a 500 from a bad UUID reaching MinIO).
        UUID contractorId = parseContractorIdFromKey(objectKey);
        if (contractorId == null) {
            log.warn("Denied contractor-document presign — malformed key.");
            throw new AppException(ErrorCode.FORBIDDEN, "Access denied to contractor document.");
        }

        // STAFF-ONLY: ADMIN and BOARD_MEMBER may read; every other role (incl. TECHNICIAN, RESIDENT)
        // is denied. This deliberately omits the announcement gate's resident-readable branch
        // (DECISIONS 2026-06-28: no resident surface for contractor documents).
        if ("ADMIN".equals(role) || "BOARD_MEMBER".equals(role)) {
            return;
        }
        log.warn("Denied contractor-document presign — role {} is not staff.", role);
        throw new AppException(ErrorCode.FORBIDDEN, "Access denied to contractor document.");
    }

    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Recovers the contractor UUID from a document object key per {@link #DOCUMENT_KEY_PREFIX}.
     *
     * <p>Convention: {@code contractors/{contractorId}/documents/{uuid-filename}} — the id is the first
     * path segment after the prefix, and the next segment must be {@code documents/}. Returns
     * {@code null} (caller denies) for any key that does not match this shape or whose id segment is not
     * a UUID, so a malformed key never produces a 500.
     *
     * @param objectKey the MinIO object key.
     * @return the parsed contractor UUID, or {@code null} if the key is malformed.
     */
    private static UUID parseContractorIdFromKey(String objectKey) {
        if (objectKey == null || !objectKey.startsWith(DOCUMENT_KEY_PREFIX)) {
            return null;
        }
        String remainder = objectKey.substring(DOCUMENT_KEY_PREFIX.length());
        int slash = remainder.indexOf('/');
        // Require a non-empty id segment.
        if (slash <= 0) {
            return null;
        }
        String afterId = remainder.substring(slash + 1);
        // Require the documents/ segment AND a non-empty filename after it ("documents/<file>").
        if (!afterId.startsWith(DOCUMENT_KEY_SEGMENT) || afterId.length() <= DOCUMENT_KEY_SEGMENT.length()) {
            return null;
        }
        try {
            return UUID.fromString(remainder.substring(0, slash));
        } catch (IllegalArgumentException ex) {
            // First segment is not a UUID — malformed key, deny.
            return null;
        }
    }

    /**
     * Detects the real document content type from the file bytes and asserts it is an allowed type
     * (pdf/docx/xlsx/pptx/txt). OOXML files are ZIP containers tika-core reports as
     * {@code application/zip}; they are disambiguated by their internal part layout
     * ({@link #classifyZipContainer}) — still pure content inspection, never the filename.
     *
     * @param file the uploaded multipart file.
     * @return the detected, allowed document MIME type.
     * @throws AppException CONTRACTOR_DOCUMENT_TYPE_NOT_ALLOWED if not allowed; INTERNAL_ERROR on read failure.
     */
    private String detectDocumentMime(MultipartFile file) {
        try {
            String detected = TIKA.detect(file.getInputStream());
            // tika-core can't peek inside a zip — resolve OOXML subtypes from the container layout.
            if ("application/zip".equals(detected) || "application/x-tika-ooxml".equals(detected)) {
                detected = classifyZipContainer(file);
            }
            if (!ALLOWED_DOCUMENT_MIME_TYPES.contains(detected)) {
                throw new AppException(ErrorCode.CONTRACTOR_DOCUMENT_TYPE_NOT_ALLOWED,
                        "Chỉ chấp nhận tệp PDF, DOCX, XLSX, PPTX hoặc TXT.");
            }
            return detected;
        } catch (IOException e) {
            throw new AppException(ErrorCode.INTERNAL_ERROR, "Không thể kiểm tra định dạng tệp.");
        }
    }

    /**
     * Classifies a ZIP container as an OOXML document type by inspecting its entry names. Real Office
     * files carry a {@code word/}, {@code xl/}, or {@code ppt/} part tree; the first match wins. A zip
     * lacking these returns {@code application/zip} so the caller rejects it.
     *
     * @param file the uploaded multipart file (a detected zip container).
     * @return the OOXML MIME type, or {@code application/zip} if it is not an Office document.
     * @throws IOException if the stream cannot be read.
     */
    private String classifyZipContainer(MultipartFile file) throws IOException {
        try (InputStream in = file.getInputStream();
             ZipInputStream zip = new ZipInputStream(in)) {
            ZipEntry entry;
            // Scan part names; OOXML always has a top-level word/ | xl/ | ppt/ directory.
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName();
                if (name.startsWith("word/")) {
                    return MIME_DOCX;
                }
                if (name.startsWith("xl/")) {
                    return MIME_XLSX;
                }
                if (name.startsWith("ppt/")) {
                    return MIME_PPTX;
                }
            }
        }
        // No OOXML part tree → a plain zip, which is not an allowed document type.
        return "application/zip";
    }

    /**
     * Maps an allowed document MIME type to its canonical file extension (object-key cosmetic only —
     * serving forces a download with the stored display filename regardless of this).
     *
     * @param mime the detected MIME type (already validated as allowed).
     * @return the extension including the dot, e.g. {@code ".pdf"}.
     */
    private String documentExtensionFor(String mime) {
        return switch (mime) {
            case MIME_PDF -> ".pdf";
            case MIME_DOCX -> ".docx";
            case MIME_XLSX -> ".xlsx";
            case MIME_PPTX -> ".pptx";
            case MIME_TXT -> ".txt";
            default -> "";
        };
    }

    /**
     * Sanitizes the client filename for STORAGE/display: drops path separators and control chars and
     * bounds the length to the column width. The full RFC 6266 header sanitization (against
     * header/query-param injection) happens at presign time via {@code ContentDispositionUtil}.
     *
     * @param original the client-supplied original filename, may be null.
     * @return a non-blank display filename safe to persist (≤255 chars).
     */
    private String sanitizeDisplayFilename(String original) {
        if (original == null || original.isBlank()) {
            return "tai-lieu";
        }
        StringBuilder sb = new StringBuilder(original.length());
        for (int i = 0; i < original.length(); i++) {
            char c = original.charAt(i);
            // Drop path separators and control chars; keep the rest (incl. Vietnamese) for display.
            if (c == '/' || c == '\\' || c < 0x20 || c == 0x7F) {
                continue;
            }
            sb.append(c);
        }
        String cleaned = sb.toString().trim();
        if (cleaned.isEmpty()) {
            return "tai-lieu";
        }
        return cleaned.length() > 255 ? cleaned.substring(0, 255) : cleaned;
    }

    /**
     * Publishes an after-commit MinIO object-cleanup event for the given keys (no-op if empty).
     *
     * @param objectKeys the object keys to delete after the current transaction commits.
     */
    private void scheduleObjectCleanup(List<String> objectKeys) {
        if (objectKeys == null || objectKeys.isEmpty()) {
            return;
        }
        eventPublisher.publishEvent(new ObjectKeysObsoleteEvent(objectKeys));
    }

    /**
     * Maps a document entity to its response DTO.
     *
     * @param document    the document entity.
     * @param downloadUrl the forced-download presigned URL, or {@code null} on the upload response.
     * @return the response DTO.
     */
    private ContractorDocumentResponse toDocumentResponse(ContractorDocument document, String downloadUrl) {
        return ContractorDocumentResponse.builder()
                .id(document.getId())
                .displayFilename(document.getDisplayFilename())
                .contentType(document.getContentType())
                .sizeBytes(document.getSizeBytes())
                .createdAt(document.getCreatedAt())
                .downloadUrl(downloadUrl)
                .build();
    }

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

    /**
     * Resolves creator display names for a set of contracts in a single batch query.
     *
     * <p>Collects the distinct non-null {@code createdBy} actor UUIDs and issues ONE
     * {@code findAllById} — never a per-row lookup — so list mapping stays free of N+1.
     *
     * @param contracts the contracts whose creator names are needed.
     * @return id&rarr;fullName map; empty when no contract carries a creator UUID.
     */
    private Map<UUID, String> creatorNames(Collection<Contract> contracts) {
        Set<UUID> ids = contracts.stream()
                .map(Contract::getCreatedBy)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        // Guard the IN clause — and skip the round-trip entirely when there is nothing to resolve.
        if (ids.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(User::getId, User::getFullName));
    }
}
