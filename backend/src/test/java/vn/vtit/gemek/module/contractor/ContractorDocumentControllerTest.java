/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.module.contractor;
import vn.vtit.gemek.support.AbstractIntegrationTest;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import vn.vtit.gemek.common.storage.FileStorageService;
import vn.vtit.gemek.module.auth.dto.LoginRequest;
import vn.vtit.gemek.module.contractor.dto.CreateContractorRequest;
import vn.vtit.gemek.module.contractor.entity.ContractorSpecialty;
import vn.vtit.gemek.module.user.dto.CreateUserRequest;
import vn.vtit.gemek.module.user.entity.UserRole;

import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.anyString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP role-gate + status integration tests for the contractor-document endpoints
 * ({@code /api/contractors/{id}/documents}). MinIO is mocked. Proves the staff-only matrix
 * (ADMIN uploads/deletes; ADMIN+BOARD read; TECHNICIAN/RESIDENT denied), the forced-download URL on
 * the list, and coded 413 / 400 / 404 paths. Deep type/cap logic lives in
 * {@link ContractorDocumentServiceIntegrationTest}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ContractorDocumentControllerTest extends AbstractIntegrationTest {

    @MockBean
    private FileStorageService fileStorageService;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private static final String ADMIN_PHONE    = "0900000000";
    private static final String ADMIN_PASSWORD = "GemekAdmin2026";
    private static final String STAFF_PASSWORD = "Password@123456";

    /** Minimal valid PDF — %PDF magic makes Tika detect application/pdf. */
    private static final byte[] PDF_BYTES =
            "%PDF-1.4\n1 0 obj<<>>endobj\ntrailer<<>>\n%%EOF".getBytes();

    private String adminToken;

    @BeforeEach
    void setUp() throws Exception {
        adminToken = login(ADMIN_PHONE, ADMIN_PASSWORD);
        // Forced-download presign returns a URL embedding the (signed) disposition so the list test can
        // assert the forced-download contract without a live MinIO.
        org.mockito.Mockito.when(fileStorageService.presign(anyString(), anyString(), anyString()))
                .thenAnswer(inv -> "https://minio.local/o?response-content-disposition=" + inv.getArgument(1)
                        + "&response-content-type=" + inv.getArgument(2));
    }

    // =========================================================================
    // Upload — ADMIN ok; BOARD/TECHNICIAN/RESIDENT forbidden
    // =========================================================================

    @Test
    @DisplayName("POST documents — ADMIN uploads a PDF (201); list shows it with a forced-download URL")
    void upload_admin_returns201_andListShowsForcedDownload() throws Exception {
        UUID contractorId = createContractor();
        MockMultipartFile file = new MockMultipartFile("file", "hợp đồng.pdf", "application/pdf", PDF_BYTES);

        mockMvc.perform(multipart("/api/contractors/" + contractorId + "/documents")
                        .file(file)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.displayFilename").value("hợp đồng.pdf"))
                .andExpect(jsonPath("$.contentType").value("application/pdf"));

        mockMvc.perform(get("/api/contractors/" + contractorId + "/documents")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].displayFilename").value("hợp đồng.pdf"))
                .andExpect(jsonPath("$[0].downloadUrl", startsWith("https://")))
                .andExpect(jsonPath("$[0].downloadUrl",
                        org.hamcrest.Matchers.containsString("response-content-disposition=attachment")));
    }

    @Test
    @DisplayName("POST documents — BOARD_MEMBER is forbidden (403)")
    void upload_boardMember_returns403() throws Exception {
        UUID contractorId = createContractor();
        String boardToken = createStaffAndLogin(UserRole.BOARD_MEMBER);
        mockMvc.perform(multipart("/api/contractors/" + contractorId + "/documents")
                        .file(new MockMultipartFile("file", "x.pdf", "application/pdf", PDF_BYTES))
                        .header("Authorization", "Bearer " + boardToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST documents — TECHNICIAN is forbidden (403)")
    void upload_technician_returns403() throws Exception {
        UUID contractorId = createContractor();
        String techToken = createStaffAndLogin(UserRole.TECHNICIAN);
        mockMvc.perform(multipart("/api/contractors/" + contractorId + "/documents")
                        .file(new MockMultipartFile("file", "x.pdf", "application/pdf", PDF_BYTES))
                        .header("Authorization", "Bearer " + techToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST documents — RESIDENT is forbidden (403)")
    void upload_resident_returns403() throws Exception {
        UUID contractorId = createContractor();
        String residentToken = createStaffAndLogin(UserRole.RESIDENT);
        mockMvc.perform(multipart("/api/contractors/" + contractorId + "/documents")
                        .file(new MockMultipartFile("file", "x.pdf", "application/pdf", PDF_BYTES))
                        .header("Authorization", "Bearer " + residentToken))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // List gate — ADMIN/BOARD 200; TECHNICIAN/RESIDENT 403
    // =========================================================================

    @Test
    @DisplayName("GET documents — BOARD_MEMBER 200; TECHNICIAN 403; RESIDENT 403")
    void list_roleGate() throws Exception {
        UUID contractorId = createContractor();
        uploadPdf(contractorId);

        mockMvc.perform(get("/api/contractors/" + contractorId + "/documents")
                        .header("Authorization", "Bearer " + createStaffAndLogin(UserRole.BOARD_MEMBER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));

        mockMvc.perform(get("/api/contractors/" + contractorId + "/documents")
                        .header("Authorization", "Bearer " + createStaffAndLogin(UserRole.TECHNICIAN)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/contractors/" + contractorId + "/documents")
                        .header("Authorization", "Bearer " + createStaffAndLogin(UserRole.RESIDENT)))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // Delete — ADMIN 204 (row gone); BOARD 403
    // =========================================================================

    @Test
    @DisplayName("DELETE documents — ADMIN removes the row (204); BOARD_MEMBER is forbidden (403)")
    void delete_adminOk_boardForbidden() throws Exception {
        UUID contractorId = createContractor();
        String documentId = uploadPdf(contractorId);

        mockMvc.perform(delete("/api/contractors/" + contractorId + "/documents/" + documentId)
                        .header("Authorization", "Bearer " + createStaffAndLogin(UserRole.BOARD_MEMBER)))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/contractors/" + contractorId + "/documents/" + documentId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/contractors/" + contractorId + "/documents")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    // =========================================================================
    // Coded error paths — oversize 413, disallowed type 400, unknown contractor 404
    // =========================================================================

    @Test
    @DisplayName("POST documents — a file over 10MB is rejected with coded 413, no row")
    void upload_oversize_returns413_noRow() throws Exception {
        UUID contractorId = createContractor();
        byte[] big = new byte[10 * 1024 * 1024 + 1];
        System.arraycopy(PDF_BYTES, 0, big, 0, PDF_BYTES.length);

        mockMvc.perform(multipart("/api/contractors/" + contractorId + "/documents")
                        .file(new MockMultipartFile("file", "big.pdf", "application/pdf", big))
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.error").value("CONTRACTOR_DOCUMENT_TOO_LARGE"));

        mockMvc.perform(get("/api/contractors/" + contractorId + "/documents")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("POST documents — a renderable type (HTML renamed .pdf) is rejected (400), no row")
    void upload_html_returns400_noRow() throws Exception {
        UUID contractorId = createContractor();
        byte[] html = "<!DOCTYPE html><html><body><script>1</script></body></html>".getBytes();

        mockMvc.perform(multipart("/api/contractors/" + contractorId + "/documents")
                        .file(new MockMultipartFile("file", "evil.pdf", "application/pdf", html))
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("CONTRACTOR_DOCUMENT_TYPE_NOT_ALLOWED"));

        mockMvc.perform(get("/api/contractors/" + contractorId + "/documents")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("POST documents — unknown contractor → 404")
    void upload_unknownContractor_returns404() throws Exception {
        mockMvc.perform(multipart("/api/contractors/" + UUID.randomUUID() + "/documents")
                        .file(new MockMultipartFile("file", "x.pdf", "application/pdf", PDF_BYTES))
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    // =========================================================================
    // Helpers
    // =========================================================================

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

    /** Creates a fresh staff/resident user of the given role and returns its JWT token. */
    private String createStaffAndLogin(UserRole role) throws Exception {
        String phone = "09" + String.format("%08d", Math.floorMod(System.nanoTime(), 100_000_000L));
        CreateUserRequest req = new CreateUserRequest(null, role + " User", phone, role, STAFF_PASSWORD);
        mockMvc.perform(post("/api/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());
        return login(phone, STAFF_PASSWORD);
    }

    private UUID createContractor() throws Exception {
        CreateContractorRequest req = new CreateContractorRequest(
                "Doc Co " + System.nanoTime(), "Contact", "0900000001",
                "c@" + System.nanoTime() + ".vn", "1 St", ContractorSpecialty.OTHER, null, null);
        MvcResult result = mockMvc.perform(post("/api/contractors")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();
        Map<?, ?> body = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
        return UUID.fromString((String) body.get("id"));
    }

    /** Uploads a PDF as ADMIN and returns the created document id. */
    private String uploadPdf(UUID contractorId) throws Exception {
        MvcResult result = mockMvc.perform(multipart("/api/contractors/" + contractorId + "/documents")
                        .file(new MockMultipartFile("file", "doc.pdf", "application/pdf", PDF_BYTES))
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isCreated())
                .andReturn();
        Map<?, ?> body = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
        return (String) body.get("id");
    }
}
