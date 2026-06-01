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
    private UUID residentUserId;

    /** Amenity seeded by V3: "Gym / Fitness Center" — requiresApproval=false. */
    private UUID gymAmenityId;
    /** Amenity seeded by V3: "BBQ Area" — requiresApproval=true. */
    private UUID bbqAmenityId;

    /**
     * Unique booking base date for this test instance, within the 14-day advance window (SEC-12).
     */
    private LocalDate baseBookingDate;

    /**
     * Base hour for Gym slots in this test instance (7-19, leaving room for a 1h slot within 7-22).
     * Combined with baseBookingDate to guarantee no two test instances book the same date+time.
     */
    private int baseHour;

    // Slot uniqueness is managed by the JVM-wide TestSlotCounter shared with AmenityBookingIntegrationTest.

    private static final String ADMIN_EMAIL    = "admin@gemek.vn";
    private static final String ADMIN_PASSWORD = "Admin@123456";

    /**
     * Authenticates admin, creates a resident user and apartment, and resolves the
     * seeded amenity IDs from the amenity list endpoint.
     */
    @BeforeEach
    void setUp() throws Exception {
        adminToken = login(ADMIN_EMAIL, ADMIN_PASSWORD);

        // Each invocation gets a unique (day, hour) slot to avoid cross-test conflicts on shared amenities.
        // 15 valid start hours (7-21); 13 valid day offsets (1-13) → 195 unique combinations.
        int counter = vn.vtit.gemek.TestSlotCounter.next();
        int dayOffset = (counter % 13) + 1;
        // hours 10-20: fits both BBQ (opens 10:00) and Gym (opens 05:30); max used is baseHour+2 <= 22.
        baseHour = 10 + (counter % 11);
        baseBookingDate = LocalDate.now().plusDays(dayOffset);

        // Create a unique resident user per test instance (nanoTime suffix avoids constraint violations).
        String residentEmail = "amenity.resident." + System.nanoTime() + "@test.com";
        residentUserId = createUser(residentEmail, UserRole.RESIDENT);
        UUID blockId = createBlock("AmenityBlock-" + System.nanoTime());
        UUID apartmentId = createApartment(blockId, "AM" + System.nanoTime());
        assignResident(residentUserId, apartmentId);
        residentToken = login(residentEmail, "Password@123456");

        // Resolve amenity IDs from the seeded data.
        gymAmenityId = resolveAmenityByName("Gym / Fitness Center");
        bbqAmenityId = resolveAmenityByName("BBQ Area");
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
                baseBookingDate,
                LocalTime.of(baseHour, 0),
                LocalTime.of(baseHour + 1, 0));

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
        LocalDate bookingDate = baseBookingDate;
        CreateBookingRequest first = buildBookingRequest(
                gymAmenityId, bookingDate, LocalTime.of(baseHour, 0), LocalTime.of(baseHour + 1, 0));

        // Create a second resident to book the same slot — should conflict.
        String ovUid = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String secondResidentEmail = "overlap.resident." + ovUid + "@test.com";
        UUID secondUserId = createUser(secondResidentEmail, UserRole.RESIDENT);
        UUID blockId2 = createBlock("OvBlock-" + ovUid);
        UUID apt2 = createApartment(blockId2, "OV-" + ovUid);
        assignResident(secondUserId, apt2);
        String secondToken = login(secondResidentEmail, "Password@123456");

        // First booking succeeds.
        mockMvc.perform(post("/api/amenity-bookings")
                        .header("Authorization", "Bearer " + residentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(first)))
                .andExpect(status().isCreated());

        // Second booking on the SAME slot by a different resident must conflict.
        CreateBookingRequest second = buildBookingRequest(
                gymAmenityId, bookingDate, LocalTime.of(baseHour, 0), LocalTime.of(baseHour + 1, 0));

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
        // Use baseBookingDate; slots 14:00-15:00 and 15:00-16:00 are unique to this test.
        LocalDate bookingDate = baseBookingDate;

        // First booking uses baseHour slot (unique to this instance).
        CreateBookingRequest first = buildBookingRequest(
                gymAmenityId, bookingDate, LocalTime.of(baseHour, 0), LocalTime.of(baseHour + 1, 0));
        mockMvc.perform(post("/api/amenity-bookings")
                        .header("Authorization", "Bearer " + residentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(first)))
                .andExpect(status().isCreated());

        // Second booking same date, non-overlapping time — per-resident daily limit is 1 so must fail.
        // Use baseHour+1 to avoid slot collision while still being on the same date.
        CreateBookingRequest second = buildBookingRequest(
                gymAmenityId, bookingDate, LocalTime.of(baseHour + 1, 0), LocalTime.of(baseHour + 2, 0));
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
        // BBQ uses baseHour slot — different amenity so no conflict with Gym bookings.
        LocalDate bookingDate = baseBookingDate;
        CreateBookingRequest req = buildBookingRequest(
                bbqAmenityId, bookingDate, LocalTime.of(baseHour, 0), LocalTime.of(baseHour + 1, 0));

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
        // BBQ uses baseHour slot — different amenity so no conflict with Gym bookings.
        LocalDate bookingDate = baseBookingDate;
        CreateBookingRequest req = buildBookingRequest(
                bbqAmenityId, bookingDate, LocalTime.of(baseHour, 0), LocalTime.of(baseHour + 1, 0));

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
     * @param email    user email.
     * @param password user password.
     * @return the JWT access token.
     */
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

    /**
     * Assigns a user as an OWNER resident of the given apartment.
     *
     * @param userId      the user UUID.
     * @param apartmentId the apartment UUID.
     */
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
}
