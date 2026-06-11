/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.amenity;

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
import java.util.HashMap;
import vn.vtit.gemek.module.user.dto.CreateUserRequest;
import vn.vtit.gemek.module.user.entity.UserRole;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for {@link AmenityController} using Testcontainers PostgreSQL and Redis.
 *
 * <p>{@link FileStorageService} is mocked because MinIO is not started in this test suite.
 *
 * <p>Test coverage:
 * <ol>
 *   <li>POST /api/amenity-bookings (RESIDENT, no-approval amenity) → 201, status=APPROVED</li>
 *   <li>POST /api/amenity-bookings overlapping slot → 409 CONFLICT</li>
 *   <li>POST /api/amenity-bookings daily limit exceeded → 409 CONFLICT</li>
 *   <li>PUT  /api/amenity-bookings/{id}/approve → 200, status=APPROVED</li>
 *   <li>PUT  /api/amenity-bookings/{id}/cancel (RESIDENT) → 200, status=CANCELLED</li>
 * </ol>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AmenityControllerTest {

    /** Mock MinIO — not started in this test suite. */
    @MockBean
    private FileStorageService fileStorageService;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String adminToken;
    private String residentToken;

    /** Fresh no-approval amenity created per test to eliminate cross-run slot conflicts. */
    private UUID gymAmenityId;
    /** Fresh requires-approval amenity created per test. */
    private UUID bbqAmenityId;

    /** Fixed booking date: today + 7 days, always within the 14-day advance window. */
    private static final LocalDate BOOKING_DATE = LocalDate.now().plusDays(7);

    private static final String ADMIN_PHONE    = "0900000000";
    // Password matches the hash stored when the admin was first seeded via ADMIN_PASSWORD in .env.
    private static final String ADMIN_PASSWORD = "GemekAdmin2026";

    /**
     * Authenticates admin, creates a resident user and apartment, and resolves the
     * seeded amenity IDs from the amenity list endpoint.
     */
    @BeforeEach
    void setUp() throws Exception {
        adminToken = login(ADMIN_PHONE, ADMIN_PASSWORD);

        // Create unique resident per test (UUID suffix guarantees uniqueness across runs).
        String uid = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String residentPhone = phoneFromUid(uid);
        UUID blockId = createBlock("AmBlock-" + uid);
        UUID apartmentId = createApartment(blockId, "AM-" + uid);
        assignResident(residentPhone, apartmentId);
        residentToken = login(residentPhone, "Password@123456");

        // Create fresh amenities per test — unique names eliminate all cross-run slot conflicts.
        gymAmenityId = createAmenity("Gym-" + uid, false, (short) 1);
        bbqAmenityId = createAmenity("BBQ-" + uid, true, (short) 2);
    }

    // =========================================================================
    // Test 1 — POST /api/amenity-bookings (no-approval amenity) → 201, APPROVED
    // =========================================================================

    /**
     * Verifies that booking a no-approval amenity immediately results in APPROVED status.
     */
    @Test
    @DisplayName("POST /api/amenity-bookings — no-approval amenity → 201, status=APPROVED")
    void createBooking_noApprovalAmenity_returns201StatusApproved() throws Exception {
        // baseHour gives a slot unique to this test instance; 1h duration satisfies SEC-11 min 30 min.
        CreateBookingRequest req = buildBookingRequest(
                gymAmenityId,
                BOOKING_DATE,
                LocalTime.of(10, 0),
                LocalTime.of(11, 0));

        mockMvc.perform(post("/api/amenity-bookings")
                        .header("Authorization", "Bearer " + residentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.amenity.id").value(gymAmenityId.toString()));
    }

    // =========================================================================
    // Test 2 — POST /api/amenity-bookings overlapping slot → 409
    // =========================================================================

    /**
     * Verifies that attempting to book an already-occupied time slot returns 409 CONFLICT.
     */
    @Test
    @DisplayName("POST /api/amenity-bookings — overlapping slot → 409 CONFLICT")
    void createBooking_overlappingSlot_returns409() throws Exception {
        // baseHour gives a slot unique to this test instance; two residents book the SAME slot to test conflict.
        LocalDate bookingDate = BOOKING_DATE;
        CreateBookingRequest first = buildBookingRequest(
                gymAmenityId, bookingDate, LocalTime.of(14, 0), LocalTime.of(15, 0));

        // Create a second resident to book the same slot — should conflict.
        String ovUid = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String secondResidentPhone = phoneFromUid(ovUid);
        UUID blockId2 = createBlock("OvBlock-" + ovUid);
        UUID apt2 = createApartment(blockId2, "OV-" + ovUid);
        assignResident(secondResidentPhone, apt2);
        String secondToken = login(secondResidentPhone, "Password@123456");

        // First booking succeeds.
        mockMvc.perform(post("/api/amenity-bookings")
                        .header("Authorization", "Bearer " + residentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(first)))
                .andExpect(status().isCreated());

        // Second booking on the SAME slot by a different resident must conflict.
        CreateBookingRequest second = buildBookingRequest(
                gymAmenityId, bookingDate, LocalTime.of(14, 0), LocalTime.of(15, 0));

        mockMvc.perform(post("/api/amenity-bookings")
                        .header("Authorization", "Bearer " + secondToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(second)))
                .andExpect(status().isConflict());
    }

    // =========================================================================
    // Test 3 — POST /api/amenity-bookings daily limit exceeded → 409
    // =========================================================================

    /**
     * Verifies that a resident exceeding the per-amenity daily booking limit receives 409 CONFLICT.
     *
     * <p>The Gym amenity has {@code maxDailyBookingsPerResident = 1} in the seed data.
     * The second booking on the same date must be rejected.
     */
    @Test
    @DisplayName("POST /api/amenity-bookings — daily limit exceeded → 409 CONFLICT")
    void createBooking_dailyLimitExceeded_returns409() throws Exception {
        // Slots 14:00-15:00 and 15:00-16:00; each test has a unique gym amenity so no cross-test conflict.
        LocalDate bookingDate = BOOKING_DATE;

        // First booking at 14:00 (unique amenity per test instance).
        CreateBookingRequest first = buildBookingRequest(
                gymAmenityId, bookingDate, LocalTime.of(14, 0), LocalTime.of(15, 0));
        mockMvc.perform(post("/api/amenity-bookings")
                        .header("Authorization", "Bearer " + residentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(first)))
                .andExpect(status().isCreated());

        // Second booking same date, non-overlapping time — per-resident daily limit is 1 so must fail.
        CreateBookingRequest second = buildBookingRequest(
                gymAmenityId, bookingDate, LocalTime.of(11, 0), LocalTime.of(12, 0));
        mockMvc.perform(post("/api/amenity-bookings")
                        .header("Authorization", "Bearer " + residentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(second)))
                .andExpect(status().isConflict());
    }

    // =========================================================================
    // Test 4 — PUT /api/amenity-bookings/{id}/approve → 200, APPROVED
    // =========================================================================

    /**
     * Verifies that an admin can approve a PENDING booking (requires-approval amenity).
     */
    @Test
    @DisplayName("PUT /api/amenity-bookings/{id}/approve — ADMIN approves PENDING → 200, APPROVED")
    void approveBooking_admin_returns200StatusApproved() throws Exception {
        // BBQ uses 14:00 slot — different amenity so no conflict with Gym bookings.
        LocalDate bookingDate = BOOKING_DATE;
        CreateBookingRequest req = buildBookingRequest(
                bbqAmenityId, bookingDate, LocalTime.of(14, 0), LocalTime.of(15, 0));

        MvcResult createResult = mockMvc.perform(post("/api/amenity-bookings")
                        .header("Authorization", "Bearer " + residentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn();

        Map<?, ?> created = objectMapper.readValue(
                createResult.getResponse().getContentAsString(), Map.class);
        String bookingId = (String) created.get("id");

        // Admin approves the booking.
        ApproveRejectRequest approveReq = new ApproveRejectRequest();
        approveReq.setStatus(BookingStatus.APPROVED);

        mockMvc.perform(put("/api/amenity-bookings/" + bookingId + "/approve")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(approveReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.approvedBy").isNotEmpty())
                .andExpect(jsonPath("$.approvedAt").isNotEmpty());
    }

    // =========================================================================
    // Test 5 — PUT /api/amenity-bookings/{id}/cancel (RESIDENT) → 200, CANCELLED
    // =========================================================================

    /**
     * Verifies that a resident can cancel their own future PENDING booking.
     */
    @Test
    @DisplayName("PUT /api/amenity-bookings/{id}/cancel — RESIDENT cancels own booking → 200, CANCELLED")
    void cancelBooking_resident_returns200StatusCancelled() throws Exception {
        // BBQ uses 14:00 slot — different amenity so no conflict with Gym bookings.
        LocalDate bookingDate = BOOKING_DATE;
        CreateBookingRequest req = buildBookingRequest(
                bbqAmenityId, bookingDate, LocalTime.of(14, 0), LocalTime.of(15, 0));

        MvcResult createResult = mockMvc.perform(post("/api/amenity-bookings")
                        .header("Authorization", "Bearer " + residentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();

        Map<?, ?> created = objectMapper.readValue(
                createResult.getResponse().getContentAsString(), Map.class);
        String bookingId = (String) created.get("id");

        // Resident cancels.
        mockMvc.perform(put("/api/amenity-bookings/" + bookingId + "/cancel")
                        .header("Authorization", "Bearer " + residentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    // =========================================================================
    // Helper methods
    // =========================================================================

    /**
     * Authenticates a user and returns the JWT access token.
     *
     * @param phone    user phone number in canonical 0xxxxxxxxx format.
     * @param password user password.
     * @return the JWT access token.
     */
    private String login(String phone, String password) throws Exception {
        LoginRequest req = new LoginRequest(phone, password);
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn();
        Map<?, ?> body = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
        return (String) body.get("accessToken");
    }

    /**
     * Creates a user and returns their UUID.
     *
     * @param email the user email.
     * @param role  the user role.
     * @return the created user UUID.
     */
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

    /**
     * Creates a block and returns its UUID.
     *
     * @param name block name.
     * @return the created block UUID.
     */
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

    /**
     * Creates an apartment on floor 3 and returns its UUID.
     *
     * @param blockId    the parent block UUID.
     * @param unitNumber the unit number string.
     * @return the created apartment UUID.
     */
    private UUID createApartment(UUID blockId, String unitNumber) throws Exception {
        CreateApartmentRequest req = new CreateApartmentRequest(
                blockId, (short) 3, unitNumber, null, null);
        MvcResult result = mockMvc.perform(post("/api/apartments")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();
        Map<?, ?> body = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
        return UUID.fromString((String) body.get("id"));
    }

    private void assignResident(String phone, UUID apartmentId) throws Exception {
        Map<String, Object> req = new HashMap<>();
        req.put("fullName", "Test Resident");
        req.put("phone", phone);
        req.put("dateOfBirth", "1990-01-01");
        req.put("password", "Password@123456");
        req.put("apartmentId", apartmentId.toString());
        req.put("type", "OWNER");
        req.put("moveInDate", "2026-01-01");
        req.put("isPrimaryContact", true);
        mockMvc.perform(post("/api/residents")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());
    }

    /**
     * Resolves the UUID of a seeded amenity by its exact display name.
     *
     * <p>Fetches the first page of amenities and searches for the matching name.
     * Throws an assertion error if not found (indicates seed data is missing).
     *
     * @param name the amenity name to look up.
     * @return the amenity UUID.
     */
    private UUID resolveAmenityByName(String name) throws Exception {
        MvcResult result = mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .get("/api/amenities")
                                .param("size", "50")
                                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();

        Map<?, ?> body = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
        java.util.List<?> data = (java.util.List<?>) body.get("data");

        for (Object item : data) {
            Map<?, ?> amenity = (Map<?, ?>) item;
            if (name.equals(amenity.get("name"))) {
                return UUID.fromString((String) amenity.get("id"));
            }
        }

        throw new AssertionError("Seeded amenity not found: " + name
                + ". Check V3 seed data or V8 migration.");
    }

    /**
     * Creates a new amenity via the API and returns its UUID.
     *
     * @param name                      unique amenity name.
     * @param requiresApproval          whether bookings need admin approval.
     * @param maxDailyBookingsPerResident per-resident daily booking cap.
     * @return the created amenity UUID.
     */
    private UUID createAmenity(String name, boolean requiresApproval,
                                short maxDailyBookingsPerResident) throws Exception {
        CreateAmenityRequest req = new CreateAmenityRequest();
        req.setName(name);
        req.setDescription("Test amenity");
        req.setLocation("Floor 1");
        req.setOpeningTime(LocalTime.of(8, 0));
        req.setClosingTime(LocalTime.of(22, 0));
        req.setMaxDailyBookingsPerResident(maxDailyBookingsPerResident);
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

    // =========================================================================
    // Test 9 — GET /api/amenity-bookings (RESIDENT) → 200, own bookings only
    // =========================================================================

    /**
     * Verifies that a RESIDENT can list amenity bookings and receives only their own.
     *
     * <p>Bug regression: was returning 403 because RESIDENT was excluded from @PreAuthorize.
     */
    @Test
    @DisplayName("GET /api/amenity-bookings — RESIDENT sees own bookings → 200")
    void listBookings_residentSeesOwnBookings_returns200() throws Exception {
        // Create a booking for the setUp resident.
        CreateBookingRequest req = buildBookingRequest(
                gymAmenityId, BOOKING_DATE, LocalTime.of(8, 0), LocalTime.of(9, 0));
        MvcResult createResult = mockMvc.perform(post("/api/amenity-bookings")
                        .header("Authorization", "Bearer " + residentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();
        Map<?, ?> created = objectMapper.readValue(
                createResult.getResponse().getContentAsString(), Map.class);
        String ownBookingId = (String) created.get("id");

        // RESIDENT lists bookings — must return 200 with that booking.
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/amenity-bookings")
                        .header("Authorization", "Bearer " + residentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[?(@.id == '" + ownBookingId + "')]").exists());
    }

    // =========================================================================
    // Test 10 — GET /api/amenity-bookings (RESIDENT) cannot see another resident's bookings
    // =========================================================================

    /**
     * Verifies resident A cannot see resident B's bookings (server-side scoping / IDOR prevention).
     */
    @Test
    @DisplayName("GET /api/amenity-bookings — RESIDENT cannot see another resident's bookings")
    void listBookings_residentCannotSeeOtherResidentBookings() throws Exception {
        // Create resident B with their own apartment and booking.
        String bUid = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        UUID bBlockId = createBlock("BBlock-" + bUid);
        UUID bAptId = createApartment(bBlockId, "B-" + bUid);
        assignResident(phoneFromUid(bUid), bAptId);
        String bToken = login(phoneFromUid(bUid), "Password@123456");

        // Resident B creates a booking on a different amenity (bbq — unique per setUp).
        CreateBookingRequest bReq = buildBookingRequest(
                bbqAmenityId, BOOKING_DATE, LocalTime.of(9, 0), LocalTime.of(10, 0));
        MvcResult bResult = mockMvc.perform(post("/api/amenity-bookings")
                        .header("Authorization", "Bearer " + bToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bReq)))
                .andExpect(status().isCreated())
                .andReturn();
        String bBookingId = (String) objectMapper.readValue(
                bResult.getResponse().getContentAsString(), Map.class).get("id");

        // Resident A lists their own bookings — must NOT contain resident B's booking.
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/amenity-bookings")
                        .header("Authorization", "Bearer " + residentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id == '" + bBookingId + "')]").doesNotExist());
    }

    // =========================================================================
    // Test 11 — GET /api/amenity-bookings (RESIDENT + residentId param) → own only (IDOR guard)
    // =========================================================================

    /**
     * Verifies that a RESIDENT passing another resident's residentId query param
     * still receives only their own bookings (server-side override, IDOR prevention).
     */
    @Test
    @DisplayName("GET /api/amenity-bookings — RESIDENT passing other residentId → still sees own only")
    void listBookings_residentPassingOtherResidentId_seesOnlyOwn() throws Exception {
        // Create resident B.
        String bUid = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        UUID bBlockId = createBlock("BBlock2-" + bUid);
        UUID bAptId = createApartment(bBlockId, "B2-" + bUid);
        assignResident(phoneFromUid(bUid), bAptId);
        String bToken = login(phoneFromUid(bUid), "Password@123456");

        // Resident B creates a booking.
        CreateBookingRequest bReq = buildBookingRequest(
                bbqAmenityId, BOOKING_DATE, LocalTime.of(10, 0), LocalTime.of(11, 0));
        MvcResult bResult = mockMvc.perform(post("/api/amenity-bookings")
                        .header("Authorization", "Bearer " + bToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bReq)))
                .andExpect(status().isCreated())
                .andReturn();
        Map<?, ?> bBooking = objectMapper.readValue(
                bResult.getResponse().getContentAsString(), Map.class);
        String bBookingId = (String) bBooking.get("id");
        // Extract B's residentId from the booking response.
        Map<?, ?> bResident = (Map<?, ?>) bBooking.get("resident");
        String bResidentId = (String) bResident.get("id");

        // Resident A passes B's residentId — server must ignore it and scope to A's own bookings.
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/amenity-bookings")
                        .param("residentId", bResidentId)
                        .header("Authorization", "Bearer " + residentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id == '" + bBookingId + "')]").doesNotExist());
    }

    // =========================================================================
    // Test 12 — GET /api/amenity-bookings (ADMIN) → sees all bookings
    // =========================================================================

    /**
     * Verifies that an ADMIN can list all amenity bookings across all residents.
     */
    @Test
    @DisplayName("GET /api/amenity-bookings — ADMIN sees all bookings")
    void listBookings_adminSeesAllBookings() throws Exception {
        // Resident A booking.
        CreateBookingRequest aReq = buildBookingRequest(
                gymAmenityId, BOOKING_DATE, LocalTime.of(8, 0), LocalTime.of(9, 0));
        MvcResult aResult = mockMvc.perform(post("/api/amenity-bookings")
                        .header("Authorization", "Bearer " + residentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(aReq)))
                .andExpect(status().isCreated())
                .andReturn();
        String aBookingId = (String) objectMapper.readValue(
                aResult.getResponse().getContentAsString(), Map.class).get("id");

        // Resident B booking.
        String bUid = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        UUID bBlockId = createBlock("BBlock3-" + bUid);
        UUID bAptId = createApartment(bBlockId, "B3-" + bUid);
        assignResident(phoneFromUid(bUid), bAptId);
        String bToken = login(phoneFromUid(bUid), "Password@123456");

        CreateBookingRequest bReq = buildBookingRequest(
                bbqAmenityId, BOOKING_DATE, LocalTime.of(11, 0), LocalTime.of(12, 0));
        MvcResult bResult = mockMvc.perform(post("/api/amenity-bookings")
                        .header("Authorization", "Bearer " + bToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bReq)))
                .andExpect(status().isCreated())
                .andReturn();
        String bBookingId = (String) objectMapper.readValue(
                bResult.getResponse().getContentAsString(), Map.class).get("id");

        // ADMIN lists bookings — must see both residents' bookings. Filtered per
        // amenity: the unfiltered list is unsorted and the shared dev DB accumulates
        // committed bookings across runs, so page membership of a fresh booking is
        // not deterministic (de-flaked 2026-06-11; was a page-100 lottery).
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/amenity-bookings")
                        .param("amenityId", gymAmenityId.toString())
                        .param("size", "100")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id == '" + aBookingId + "')]").exists());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/amenity-bookings")
                        .param("amenityId", bbqAmenityId.toString())
                        .param("size", "100")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id == '" + bBookingId + "')]").exists());
    }

    // =========================================================================
    // Test 6 — POST /api/amenity-bookings past date → 400
    // =========================================================================

    /**
     * SEC-22 regression: booking date in the past must be rejected.
     */
    @Test
    @DisplayName("POST /api/amenity-bookings — past booking date → 400 BAD_REQUEST (SEC-22)")
    void createBooking_pastDate_returns400() throws Exception {
        CreateBookingRequest req = buildBookingRequest(
                gymAmenityId,
                LocalDate.now().minusDays(1),
                LocalTime.of(10, 0),
                LocalTime.of(11, 0));

        mockMvc.perform(post("/api/amenity-bookings")
                        .header("Authorization", "Bearer " + residentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    // =========================================================================
    // Test 7 — POST /api/amenity-bookings too far in advance → 400
    // =========================================================================

    /**
     * SEC-12 regression: booking more than 14 days in advance must be rejected.
     */
    @Test
    @DisplayName("POST /api/amenity-bookings — date > 14 days ahead → 400 BAD_REQUEST (SEC-12)")
    void createBooking_tooFarInAdvance_returns400() throws Exception {
        CreateBookingRequest req = buildBookingRequest(
                gymAmenityId,
                LocalDate.now().plusDays(15),
                LocalTime.of(10, 0),
                LocalTime.of(11, 0));

        mockMvc.perform(post("/api/amenity-bookings")
                        .header("Authorization", "Bearer " + residentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    // =========================================================================
    // Test 8 — POST /api/amenity-bookings duration < 30 min → 400
    // =========================================================================

    /**
     * SEC-11 regression: booking duration less than 30 minutes must be rejected.
     */
    @Test
    @DisplayName("POST /api/amenity-bookings — duration < 30 min → 400 BAD_REQUEST (SEC-11)")
    void createBooking_durationTooShort_returns400() throws Exception {
        CreateBookingRequest req = buildBookingRequest(
                gymAmenityId,
                BOOKING_DATE,
                LocalTime.of(10, 0),
                LocalTime.of(10, 20));  // only 20 minutes

        mockMvc.perform(post("/api/amenity-bookings")
                        .header("Authorization", "Bearer " + residentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    // =========================================================================
    // Helper methods
    // =========================================================================

    /**
     * Builds a {@link CreateBookingRequest} for the given amenity and time slot.
     *
     * @param amenityId   the amenity UUID.
     * @param bookingDate the booking date.
     * @param start       the slot start time.
     * @param end         the slot end time.
     * @return the populated request.
     */
    private CreateBookingRequest buildBookingRequest(UUID amenityId, LocalDate bookingDate,
                                                      LocalTime start, LocalTime end) {
        CreateBookingRequest req = new CreateBookingRequest();
        req.setAmenityId(amenityId);
        req.setBookingDate(bookingDate);
        req.setStartTime(start);
        req.setEndTime(end);
        return req;
    }

    /**
     * Derives a deterministic 10-digit VN phone from an 8-char hex uid.
     *
     * @param uid 8-character lowercase hexadecimal string (from UUID).
     * @return a 10-digit VN phone starting with "090".
     */
    private static String phoneFromUid(String uid) {
        long num = Long.parseLong(uid.substring(0, 7), 16) % 9_000_000L + 1_000_000L;
        return "090" + num;
    }

    // =========================================================================
    // Test 13 — POST /api/amenities dup name → 409 AMENITY_NAME_EXISTS
    // =========================================================================

    /**
     * Verifies that creating an amenity with a duplicate name returns 409 with AMENITY_NAME_EXISTS.
     */
    @Test
    @DisplayName("POST /api/amenities — duplicate name → 409 AMENITY_NAME_EXISTS")
    void createAmenity_dupName_returns409AmenityNameExists() throws Exception {
        String uid = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String name = "DupAmenity-" + uid;
        createAmenity(name, false, (short) 1);

        CreateAmenityRequest req = new CreateAmenityRequest();
        req.setName(name);
        req.setDescription("duplicate");
        req.setLocation("Floor 1");
        req.setOpeningTime(LocalTime.of(8, 0));
        req.setClosingTime(LocalTime.of(22, 0));
        req.setMaxDailyBookingsPerResident((short) 1);
        req.setRequiresApproval(false);

        mockMvc.perform(post("/api/amenities")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("AMENITY_NAME_EXISTS"));
    }

    // =========================================================================
    // Test 14 — PUT /api/amenities/{id} dup name → 409 AMENITY_NAME_EXISTS
    // =========================================================================

    /**
     * Verifies that renaming an amenity to an existing name returns 409 with AMENITY_NAME_EXISTS.
     */
    @Test
    @DisplayName("PUT /api/amenities/{id} — rename to existing name → 409 AMENITY_NAME_EXISTS")
    void updateAmenity_dupName_returns409AmenityNameExists() throws Exception {
        String uid = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String nameA = "DupA-" + uid;
        String nameB = "DupB-" + uid;
        createAmenity(nameA, false, (short) 1);
        UUID amenityB = createAmenity(nameB, false, (short) 1);

        Map<String, Object> req = new HashMap<>();
        req.put("name", nameA);

        mockMvc.perform(put("/api/amenities/" + amenityB)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("AMENITY_NAME_EXISTS"));
    }

    // =========================================================================
    // Test 15 — PUT /api/amenity-bookings/{id}/approve not-PENDING → 409 BOOKING_NOT_PENDING
    // =========================================================================

    /**
     * Verifies that approving an already-approved booking returns 409 with BOOKING_NOT_PENDING.
     */
    @Test
    @DisplayName("PUT /api/amenity-bookings/{id}/approve — booking not PENDING → 409 BOOKING_NOT_PENDING")
    void approveBooking_notPending_returns409BookingNotPending() throws Exception {
        CreateBookingRequest booking = buildBookingRequest(
                bbqAmenityId, BOOKING_DATE, LocalTime.of(10, 0), LocalTime.of(11, 0));
        MvcResult result = mockMvc.perform(post("/api/amenity-bookings")
                        .header("Authorization", "Bearer " + residentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(booking)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn();
        String bookingId = (String) objectMapper.readValue(
                result.getResponse().getContentAsString(), Map.class).get("id");

        ApproveRejectRequest approveReq = new ApproveRejectRequest();
        approveReq.setStatus(BookingStatus.APPROVED);

        mockMvc.perform(put("/api/amenity-bookings/" + bookingId + "/approve")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(approveReq)))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/amenity-bookings/" + bookingId + "/approve")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(approveReq)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("BOOKING_NOT_PENDING"));
    }
}
