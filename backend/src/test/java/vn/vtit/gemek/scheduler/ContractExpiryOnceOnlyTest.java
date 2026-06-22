/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.scheduler;
import vn.vtit.gemek.support.AbstractIntegrationTest;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import vn.vtit.gemek.common.storage.FileStorageService;
import vn.vtit.gemek.module.contractor.entity.Contract;
import vn.vtit.gemek.module.contractor.entity.ContractStatus;
import vn.vtit.gemek.module.contractor.entity.Contractor;
import vn.vtit.gemek.module.contractor.repository.ContractRepository;
import vn.vtit.gemek.module.contractor.repository.ContractorRepository;
import vn.vtit.gemek.module.notification.entity.Notification;
import vn.vtit.gemek.module.user.entity.User;
import vn.vtit.gemek.module.user.entity.UserRole;
import vn.vtit.gemek.module.user.repository.UserRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * N3 P6 — G6 bug-fix proof: an expiring contract notifies its staff user ONCE,
 * not on every daily run.
 *
 * <p>Invokes {@link ContractExpiryScheduler#checkExpiringContracts()} directly
 * against the real DB (the pre-fix bug lived in the repository scan — the existing
 * Mockito unit test cannot observe it). Class-level {@code @Transactional} rolls
 * all fixtures back; assertions are scoped to the fixture contract's
 * {@code referenceId} because the shared dev DB contains foreign rows.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ContractExpiryOnceOnlyTest extends AbstractIntegrationTest {

    /** Mock MinIO — not started in this test suite. */
    @MockBean
    private FileStorageService fileStorageService;

    @Autowired
    private ContractExpiryScheduler scheduler;

    @Autowired
    private ContractorRepository contractorRepository;

    @Autowired
    private ContractRepository contractRepository;

    @Autowired
    private UserRepository userRepository;

    @PersistenceContext
    private EntityManager entityManager;

    private User staff;
    private Contract contract;

    @BeforeEach
    void setUp() {
        long tag = System.nanoTime();

        staff = new User();
        // Phone prefix "02" — shared with TicketSlaSchedulerTest, unused elsewhere.
        staff.setPhone("02" + String.format("%08d", Math.abs(tag) % 100_000_000L));
        staff.setFullName("P6 contract staff " + tag);
        staff.setPasswordHash("test-hash-not-a-credential");
        staff.setRole(UserRole.ADMIN);
        staff.setActive(true);
        staff = userRepository.save(staff);

        Contractor contractor = new Contractor();
        contractor.setCompanyName("P6 Contractor " + tag);
        contractor = contractorRepository.save(contractor);

        contract = new Contract();
        contract.setContractor(contractor);
        contract.setTitle("P6 expiring contract " + tag);
        contract.setStartDate(LocalDate.now().minusMonths(6));
        contract.setEndDate(LocalDate.now().plusDays(15));
        contract.setStatus(ContractStatus.ACTIVE);
        contract.setCreatedBy(staff);
        contract = contractRepository.save(contract);
    }

    @Test
    @DisplayName("G6 — expiring contract notifies once; immediate second run adds zero rows")
    void expiringContract_notifiesOnce_secondRunZero() {
        scheduler.checkExpiringContracts();

        List<Notification> rows = rowsForContract(contract.getId());
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getUser().getId()).isEqualTo(staff.getId());
        assertThat(contract.getExpiryNotifiedAt()).isNotNull();

        // Pre-fix behavior: every run re-notified. The sent-marker must stop run 2.
        scheduler.checkExpiringContracts();
        assertThat(rowsForContract(contract.getId())).hasSize(1);
    }

    /**
     * Lists all notification rows referencing the contract.
     *
     * @param contractId the contract UUID.
     * @return matching rows.
     */
    private List<Notification> rowsForContract(UUID contractId) {
        return entityManager.createQuery(
                        "SELECT n FROM Notification n WHERE n.referenceId = :id"
                                + " AND n.referenceType = 'Contract'",
                        Notification.class)
                .setParameter("id", contractId)
                .getResultList();
    }
}
