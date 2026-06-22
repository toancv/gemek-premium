/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.resident;
import vn.vtit.gemek.support.AbstractIntegrationTest;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import vn.vtit.gemek.module.apartment.entity.Apartment;
import vn.vtit.gemek.module.apartment.entity.Block;
import vn.vtit.gemek.module.apartment.repository.ApartmentRepository;
import vn.vtit.gemek.module.apartment.repository.BlockRepository;
import vn.vtit.gemek.module.notification.entity.Notification;
import vn.vtit.gemek.module.notification.entity.NotificationType;
import vn.vtit.gemek.module.resident.dto.CreateResidentRequest;
import vn.vtit.gemek.module.resident.dto.ResidentResponse;
import vn.vtit.gemek.module.resident.entity.Resident;
import vn.vtit.gemek.module.resident.entity.ResidentType;
import vn.vtit.gemek.module.resident.repository.ResidentRepository;
import vn.vtit.gemek.module.user.entity.User;
import vn.vtit.gemek.module.user.entity.UserRole;
import vn.vtit.gemek.module.user.repository.UserRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * N3 P4 — C9 household dispatch tests.
 *
 * <p>Verifies that creating a resident notifies the apartment's existing ACTIVE
 * household members with the exact Vietnamese strings, and that the new user,
 * moved-out residents, and the acting user receive nothing. The empty-apartment
 * case must produce zero rows without error.
 *
 * <p>Class-level {@code @Transactional} rolls all fixtures back.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ResidentHouseholdDispatchTest extends AbstractIntegrationTest {

    @Autowired
    private ResidentService residentService;

    @Autowired
    private ResidentRepository residentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BlockRepository blockRepository;

    @Autowired
    private ApartmentRepository apartmentRepository;

    @PersistenceContext
    private EntityManager entityManager;

    private Apartment apartment;
    private User admin;
    private User memberA;
    private User memberB;
    private User movedOut;
    private long tag;

    @BeforeEach
    void setUp() {
        tag = System.nanoTime();

        Block block = new Block();
        block.setName("P4-" + tag);
        block = blockRepository.save(block);

        apartment = new Apartment();
        apartment.setBlock(block);
        apartment.setFloor((short) 2);
        apartment.setUnitNumber("P4-" + tag);
        apartment = apartmentRepository.save(apartment);

        admin = saveUser(tag + 1, UserRole.ADMIN);
        memberA = saveUser(tag + 2, UserRole.RESIDENT);
        memberB = saveUser(tag + 3, UserRole.RESIDENT);
        movedOut = saveUser(tag + 4, UserRole.RESIDENT);

        saveResidency(memberA, apartment, null);
        saveResidency(memberB, apartment, null);
        // Former household member — must NOT be notified.
        saveResidency(movedOut, apartment, LocalDate.now().minusMonths(1));
    }

    @Test
    @DisplayName("C9 — both active members get the exact VN row; new user and moved-out member get nothing")
    void createResident_existingHousehold_notifiesActiveMembersOnly() {
        ResidentResponse created = residentService.createResident(
                createRequest("Trần Thị Mai"), admin.getId());

        List<Notification> rows = householdRows(created.getId());
        assertThat(rows)
                .extracting(row -> row.getUser().getId())
                .containsExactlyInAnyOrder(memberA.getId(), memberB.getId());

        Notification row = rows.get(0);
        assertThat(row.getTitle()).isEqualTo("Thành viên mới");
        assertThat(row.getBody()).isEqualTo("Cư dân Trần Thị Mai đã được thêm vào căn hộ "
                + apartment.getUnitNumber() + ".");
        assertThat(row.getType()).isEqualTo(NotificationType.HOUSEHOLD_MEMBER_ADDED);
        assertThat(row.getReferenceId()).isEqualTo(created.getId());
        assertThat(row.getReferenceType()).isEqualTo("Resident");

        // The new user has no notification rows at all.
        Long newUserRows = entityManager.createQuery(
                        "SELECT COUNT(n) FROM Notification n WHERE n.user.id = "
                                + "(SELECT r.user.id FROM Resident r WHERE r.id = :residentId)",
                        Long.class)
                .setParameter("residentId", created.getId())
                .getSingleResult();
        assertThat(newUserRows).isZero();
    }

    @Test
    @DisplayName("C9 — first resident of an empty apartment: zero rows, no exception")
    void createResident_emptyApartment_dispatchesNothing() {
        Apartment empty = new Apartment();
        empty.setBlock(apartment.getBlock());
        empty.setFloor((short) 3);
        empty.setUnitNumber("P4E-" + tag);
        final Apartment emptyApartment = apartmentRepository.save(empty);

        assertThatCode(() -> {
            ResidentResponse created = residentService.createResident(
                    createRequest("Lê Văn Bình", emptyApartment.getId(), "0399"), admin.getId());
            assertThat(householdRows(created.getId())).isEmpty();
        }).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("C9 — acting user inside the household is excluded (uniform actor-exclusion rule)")
    void createResident_actorIsHouseholdMember_actorExcluded() {
        ResidentResponse created = residentService.createResident(
                createRequest("Phạm Văn Cường"), memberA.getId());

        assertThat(householdRows(created.getId()))
                .extracting(row -> row.getUser().getId())
                .containsExactly(memberB.getId());
    }

    // =========================================================================
    // Fixture and query helpers
    // =========================================================================

    /**
     * Builds a create request for the fixture apartment with a unique "03" phone.
     *
     * @param fullName the new resident's display name.
     * @return the request.
     */
    private CreateResidentRequest createRequest(String fullName) {
        return createRequest(fullName, apartment.getId(), "0388");
    }

    /**
     * Builds a create request with an explicit apartment and phone prefix.
     *
     * @param fullName    the new resident's display name.
     * @param apartmentId the target apartment.
     * @param prefix      4-digit VN mobile prefix for uniqueness separation.
     * @return the request.
     */
    private CreateResidentRequest createRequest(String fullName, UUID apartmentId, String prefix) {
        String phone = prefix + String.format("%06d", Math.abs(System.nanoTime()) % 1_000_000L);
        return new CreateResidentRequest(
                fullName, null, "Password@123456", phone, LocalDate.of(1990, 1, 1),
                apartmentId, ResidentType.TENANT, LocalDate.now(), false, null);
    }

    /**
     * Persists an active user with the given role and a unique "03"-prefixed phone.
     *
     * @param userTag uniqueness tag.
     * @param role    the role to assign.
     * @return the saved user.
     */
    private User saveUser(long userTag, UserRole role) {
        User user = new User();
        user.setPhone("03" + String.format("%08d", Math.abs(userTag) % 100_000_000L));
        user.setFullName("P4 " + role + " " + userTag);
        user.setPasswordHash("test-hash-not-a-credential");
        user.setRole(role);
        user.setActive(true);
        return userRepository.save(user);
    }

    /**
     * Persists a residency, optionally already moved out.
     *
     * @param user        the resident user.
     * @param target      the apartment.
     * @param moveOutDate move-out date; {@code null} for an active residency.
     */
    private void saveResidency(User user, Apartment target, LocalDate moveOutDate) {
        Resident residency = new Resident();
        residency.setUser(user);
        residency.setApartment(target);
        residency.setType(ResidentType.OWNER);
        residency.setMoveInDate(LocalDate.now().minusYears(1));
        residency.setMoveOutDate(moveOutDate);
        residentRepository.save(residency);
    }

    /**
     * Lists all HOUSEHOLD_MEMBER_ADDED rows referencing the given resident.
     *
     * @param residentId the new resident's UUID.
     * @return matching rows.
     */
    private List<Notification> householdRows(UUID residentId) {
        return entityManager.createQuery(
                        "SELECT n FROM Notification n WHERE n.referenceId = :id AND n.type = :type",
                        Notification.class)
                .setParameter("id", residentId)
                .setParameter("type", NotificationType.HOUSEHOLD_MEMBER_ADDED)
                .getResultList();
    }
}
