/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.announcement;

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
import vn.vtit.gemek.module.announcement.dto.CreateAnnouncementRequest;
import vn.vtit.gemek.module.announcement.dto.UpdateAnnouncementRequest;
import vn.vtit.gemek.module.announcement.entity.Announcement;
import vn.vtit.gemek.module.announcement.entity.AnnouncementScope;
import vn.vtit.gemek.module.announcement.entity.AnnouncementType;
import vn.vtit.gemek.module.announcement.repository.AnnouncementReadRepository;
import vn.vtit.gemek.module.announcement.repository.AnnouncementRepository;
import vn.vtit.gemek.module.apartment.entity.Apartment;
import vn.vtit.gemek.module.apartment.entity.Block;
import vn.vtit.gemek.module.apartment.repository.BlockRepository;
import vn.vtit.gemek.module.notification.repository.NotificationRepository;
import vn.vtit.gemek.module.resident.entity.Resident;
import vn.vtit.gemek.module.resident.entity.ResidentType;
import vn.vtit.gemek.module.resident.repository.ResidentRepository;
import vn.vtit.gemek.module.user.repository.UserRepository;

import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AnnouncementServiceImpl} — GAP-10 access control.
 *
 * <p>Covers: RESIDENT draft-view NOT_FOUND, published-edit/delete CONFLICT,
 * SEC-07 draft markRead NOT_FOUND, and scope constraint VALIDATION_ERROR.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AnnouncementServiceImplTest {

    @Mock private AnnouncementRepository announcementRepository;
    @Mock private AnnouncementReadRepository announcementReadRepository;
    @Mock private BlockRepository blockRepository;
    @Mock private UserRepository userRepository;
    @Mock private ResidentRepository residentRepository;
    @Mock private NotificationRepository notificationRepository;

    private AnnouncementServiceImpl service;

    private UUID announcementId;
    private UUID principalId;
    private Announcement draftAnnouncement;
    private Announcement publishedAnnouncement;

    @BeforeEach
    void setUp() {
        service = new AnnouncementServiceImpl(
                announcementRepository, announcementReadRepository,
                blockRepository, userRepository, residentRepository,
                notificationRepository);

        announcementId = UUID.randomUUID();
        principalId = UUID.randomUUID();

        draftAnnouncement = new Announcement();
        draftAnnouncement.setId(announcementId);
        draftAnnouncement.setTitle("Draft");
        draftAnnouncement.setContent("Draft content");
        draftAnnouncement.setScope(AnnouncementScope.ALL);
        // publishedAt intentionally null — draft state

        publishedAnnouncement = new Announcement();
        publishedAnnouncement.setId(announcementId);
        publishedAnnouncement.setTitle("Published");
        publishedAnnouncement.setContent("Published content");
        publishedAnnouncement.setScope(AnnouncementScope.ALL);
        publishedAnnouncement.setPublishedAt(OffsetDateTime.now());
    }

    // =========================================================================
    // getAnnouncement — RESIDENT viewing draft → NOT_FOUND (not leaked)
    // =========================================================================

    @Test
    @DisplayName("getAnnouncement — RESIDENT viewing unpublished draft throws NOT_FOUND")
    void getAnnouncement_residentViewingDraft_throwsNotFound() {
        when(announcementRepository.findById(announcementId))
                .thenReturn(Optional.of(draftAnnouncement));

        assertThatThrownBy(() -> service.getAnnouncement(announcementId, principalId, "RESIDENT"))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.NOT_FOUND));
    }

    // =========================================================================
    // getAnnouncement — announcement not found → NOT_FOUND
    // =========================================================================

    @Test
    @DisplayName("getAnnouncement — unknown announcement ID throws NOT_FOUND")
    void getAnnouncement_notFound_throwsNotFound() {
        UUID unknownId = UUID.randomUUID();
        when(announcementRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getAnnouncement(unknownId, principalId, "ADMIN"))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.NOT_FOUND));
    }

    // =========================================================================
    // updateAnnouncement — already published → CONFLICT (immutable)
    // =========================================================================

    @Test
    @DisplayName("updateAnnouncement — editing a published announcement throws INVALID_STATUS_TRANSITION")
    void updateAnnouncement_publishedAnnouncement_throwsInvalidStatusTransition() {
        when(announcementRepository.findById(announcementId))
                .thenReturn(Optional.of(publishedAnnouncement));

        UpdateAnnouncementRequest request = new UpdateAnnouncementRequest();
        request.setTitle("New Title");

        assertThatThrownBy(() -> service.updateAnnouncement(announcementId, request))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_STATUS_TRANSITION));
    }

    // =========================================================================
    // deleteAnnouncement — already published → CONFLICT (preserve read history)
    // =========================================================================

    @Test
    @DisplayName("deleteAnnouncement — deleting a published announcement throws CONFLICT")
    void deleteAnnouncement_publishedAnnouncement_throwsConflict() {
        when(announcementRepository.findById(announcementId))
                .thenReturn(Optional.of(publishedAnnouncement));

        assertThatThrownBy(() -> service.deleteAnnouncement(announcementId))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.CONFLICT));
    }

    // =========================================================================
    // markRead — draft announcement → NOT_FOUND (SEC-07)
    // =========================================================================

    @Test
    @DisplayName("markRead — marking a draft announcement as read throws NOT_FOUND (SEC-07)")
    void markRead_draftAnnouncement_throwsNotFound() {
        when(announcementRepository.findById(announcementId))
                .thenReturn(Optional.of(draftAnnouncement));

        assertThatThrownBy(() -> service.markRead(announcementId, principalId))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.NOT_FOUND));
    }

    // =========================================================================
    // createAnnouncement — BLOCK scope without targetBlockId → VALIDATION_ERROR
    // =========================================================================

    @Test
    @DisplayName("createAnnouncement — BLOCK scope without targetBlockId throws VALIDATION_ERROR")
    void createAnnouncement_blockScopeWithoutBlockId_throwsValidationError() {
        CreateAnnouncementRequest request = new CreateAnnouncementRequest();
        request.setTitle("Block Announcement");
        request.setContent("Content");
        request.setType(AnnouncementType.GENERAL);
        request.setTargetScope(AnnouncementScope.BLOCK);
        // targetBlockId intentionally null

        assertThatThrownBy(() -> service.createAnnouncement(request, principalId))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.VALIDATION_ERROR));
    }

    // =========================================================================
    // createAnnouncement — FLOOR scope without targetBlockId → VALIDATION_ERROR
    // =========================================================================

    @Test
    @DisplayName("createAnnouncement — FLOOR scope without targetBlockId throws VALIDATION_ERROR")
    void createAnnouncement_floorScopeWithoutBlockId_throwsValidationError() {
        CreateAnnouncementRequest request = new CreateAnnouncementRequest();
        request.setTitle("Floor Announcement");
        request.setContent("Content");
        request.setType(AnnouncementType.GENERAL);
        request.setTargetScope(AnnouncementScope.FLOOR);
        request.setTargetFloor((short) 3);
        // targetBlockId intentionally null

        assertThatThrownBy(() -> service.createAnnouncement(request, principalId))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.VALIDATION_ERROR));
    }

    // =========================================================================
    // createAnnouncement — FLOOR scope without targetFloor → VALIDATION_ERROR
    // =========================================================================

    @Test
    @DisplayName("createAnnouncement — FLOOR scope without targetFloor throws VALIDATION_ERROR")
    void createAnnouncement_floorScopeWithoutFloor_throwsValidationError() {
        CreateAnnouncementRequest request = new CreateAnnouncementRequest();
        request.setTitle("Floor Announcement");
        request.setContent("Content");
        request.setType(AnnouncementType.GENERAL);
        request.setTargetScope(AnnouncementScope.FLOOR);
        request.setTargetBlockId(UUID.randomUUID());
        // targetFloor intentionally null

        assertThatThrownBy(() -> service.createAnnouncement(request, principalId))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.VALIDATION_ERROR));
    }

    // =========================================================================
    // listAnnouncements — N+1 guard: creator names resolved in ONE batch query
    // =========================================================================

    @Test
    @DisplayName("listAnnouncements (ADMIN) — resolves creator names via a single findAllById (no per-row findById)")
    void listAnnouncements_resolvesCreatorNamesInBatch_noN1() {
        // Three announcements, each with a distinct creator UUID — naive mapping would do 3 lookups.
        Announcement a1 = announcementWithCreator(UUID.randomUUID());
        Announcement a2 = announcementWithCreator(UUID.randomUUID());
        Announcement a3 = announcementWithCreator(UUID.randomUUID());

        when(announcementRepository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(a1, a2, a3)));
        when(announcementReadRepository.findReadAnnouncementIds(any(), anyList()))
                .thenReturn(List.of());
        when(userRepository.findAllById(any())).thenReturn(List.of());

        service.listAnnouncements(principalId, "ADMIN", PageRequest.of(0, 10));

        // Batch resolution: exactly one user query for the whole page, never one per row.
        verify(userRepository, times(1)).findAllById(any());
        verify(userRepository, never()).findById(any());
    }

    // =========================================================================
    // listAnnouncements (RESIDENT) — multi-residency: union across active apartments,
    // each announcement AT MOST ONCE (one query, never per-apartment concat).
    // =========================================================================

    @Test
    @DisplayName("listAnnouncements (RESIDENT, 2 apartments) — one feed query, ALL announcement appears once (no duplicate)")
    void listAnnouncements_residentTwoApartments_noDuplicateAndSingleQuery() {
        // A user actively residing in two apartments (different blocks).
        Resident residencyA = residencyIn("Block A", (short) 1);
        Resident residencyB = residencyIn("Block B", (short) 5);
        when(residentRepository.findAllActiveByUserId(principalId))
                .thenReturn(List.of(residencyA, residencyB));

        // A single building-wide (ALL) announcement — it must NOT be duplicated by the two residencies.
        Announcement allScoped = announcementWithCreator(UUID.randomUUID());
        when(announcementRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(allScoped)));
        when(announcementReadRepository.findReadAnnouncementIds(any(), anyList()))
                .thenReturn(List.of());
        when(userRepository.findAllById(any())).thenReturn(List.of());

        var page = service.listAnnouncements(principalId, "RESIDENT", PageRequest.of(0, 20));

        // Union-not-concat: ONE feed query for all residencies (a per-apartment loop would query twice).
        verify(announcementRepository, times(1))
                .findAll(any(Specification.class), any(Pageable.class));
        // The ALL announcement appears exactly once for the multi-apartment resident.
        assertThat(page.getData()).hasSize(1);
    }

    /**
     * Builds an active residency in a freshly-created block at the given floor.
     *
     * @param blockName the block display name.
     * @param floor     the apartment floor.
     * @return an active {@link Resident} with apartment and block populated.
     */
    private Resident residencyIn(String blockName, short floor) {
        Block block = new Block();
        block.setId(UUID.randomUUID());
        block.setName(blockName);
        Apartment apartment = new Apartment();
        apartment.setId(UUID.randomUUID());
        apartment.setBlock(block);
        apartment.setFloor(floor);
        Resident resident = new Resident();
        resident.setId(UUID.randomUUID());
        resident.setApartment(apartment);
        resident.setType(ResidentType.OWNER);
        resident.setMoveInDate(LocalDate.of(2026, 1, 1));
        return resident;
    }

    /**
     * Builds a published announcement carrying the given creator actor UUID.
     *
     * @param creatorId the creator actor UUID.
     * @return an announcement with id, scope, and createdBy populated.
     */
    private Announcement announcementWithCreator(UUID creatorId) {
        Announcement a = new Announcement();
        a.setId(UUID.randomUUID());
        a.setTitle("A");
        a.setContent("body");
        a.setScope(AnnouncementScope.ALL);
        a.setType(AnnouncementType.GENERAL);
        a.setPublishedAt(OffsetDateTime.now());
        a.setCreatedBy(creatorId);
        return a;
    }

    // =========================================================================
    // assertMediaPresignAccess — C2.1 scope-mirroring presign gate (security)
    // =========================================================================

    /**
     * Builds a media object key following the C2.1 convention announcements/{id}/{file}.
     *
     * @param announcementId the owning announcement id.
     * @return a well-formed media object key.
     */
    private static String mediaKey(UUID announcementId) {
        return "announcements/" + announcementId + "/" + UUID.randomUUID() + ".jpg";
    }

    @Test
    @DisplayName("assertMediaPresignAccess — RESIDENT in announcement scope is ALLOWED")
    void assertMediaPresignAccess_residentInScope_allows() {
        when(announcementRepository.existsReadableByResident(announcementId, principalId))
                .thenReturn(true);

        // No throw == allowed; the parsed id is forwarded verbatim to the scope query.
        service.assertMediaPresignAccess(mediaKey(announcementId), principalId, "RESIDENT");

        verify(announcementRepository).existsReadableByResident(announcementId, principalId);
    }

    @Test
    @DisplayName("assertMediaPresignAccess — RESIDENT out of scope is DENIED (FORBIDDEN)")
    void assertMediaPresignAccess_residentOutOfScope_denies() {
        when(announcementRepository.existsReadableByResident(announcementId, principalId))
                .thenReturn(false);

        assertThatThrownBy(() ->
                service.assertMediaPresignAccess(mediaKey(announcementId), principalId, "RESIDENT"))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.FORBIDDEN));
    }

    @Test
    @DisplayName("assertMediaPresignAccess — RESIDENT on draft/nonexistent (query=false) is DENIED")
    void assertMediaPresignAccess_residentDraftOrMissing_denies() {
        // The published-only + existence gate lives in the JPQL: a draft or a nonexistent id
        // both surface here as existsReadableByResident == false → deny. No 500.
        when(announcementRepository.existsReadableByResident(any(UUID.class), any(UUID.class)))
                .thenReturn(false);

        assertThatThrownBy(() ->
                service.assertMediaPresignAccess(mediaKey(UUID.randomUUID()), principalId, "RESIDENT"))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.FORBIDDEN));
    }

    @Test
    @DisplayName("assertMediaPresignAccess — ADMIN is unrestricted (no scope query)")
    void assertMediaPresignAccess_admin_allowsWithoutQuery() {
        service.assertMediaPresignAccess(mediaKey(announcementId), principalId, "ADMIN");

        verify(announcementRepository, never()).existsReadableByResident(any(), any());
    }

    @Test
    @DisplayName("assertMediaPresignAccess — BOARD_MEMBER is unrestricted (no scope query)")
    void assertMediaPresignAccess_boardMember_allowsWithoutQuery() {
        service.assertMediaPresignAccess(mediaKey(announcementId), principalId, "BOARD_MEMBER");

        verify(announcementRepository, never()).existsReadableByResident(any(), any());
    }

    @Test
    @DisplayName("assertMediaPresignAccess — TECHNICIAN (no announcement audience) is DENIED")
    void assertMediaPresignAccess_technician_denies() {
        assertThatThrownBy(() ->
                service.assertMediaPresignAccess(mediaKey(announcementId), principalId, "TECHNICIAN"))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.FORBIDDEN));
        verify(announcementRepository, never()).existsReadableByResident(any(), any());
    }

    @Test
    @DisplayName("assertMediaPresignAccess — malformed key (no id segment) is DENIED, no 500")
    void assertMediaPresignAccess_malformedNoSegment_denies() {
        // "announcements/x" has no <id>/<file> shape → deny before any role/query work.
        assertThatThrownBy(() ->
                service.assertMediaPresignAccess("announcements/loose-file.jpg", principalId, "RESIDENT"))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.FORBIDDEN));
        verify(announcementRepository, never()).existsReadableByResident(any(), any());
    }

    @Test
    @DisplayName("assertMediaPresignAccess — malformed key (non-UUID id) is DENIED, no 500")
    void assertMediaPresignAccess_malformedBadUuid_denies() {
        assertThatThrownBy(() ->
                service.assertMediaPresignAccess("announcements/not-a-uuid/file.jpg", principalId, "ADMIN"))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.FORBIDDEN));
    }
}
