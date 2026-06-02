/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import vn.vtit.gemek.common.storage.FileStorageService;
import vn.vtit.gemek.module.amenity.dto.ApproveRejectRequest;
import vn.vtit.gemek.module.amenity.dto.CreateAmenityRequest;
import vn.vtit.gemek.module.amenity.dto.CreateBookingRequest;
import vn.vtit.gemek.module.amenity.entity.BookingStatus;
import vn.vtit.gemek.module.apartment.dto.CreateApartmentRequest;
import vn.vtit.gemek.module.apartment.dto.CreateBlockRequest;
import vn.vtit.gemek.module.auth.dto.LoginRequest;
import vn.vtit.gemek.module.resident.dto.CreateResidentRequest;
import vn.vtit.gemek.module.resident.entity.ResidentType;
import vn.vtit.gemek.module.user.dto.CreateUserRequest;
import vn.vtit.gemek.module.user.entity.UserRole;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * G4 cross-module integration tests for amenity booking flows.
 *
 * <p>Covers:
 * <ol>
 *   <li>Book requires-approval amenity, admin approves — resident sees APPROVED status.</li>
 *   <li>Conflicting booking on same slot by second resident returns 409.</li>
 *   <li>No-approval amenity booking is immediately APPROVED without admin action.</li>
 *   <li>Resident cancels PENDING booking; second cancel attempt returns 409 or 404.</li>
 * </ol>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AmenityBookingIntegrationTest {

    /** Mock MinIO — not started in this test suite. */
    @MockBean
    private FileStorageService fileStorageService;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String adminToken;
    private String residentToken;

    /**
     * Fresh amenity requiring admin approval, created per test to avoid cross-run slot conflicts.
     * Each test creates its own amenity — different amenity IDs guarantee zero slot conflicts
     * regardless of how many times the suite runs against the same DB.
     */
    private UUID bbqAmenityId;

    /** Fresh no-approval amenity, created per test. */
    private UUID gymAmenityId;

    /** Fixed booking date: today + 7 days, always within the 14-day advance window. */
    private static final LocalDate BOOKING_DATE = LocalDate.now().plusDays(7);

    private static final String ADMIN_EMAIL    = "admin@gemek.vn";
    private static final String ADMIN_PASSWORD = "Admin@123456";

    @BeforeEach
    void setUp() throws Exception {
        adminToken = login(ADMIN_EMAIL, ADMIN_PASSWORD);

        String uid    = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String email  = "res.ab." + uid + "@test.com";
        UUID blockId  = createBlock("ABBlock-" + uid);
        UUID aptId    = createApartment(blockId, "AB-" + uid);
        UUID userId   = createUser(email, UserRole.RESIDENT);
        assignResident(userId, aptId);
        residentToken = login(email, "Password@123456");

        // Create a fresh amenity per test — unique UUID name means no cross-run slot conflicts.
        bbqAmenityId = createAmenity("BBQ-" + uid, true, (short) 1);
        gymAmenityId = createAmenity("Gym-" + uid, false, (short) 1);
    }

    // =========================================================================
    // Test 1 — book requires-approval amenity, admin approves, resident sees APPROVED
    // =========================================================================

    @Test
    @DisplayName("Resident books requires-approval amenity → PENDING; admin approves → APPROVED in resident's list")
    void bookAndApprove_fullFlow() throws Exception {
        CreateBookingRequest req = buildBookingRequest(
                bbqAmenityId, BOOKING_DATE, LocalTime.of(10, 0), LocalTime.of(11, 0));

        MvcResult createResult = mockMvc.perform(post("/api/amenity-bookings")
                        .header("Authorization", "Bearer " + residentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn();

        String bookingId = (String) objectMapper.readValue(
                createResult.getResponse().getContentAsString(), Map.class).get("id");

        // Admin approves.
        ApproveRejectRequest approveReq = new ApproveRejectRequest();
        approveReq.setStatus(BookingStatus.APPROVED);
        mockMvc.perform(put("/api/amenity-bookings/" + bookingId + "/approve")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(approveReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        // Resident fetches their booking directly and verifies APPROVED status.
        mockMvc.perform(get("/api/amenity-bookings/" + bookingId)
                        .header("Authorization", "Bearer " + residentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));
    }

    // =========================================================================
    // Test 2 — second resident books same slot → 409 CONFLICT
    // =========================================================================

    @Test
    @DisplayName("Second resident books same amenity/date/time slot → 409 CONFLICT")
    void conflictingBooking_rejected() throws Exception {
        // Resident A books and admin approves to lock the slot on this test's unique bbqAmenityId.
        CreateBookingRequest reqA = buildBookingRequest(
                bbqAmenityId, BOOKING_DATE, LocalTime.of(10, 0), LocalTime.of(11, 0));
        MvcResult createA = mockMvc.perform(post("/api/amenity-bookings")
                        .header("Authorization", "Bearer " + residentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqA)))
                .andExpect(status().isCreated())
                .andReturn();
        String bookingIdA = (String) objectMapper.readValue(
                createA.getResponse().getContentAsString(), Map.class).get("id");

        ApproveRejectRequest approveReq = new ApproveRejectRequest();
        approveReq.setStatus(BookingStatus.APPROVED);
        mockMvc.perform(put("/api/amenity-bookings/" + bookingIdA + "/approve")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(approveReq)))
                .andExpect(status().isOk());

        // Create a second resident with a different apartment.
        String uid2    = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String emailB  = "res.ab2." + uid2 + "@test.com";
        UUID blockId2  = createBlock("ABBlock2-" + uid2);
        UUID aptId2    = createApartment(blockId2, "AB2-" + uid2);
        UUID userIdB   = createUser(emailB, UserRole.RESIDENT);
        assignResident(userIdB, aptId2);
        String tokenB  = login(emailB, "Password@123456");

        // Resident B attempts the same slot — must conflict.
        CreateBookingRequest reqB = buildBookingRequest(
                bbqAmenityId, BOOKING_DATE, LocalTime.of(10, 0), LocalTime.of(11, 0));
        mockMvc.perform(post("/api/amenity-bookings")
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqB)))
                .andExpect(status().isConflict());
    }

    // =========================================================================
    // Test 3 — no-approval amenity → status=APPROVED immediately
    // =========================================================================

    @Test
    @DisplayName("No-approval amenity booking is immediately APPROVED without admin action")
    void autoApprove_noApprovalRequired() throws Exception {
        CreateBookingRequest req = buildBookingRequest(
                gymAmenityId, BOOKING_DATE, LocalTime.of(10, 0), LocalTime.of(11, 0));

        mockMvc.perform(post("/api/amenity-bookings")
                        .header("Authorization", "Bearer " + residentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.id").isNotEmpty());
    }

    // =========================================================================
    // Test 4 — cancel PENDING booking; second cancel attempt returns 409 or 404
    // =========================================================================

    @Test
    @DisplayName("Resident cancels PENDING booking → CANCELLED; second cancel returns 409 or 404")
    void cancelPending_allowed() throws Exception {
        CreateBookingRequest req = buildBookingRequest(
                bbqAmenityId, BOOKING_DATE, LocalTime.of(10, 0), LocalTime.of(11, 0));

        MvcResult createResult = mockMvc.perform(post("/api/amenity-bookings")
                        .header("Authorization", "Bearer " + residentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn();

        String bookingId = (String) objectMapper.readValue(
                createResult.getResponse().getContentAsString(), Map.class).get("id");

        // First cancel — must succeed with CANCELLED status.
        mockMvc.perform(put("/api/amenity-bookings/" + bookingId + "/cancel")
                        .header("Authorization", "Bearer " + residentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        // Second cancel — booking is already CANCELLED; must return 409 or 404.
        mockMvc.perform(put("/api/amenity-bookings/" + bookingId + "/cancel")
                        .header("Authorization", "Bearer " + residentToken))
                .andExpect(result -> {
                    int statusCode = result.getResponse().getStatus();
                    org.junit.jupiter.api.Assertions.assertTrue(
                            statusCode == 409 || statusCode == 404,
                            "Second cancel must return 409 or 404, got: " + statusCode);
                });
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private String login(String email, String password) throws Exception {
        LoginRequest req = new LoginRequest(email, password);
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn();
        Map<?, ?> body = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
        return (String) body.get("accessToken");
    }

    private UUID createBlock(String name) throws Exception {
        CreateBlockRequest req = new CreateBlockRequest(name, null);
        MvcResult result = mockMvc.perform(post("/api/blocks")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();
        Map<?, ?> body = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
        return UUID.fromString((String) body.get("id"));
    }

    private UUID createApartment(UUID blockId, String unitNumber) throws Exception {
        CreateApartmentRequest req = new CreateApartmentRequest(blockId, (short) 3, unitNumber, null, null);
        MvcResult result = mockMvc.perform(post("/api/apartments")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();
        Map<?, ?> body = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
        return UUID.fromString((String) body.get("id"));
    }

    private UUID createUser(String email, UserRole role) throws Exception {
        CreateUserRequest req = new CreateUserRequest(
                email, "Test User", "0900000099", role, "Password@123456");
        MvcResult result = mockMvc.perform(post("/api/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();
        Map<?, ?> body = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
        return UUID.fromString((String) body.get("id"));
    }

    private void assignResident(UUID userId, UUID apartmentId) throws Exception {
        CreateResidentRequest req = new CreateResidentRequest(
                userId, apartmentId, ResidentType.OWNER,
                LocalDate.of(2026, 1, 1), true, null);
        mockMvc.perform(post("/api/residents")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());
    }

    private UUID createAmenity(String name, boolean requiresApproval, short maxDaily) throws Exception {
        CreateAmenityRequest req = new CreateAmenityRequest();
        req.setName(name);
        req.setDescription("Test amenity");
        req.setLocation("Test floor");
        req.setOpeningTime(LocalTime.of(10, 0));
        req.setClosingTime(LocalTime.of(22, 0));
        req.setMaxDailyBookingsPerResident(maxDaily);
        req.setRequiresApproval(requiresApproval);
        MvcResult result = mockMvc.perform(post("/api/amenities")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();
        Map<?, ?> body = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
        return UUID.fromString((String) body.get("id"));
    }

    private CreateBookingRequest buildBookingRequest(UUID amenityId, LocalDate bookingDate,
                                                      LocalTime start, LocalTime end) {
        CreateBookingRequest req = new CreateBookingRequest();
        req.setAmenityId(amenityId);
        req.setBookingDate(bookingDate);
        req.setStartTime(start);
        req.setEndTime(end);
        return req;
    }
}
