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
import vn.vtit.gemek.module.apartment.repository.BlockRepository;
import vn.vtit.gemek.module.resident.repository.ResidentRepository;
import vn.vtit.gemek.module.user.repository.UserRepository;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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

    private AnnouncementServiceImpl service;

    private UUID announcementId;
    private UUID principalId;
    private Announcement draftAnnouncement;
    private Announcement publishedAnnouncement;

    @BeforeEach
    void setUp() {
        service = new AnnouncementServiceImpl(
                announcementRepository, announcementReadRepository,
                blockRepository, userRepository, residentRepository);

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
    @DisplayName("updateAnnouncement — editing a published announcement throws CONFLICT")
    void updateAnnouncement_publishedAnnouncement_throwsConflict() {
        when(announcementRepository.findById(announcementId))
                .thenReturn(Optional.of(publishedAnnouncement));

        UpdateAnnouncementRequest request = new UpdateAnnouncementRequest();
        request.setTitle("New Title");

        assertThatThrownBy(() -> service.updateAnnouncement(announcementId, request))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.CONFLICT));
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
        request.setScope(AnnouncementScope.BLOCK);
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
        request.setScope(AnnouncementScope.FLOOR);
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
        request.setScope(AnnouncementScope.FLOOR);
        request.setTargetBlockId(UUID.randomUUID());
        // targetFloor intentionally null

        assertThatThrownBy(() -> service.createAnnouncement(request, principalId))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.VALIDATION_ERROR));
    }
}
