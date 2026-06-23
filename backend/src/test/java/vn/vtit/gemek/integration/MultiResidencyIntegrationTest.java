/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.integration;
import vn.vtit.gemek.support.AbstractIntegrationTest;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import vn.vtit.gemek.common.storage.FileStorageService;
import vn.vtit.gemek.module.amenity.dto.CreateAmenityRequest;
import vn.vtit.gemek.module.amenity.dto.CreateBookingRequest;
import vn.vtit.gemek.module.announcement.dto.CreateAnnouncementRequest;
import vn.vtit.gemek.module.announcement.entity.AnnouncementScope;
import vn.vtit.gemek.module.announcement.entity.AnnouncementType;
import vn.vtit.gemek.module.apartment.dto.CreateApartmentRequest;
import vn.vtit.gemek.module.apartment.dto.CreateBlockRequest;
import vn.vtit.gemek.module.apartment.entity.Apartment;
import vn.vtit.gemek.module.apartment.repository.ApartmentRepository;
import vn.vtit.gemek.module.auth.dto.LoginRequest;
import vn.vtit.gemek.module.resident.entity.Resident;
import vn.vtit.gemek.module.resident.entity.ResidentType;
import vn.vtit.gemek.module.resident.repository.ResidentRepository;
import vn.vtit.gemek.module.ticket.dto.CreateTicketRequest;
import vn.vtit.gemek.module.ticket.entity.TicketCategory;
import vn.vtit.gemek.module.user.entity.User;
import vn.vtit.gemek.module.user.repository.UserRepository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P2 multi-residency integration proofs — run AFTER V20 relaxed
 * {@code uq_residents_active_user} from {@code (user_id)} to {@code (user_id, apartment_id)}.
 *
 * <p>These tests construct a genuine 2-active-residency state the NORMAL way — a second
 * {@code residents} row is persisted through the real repository, hitting the live partial
 * unique index (NOT bypassing it). Under the old index this INSERT threw a unique violation;
 * under V20 it succeeds. The tests then re-exercise the HIGH P1 surfaces under that real state,
 * proving the P1 sweep holds once two active rows actually exist.
 *
 * <p>Covers:
 * <ol>
 *   <li>Two active residencies in two DIFFERENT apartments persist; a second active row for the
 *       SAME (user, apartment) pair is still rejected (the relaxed index still guards the real
 *       invariant).</li>
 *   <li>{@code GET /residents/me} returns BOTH residencies.</li>
 *   <li>Ticket per-context ownership allows each resided apartment, denies a third non-resided one;
 *       {@code GET /tickets?mine=true} includes both apartments' tickets.</li>
 *   <li>Announcement feed returns each announcement at most once.</li>
 *   <li>Amenity primary-or-latest selection is deterministic and does not throw.</li>
 * </ol>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class MultiResidencyIntegrationTest extends AbstractIntegrationTest {

    /** Mock MinIO — not started in this test suite. */
    @MockBean
    private FileStorageService fileStorageService;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ResidentRepository residentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ApartmentRepository apartmentRepository;

    private String adminToken;

    private static final String ADMIN_PHONE    = "0900000000";
    private static final String ADMIN_PASSWORD = "GemekAdmin2026";
    private static final String RESIDENT_PASSWORD = "Password@123456";
    private static final LocalDate BOOKING_DATE = LocalDate.now().plusDays(7);

    @BeforeEach
    void setUp() throws Exception {
        adminToken = login(ADMIN_PHONE, ADMIN_PASSWORD);
    }

    // =========================================================================
    // Test 1 — the payoff: two active rows in DIFFERENT apartments now persist;
    //          a duplicate active row for the SAME (user, apartment) is still rejected.
    // =========================================================================

    @Test
    @DisplayName("V20: 2 active residencies in different apartments persist; same (user,apartment) pair still rejected")
    void twoActiveResidenciesDifferentApartments_persist_sameApartmentPairRejected() throws Exception {
        Fixture fx = setUpUserWithTwoResidencies();

        // Payoff: the SECOND active row (already inserted by the fixture via the real index) succeeded.
        List<Resident> active = residentRepository.findAllActiveByUserId(fx.userId);
        Assertions.assertEquals(2, active.size(),
                "User must hold exactly 2 active residencies after the relaxed index allowed the 2nd insert");

        // The relaxed index STILL guards the real invariant: a 2nd active row for the SAME
        // (user, apartment) pair must violate the unique index. Asserted LAST — the failed flush
        // marks the test transaction rollback-only; the method then ends and rolls back cleanly.
        Resident duplicate = newActiveResident(fx.userId, fx.apartmentAId);
        Assertions.assertThrows(DataIntegrityViolationException.class,
                () -> residentRepository.saveAndFlush(duplicate),
                "A second ACTIVE residency for the same (user, apartment) pair must still be rejected");
    }

    // =========================================================================
    // Test 2 — /residents/me returns BOTH residencies under the genuine 2-active state.
    // =========================================================================

    @Test
    @DisplayName("V20: GET /residents/me returns BOTH active residencies")
    void residentsMe_returnsBothResidencies() throws Exception {
        Fixture fx = setUpUserWithTwoResidencies();
        String token = login(fx.phone, RESIDENT_PASSWORD);

        MvcResult result = mockMvc.perform(get("/api/residents/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andReturn();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> body = objectMapper.readValue(
                result.getResponse().getContentAsString(), List.class);
        java.util.Set<String> aptIds = new java.util.HashSet<>();
        for (Map<String, Object> row : body) {
            @SuppressWarnings("unchecked")
            Map<String, Object> apartment = (Map<String, Object>) row.get("apartment");
            aptIds.add((String) apartment.get("id"));
        }
        Assertions.assertTrue(aptIds.contains(fx.apartmentAId.toString()), "feed must include apartment A");
        Assertions.assertTrue(aptIds.contains(fx.apartmentBId.toString()), "feed must include apartment B");
    }

    // =========================================================================
    // Test 3 — ticket per-context ownership + mine-scope across both apartments.
    // =========================================================================

    @Test
    @DisplayName("V20: ticket per-context allows each resided apartment, denies a third; mine returns both")
    void ticketPerContext_allowsResided_deniesThird_mineReturnsBoth() throws Exception {
        Fixture fx = setUpUserWithTwoResidencies();
        // A third apartment the user does NOT reside in.
        UUID blockC = createBlock("MRBlockC-" + fx.uid);
        UUID apartmentC = createApartment(blockC, "MRC-" + fx.uid);
        String token = login(fx.phone, RESIDENT_PASSWORD);

        String ticketA = createTicket(token, fx.apartmentAId, "Ticket in A");
        String ticketB = createTicket(token, fx.apartmentBId, "Ticket in B");

        // Per-context guard: a non-resided apartment must be denied.
        CreateTicketRequest reqC = CreateTicketRequest.builder()
                .apartmentId(apartmentC).category(TicketCategory.OTHER).title("Ticket in C").build();
        mockMvc.perform(post("/api/tickets")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqC)))
                .andExpect(status().isForbidden());

        // RESIDENT default scope (ALL semantic): tickets of EVERY actively-resided apartment.
        // (The `mine` request param is the technician "assigned-to-me" filter, not the resident
        // household scope — a RESIDENT's default list is already scoped to their active apartments.)
        MvcResult mine = mockMvc.perform(get("/api/tickets")
                        .param("size", "100")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        String mineJson = mine.getResponse().getContentAsString();
        Assertions.assertTrue(mineJson.contains(ticketA), "mine list must include the ticket raised in apartment A");
        Assertions.assertTrue(mineJson.contains(ticketB), "mine list must include the ticket raised in apartment B");
    }

    // =========================================================================
    // Test 4 — announcement feed returns each announcement at most once.
    // =========================================================================

    @Test
    @DisplayName("V20: announcement feed returns an ALL-scope announcement exactly once for a 2-residency user")
    void announcementFeed_noDuplicateForTwoResidencies() throws Exception {
        Fixture fx = setUpUserWithTwoResidencies();
        String token = login(fx.phone, RESIDENT_PASSWORD);

        String announcementId = createAndPublishAllAnnouncement("MR-ALL-" + fx.uid);

        MvcResult feed = mockMvc.perform(get("/api/announcements")
                        .param("size", "100")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        @SuppressWarnings("unchecked")
        Map<String, Object> page = objectMapper.readValue(feed.getResponse().getContentAsString(), Map.class);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) page.get("data");
        long occurrences = items.stream()
                .filter(a -> announcementId.equals(a.get("id")))
                .count();
        Assertions.assertEquals(1, occurrences,
                "an ALL-scope announcement must appear exactly once even for a user with 2 active residencies");
    }

    // =========================================================================
    // Test 5 — amenity primary-or-latest selection is deterministic and does not throw.
    // =========================================================================

    @Test
    @DisplayName("V20: amenity booking resolves primary-or-latest residency deterministically (no NonUniqueResult)")
    void amenityBooking_primaryOrLatest_noThrow() throws Exception {
        Fixture fx = setUpUserWithTwoResidencies();
        String token = login(fx.phone, RESIDENT_PASSWORD);
        UUID gymAmenityId = createAmenity("MRGym-" + fx.uid);

        CreateBookingRequest req = new CreateBookingRequest();
        req.setAmenityId(gymAmenityId);
        req.setBookingDate(BOOKING_DATE);
        req.setStartTime(LocalTime.of(10, 0));
        req.setEndTime(LocalTime.of(11, 0));

        mockMvc.perform(post("/api/amenity-bookings")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.id").isNotEmpty());
    }

    // =========================================================================
    // Fixture + helpers
    // =========================================================================

    /** Holder for a user with two active residencies (apartment A via the service, apartment B via the repo). */
    private static final class Fixture {
        final String uid;
        final String phone;
        final UUID userId;
        final UUID apartmentAId;
        final UUID apartmentBId;

        Fixture(String uid, String phone, UUID userId, UUID apartmentAId, UUID apartmentBId) {
            this.uid = uid;
            this.phone = phone;
            this.userId = userId;
            this.apartmentAId = apartmentAId;
            this.apartmentBId = apartmentBId;
        }
    }

    /**
     * Creates one user with TWO active residencies in two different apartments. Apartment A is
     * created through the real {@code POST /residents} path (creates the user); apartment B's active
     * residency is persisted through the real repository — this is the INSERT that previously violated
     * {@code uq_residents_active_user} and now succeeds under V20.
     *
     * @return the fixture identifiers.
     * @throws Exception on MockMvc failure.
     */
    private Fixture setUpUserWithTwoResidencies() throws Exception {
        String uid = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String phone = phoneFromUid(uid);
        UUID blockA = createBlock("MRBlockA-" + uid);
        UUID apartmentA = createApartment(blockA, "MRA-" + uid);
        UUID blockB = createBlock("MRBlockB-" + uid);
        UUID apartmentB = createApartment(blockB, "MRB-" + uid);

        assignResident(phone, apartmentA);
        UUID userId = userRepository.findByPhone(phone)
                .orElseThrow(() -> new IllegalStateException("seeded resident user not found: " + phone))
                .getId();

        // Second active residency via the real repository → hits the relaxed unique index for real.
        residentRepository.saveAndFlush(newActiveResident(userId, apartmentB));
        return new Fixture(uid, phone, userId, apartmentA, apartmentB);
    }

    /** Builds a fresh ACTIVE (move_out_date null) resident row for the given user + apartment. */
    private Resident newActiveResident(UUID userId, UUID apartmentId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("user not found: " + userId));
        Apartment apartment = apartmentRepository.findById(apartmentId)
                .orElseThrow(() -> new IllegalStateException("apartment not found: " + apartmentId));
        OffsetDateTime now = OffsetDateTime.now();
        Resident resident = new Resident();
        resident.setUser(user);
        resident.setApartment(apartment);
        resident.setType(ResidentType.OWNER);
        resident.setMoveInDate(LocalDate.now());
        resident.setPrimaryContact(false);
        resident.setCreatedAt(now);
        resident.setUpdatedAt(now);
        return resident;
    }

    private static String phoneFromUid(String uid) {
        long num = Long.parseLong(uid.substring(0, 7), 16) % 9_000_000L + 1_000_000L;
        return "090" + num;
    }

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

    private void assignResident(String phone, UUID apartmentId) throws Exception {
        Map<String, Object> req = new HashMap<>();
        req.put("fullName", "Test Resident");
        req.put("phone", phone);
        req.put("dateOfBirth", "1990-01-01");
        req.put("password", RESIDENT_PASSWORD);
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

    private String createTicket(String token, UUID apartmentId, String title) throws Exception {
        CreateTicketRequest req = CreateTicketRequest.builder()
                .apartmentId(apartmentId).category(TicketCategory.OTHER).title(title).build();
        MvcResult result = mockMvc.perform(post("/api/tickets")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();
        Map<?, ?> body = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
        return (String) body.get("id");
    }

    private String createAndPublishAllAnnouncement(String title) throws Exception {
        CreateAnnouncementRequest req = new CreateAnnouncementRequest();
        req.setTitle(title);
        req.setContent("Multi-residency announcement feed proof.");
        req.setType(AnnouncementType.GENERAL);
        req.setTargetScope(AnnouncementScope.ALL);
        MvcResult created = mockMvc.perform(post("/api/announcements")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();
        String id = (String) objectMapper.readValue(created.getResponse().getContentAsString(), Map.class).get("id");
        mockMvc.perform(post("/api/announcements/" + id + "/publish")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
        return id;
    }

    private UUID createAmenity(String name) throws Exception {
        CreateAmenityRequest req = new CreateAmenityRequest();
        req.setName(name);
        req.setDescription("Test amenity");
        req.setLocation("Test floor");
        req.setOpeningTime(LocalTime.of(8, 0));
        req.setClosingTime(LocalTime.of(22, 0));
        req.setMaxDailyBookingsPerResident((short) 5);
        req.setRequiresApproval(false);
        MvcResult result = mockMvc.perform(post("/api/amenities")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();
        Map<?, ?> body = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
        return UUID.fromString((String) body.get("id"));
    }
}
