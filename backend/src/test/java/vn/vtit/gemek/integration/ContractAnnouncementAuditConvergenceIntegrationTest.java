/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.integration;

import vn.vtit.gemek.support.AbstractIntegrationTest;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import vn.vtit.gemek.common.security.UserPrincipal;
import vn.vtit.gemek.common.storage.FileStorageService;
import vn.vtit.gemek.module.announcement.AnnouncementService;
import vn.vtit.gemek.module.announcement.dto.AnnouncementResponse;
import vn.vtit.gemek.module.announcement.dto.CreateAnnouncementRequest;
import vn.vtit.gemek.module.announcement.entity.Announcement;
import vn.vtit.gemek.module.announcement.entity.AnnouncementScope;
import vn.vtit.gemek.module.announcement.entity.AnnouncementType;
import vn.vtit.gemek.module.announcement.repository.AnnouncementRepository;
import vn.vtit.gemek.module.contractor.ContractorService;
import vn.vtit.gemek.module.contractor.dto.ContractResponse;
import vn.vtit.gemek.module.contractor.dto.CreateContractRequest;
import vn.vtit.gemek.module.contractor.dto.UpdateContractRequest;
import vn.vtit.gemek.module.contractor.entity.Contract;
import vn.vtit.gemek.module.contractor.entity.ContractStatus;
import vn.vtit.gemek.module.contractor.entity.Contractor;
import vn.vtit.gemek.module.contractor.entity.ContractorSpecialty;
import vn.vtit.gemek.module.contractor.repository.ContractRepository;
import vn.vtit.gemek.module.contractor.repository.ContractorRepository;
import vn.vtit.gemek.module.user.entity.User;
import vn.vtit.gemek.module.user.entity.UserRole;
import vn.vtit.gemek.module.user.repository.UserRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies AUD.2 convergence: Contract / Announcement creator attribution is captured by
 * Spring Data auditing (not a manual {@code setCreatedBy}), {@code updated_by} is stamped on
 * update, and the response mappers resolve the creator's display name from the actor UUID.
 *
 * <p>Runs against the shared dev DB; every assertion targets only rows this test created and
 * the test is {@code @Transactional} (rolled back per method).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Transactional
class ContractAnnouncementAuditConvergenceIntegrationTest extends AbstractIntegrationTest {

    /** Mock MinIO — not started in this test suite. */
    @MockBean
    private FileStorageService fileStorageService;

    @Autowired
    private ContractorService contractorService;

    @Autowired
    private AnnouncementService announcementService;

    @Autowired
    private ContractorRepository contractorRepository;

    @Autowired
    private ContractRepository contractRepository;

    @Autowired
    private AnnouncementRepository announcementRepository;

    @Autowired
    private UserRepository userRepository;

    @PersistenceContext
    private EntityManager entityManager;

    /** Unique-phone source to avoid collisions on the shared dev DB. */
    private static final AtomicLong PHONE_SEQ = new AtomicLong(System.nanoTime());

    private User actorOne;
    private User actorTwo;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        actorOne = userRepository.saveAndFlush(newUser("AUD2 Actor One"));
        actorTwo = userRepository.saveAndFlush(newUser("AUD2 Actor Two"));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Contract: create stamps created_by_user_id via auditing; response resolves creator name from UUID")
    void contractCreate_auditingStampsCreator_andResponseResolvesName() {
        authenticateAs(actorOne);
        Contractor contractor = newContractor();

        CreateContractRequest request = new CreateContractRequest(
                contractor.getId(), "AUD2 Contract", "Scope",
                new BigDecimal("1000000"), "VND",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), null);

        ContractResponse response = contractorService.createContract(
                contractor.getId(), request, actorOne.getId());

        // Response exposes the creator resolved from the actor UUID (not the old User association).
        assertThat(response.createdBy()).isNotNull();
        assertThat(response.createdBy().id()).isEqualTo(actorOne.getId());
        assertThat(response.createdBy().fullName()).isEqualTo(actorOne.getFullName());

        // The persisted row's actor column was set by auditing — and is not null (manual call gone).
        entityManager.flush();
        entityManager.clear();
        Contract reloaded = contractRepository.findById(response.id()).orElseThrow();
        assertThat(reloaded.getCreatedBy()).isEqualTo(actorOne.getId());
        assertThat(reloaded.getUpdatedBy()).isEqualTo(actorOne.getId());
    }

    @Test
    @DisplayName("Contract: update under a different actor stamps updated_by; created_by stays the creator")
    void contractUpdate_stampsUpdatedBy() {
        authenticateAs(actorOne);
        Contractor contractor = newContractor();
        CreateContractRequest request = new CreateContractRequest(
                contractor.getId(), "AUD2 Contract", "Scope",
                new BigDecimal("1000000"), "VND",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), null);
        ContractResponse created = contractorService.createContract(
                contractor.getId(), request, actorOne.getId());

        entityManager.flush();
        entityManager.clear();

        authenticateAs(actorTwo);
        contractorService.updateContract(created.id(),
                new UpdateContractRequest("Renamed", null, null, null, null, null));

        entityManager.flush();
        entityManager.clear();
        Contract reloaded = contractRepository.findById(created.id()).orElseThrow();
        assertThat(reloaded.getCreatedBy()).as("creator immutable").isEqualTo(actorOne.getId());
        assertThat(reloaded.getUpdatedBy()).as("modifier tracked").isEqualTo(actorTwo.getId());
    }

    @Test
    @DisplayName("Announcement: create stamps created_by_user_id via auditing; response resolves creator name from UUID")
    void announcementCreate_auditingStampsCreator_andResponseResolvesName() {
        authenticateAs(actorOne);

        CreateAnnouncementRequest req = new CreateAnnouncementRequest();
        req.setTitle("AUD2 Announcement");
        req.setContent("Body");
        req.setType(AnnouncementType.GENERAL);
        req.setTargetScope(AnnouncementScope.ALL);

        AnnouncementResponse response = announcementService.createAnnouncement(req, actorOne.getId());

        assertThat(response.getCreatedBy()).isNotNull();
        assertThat(response.getCreatedBy().getId()).isEqualTo(actorOne.getId());
        assertThat(response.getCreatedBy().getFullName()).isEqualTo(actorOne.getFullName());

        entityManager.flush();
        entityManager.clear();
        Announcement reloaded = announcementRepository.findById(response.getId()).orElseThrow();
        assertThat(reloaded.getCreatedBy()).isEqualTo(actorOne.getId());
        assertThat(reloaded.getUpdatedBy()).isEqualTo(actorOne.getId());
    }

    /**
     * Persists a minimal contractor under the current actor.
     *
     * @return the saved contractor.
     */
    private Contractor newContractor() {
        Contractor contractor = new Contractor();
        contractor.setCompanyName("AUD2 Contractor " + PHONE_SEQ.incrementAndGet());
        contractor.setSpecialty(ContractorSpecialty.ELECTRICAL);
        contractor.setActive(true);
        return contractorRepository.saveAndFlush(contractor);
    }

    /**
     * Builds a minimally-valid {@link User} with a unique phone and no email.
     *
     * @param fullName the display name.
     * @return an unsaved user.
     */
    private User newUser(String fullName) {
        User user = new User();
        user.setFullName(fullName);
        user.setPhone(String.format("%013d", PHONE_SEQ.incrementAndGet() % 1_000_000_000_000L));
        user.setPasswordHash("$2a$10$testtesttesttesttesttesttesttesttesttesttesttesttest");
        user.setRole(UserRole.ADMIN);
        user.setActive(true);
        return user;
    }

    /**
     * Sets the SecurityContext to an authenticated {@link UserPrincipal} for the given user.
     *
     * @param user the user to authenticate as.
     */
    private void authenticateAs(User user) {
        UserPrincipal principal = new UserPrincipal(user);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }
}
