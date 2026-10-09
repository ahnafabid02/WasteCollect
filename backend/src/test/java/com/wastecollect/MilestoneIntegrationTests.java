package com.wastecollect;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.EntityManager;

import java.time.LocalDate;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Rollback
class MilestoneIntegrationTests {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String CENTRAL_ZONE = "00000000-0000-0000-0000-000000000001";
    private static final String GENERAL_WASTE = "00000000-0000-0000-0000-000000000001";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwords;
    @Autowired EntityManager entityManager;

    @Test
    void residentRequestHistoryAndOwnershipAreEnforced() throws Exception {
        String first = residentToken("first");
        String second = residentToken("second");
        UUID requestId = createRequest(first, LocalDate.now().plusDays(1));

        mvc.perform(get("/api/v1/requests/{id}", requestId).header("Authorization", bearer(second)))
            .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/requests/{id}/history", requestId).header("Authorization", bearer(first)))
            .andExpect(status().isOk()).andExpect(jsonPath("$[0].nextStatus").value("PENDING"));
        mvc.perform(patch("/api/v1/requests/{id}/cancel", requestId).header("Authorization", bearer(first)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
        mvc.perform(get("/api/v1/requests/{id}/history", requestId).header("Authorization", bearer(first)))
            .andExpect(status().isOk()).andExpect(jsonPath("$[1].nextStatus").value("CANCELLED"));
    }

    @Test
    void groupingSuggestionsAreAdminOnlyAndConfirmationIsAtomic() throws Exception {
        LocalDate date = LocalDate.now().plusDays(2);
        String resident = residentToken("grouping");
        UUID first = createRequest(resident, date);
        UUID second = createRequest(resident, date);

        mvc.perform(post("/api/v1/admin/groups/suggestions").header("Authorization", bearer(resident)))
            .andExpect(status().isForbidden());

        String admin = adminToken();
        mvc.perform(post("/api/v1/admin/groups/suggestions").header("Authorization", bearer(admin)))
            .andExpect(status().isOk()).andExpect(jsonPath("$[0].zoneId").value(CENTRAL_ZONE));

        String confirmation = """
            {"zoneId":"%s","preferredDate":"%s","requestIds":["%s","%s"]}
            """.formatted(CENTRAL_ZONE, date, first, second);
        mvc.perform(post("/api/v1/admin/groups").header("Authorization", bearer(admin))
                .contentType(MediaType.APPLICATION_JSON).content(confirmation))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("DRAFT"))
            .andExpect(jsonPath("$.requests.length()").value(2));
        mvc.perform(post("/api/v1/admin/groups").header("Authorization", bearer(admin))
                .contentType(MediaType.APPLICATION_JSON).content(confirmation))
            .andExpect(status().isConflict());
        mvc.perform(get("/api/v1/requests/{id}", first).header("Authorization", bearer(resident)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("GROUPED"));
        mvc.perform(patch("/api/v1/requests/{id}/cancel", first).header("Authorization", bearer(resident)))
            .andExpect(status().isConflict());

        mvc.perform(post("/api/v1/admin/users/collectors").header("Authorization", bearer(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"collector." + UUID.randomUUID() + "@example.com\",\"temporaryPassword\":\"collector-pass-123\",\"displayName\":\"Test Collector\"}"))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.role").value("COLLECTOR"));
    }

    @Test
    void refreshTokensRotateAndLogoutRevokesTheCurrentSession() throws Exception {
        String email = "session." + UUID.randomUUID() + "@example.com";
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"long-password-123\",\"displayName\":\"Session Test\"}"))
            .andExpect(status().isCreated());
        JsonNode initial = loginResponse(email, "long-password-123");
        String initialRefresh = initial.get("refreshToken").asText();
        String rotatedBody = mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + initialRefresh + "\"}"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String rotatedRefresh = JSON.readTree(rotatedBody).get("refreshToken").asText();
        mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + initialRefresh + "\"}"))
            .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/auth/logout").contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + rotatedRefresh + "\"}"))
            .andExpect(status().isNoContent());
        mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + rotatedRefresh + "\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void collectorCanOnlyExecuteAssignedWorkAndResidentSeesCompletedStatus() throws Exception {
        LocalDate date = LocalDate.now().plusDays(3);
        String resident = residentToken("collector-flow");
        UUID requestId = createRequest(resident, date);
        String admin = adminToken();
        String groupBody = """
            {"zoneId":"%s","preferredDate":"%s","requestIds":["%s"]}
            """.formatted(CENTRAL_ZONE, date, requestId);
        String groupResponse = mvc.perform(post("/api/v1/admin/groups").header("Authorization", bearer(admin))
                .contentType(MediaType.APPLICATION_JSON).content(groupBody))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        UUID groupId = UUID.fromString(JSON.readTree(groupResponse).get("id").asText());
        entityManager.flush();
        String start = date + "T09:00:00Z";
        String end = date + "T10:00:00Z";
        mvc.perform(patch("/api/v1/admin/groups/{id}/schedule", groupId).header("Authorization", bearer(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"startsAt\":\"" + start + "\",\"endsAt\":\"" + end + "\",\"reason\":\"Planned route\"}"))
            .andExpect(status().isNoContent());
        String collectorEmail = "collector." + UUID.randomUUID() + "@example.com";
        String collectorPassword = "collector-pass-123";
        mvc.perform(post("/api/v1/admin/users/collectors").header("Authorization", bearer(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + collectorEmail + "\",\"temporaryPassword\":\"" + collectorPassword + "\",\"displayName\":\"Route Collector\"}"))
            .andExpect(status().isCreated());
        entityManager.flush();
        mvc.perform(post("/api/v1/admin/groups/{id}/assignment", groupId).header("Authorization", bearer(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"collectorId\":\"" + jdbc.queryForObject("SELECT id FROM app_users WHERE email=?", UUID.class, collectorEmail) + "\",\"reason\":\"Assigned route\"}"))
            .andExpect(status().isNoContent());
        String collector = login(collectorEmail, collectorPassword);

        mvc.perform(get("/api/v1/collector/groups/{id}", groupId).header("Authorization", bearer(collector)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.requests[0].id").value(requestId.toString()));
        mvc.perform(post("/api/v1/collector/groups/{id}/start", groupId).header("Authorization", bearer(collector)))
            .andExpect(status().isNoContent());
        mvc.perform(post("/api/v1/collector/groups/{id}/attempts", groupId).header("Authorization", bearer(collector))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"requestId\":\"" + requestId + "\",\"outcome\":\"COMPLETED\",\"reason\":\"Collected at gate\"}"))
            .andExpect(status().isNoContent());
        entityManager.clear();
        mvc.perform(get("/api/v1/requests/{id}", requestId).header("Authorization", bearer(resident)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("COMPLETED"));
        mvc.perform(get("/api/v1/collector/groups/{id}", groupId).header("Authorization", bearer(resident)))
            .andExpect(status().isForbidden());
    }

    private String residentToken(String prefix) throws Exception {
        String email = prefix + "." + UUID.randomUUID() + "@example.com";
        String body = """
            {"email":"%s","password":"long-password-123","displayName":"Test Resident"}
            """.formatted(email);
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated());
        return login(email, "long-password-123");
    }

    private String adminToken() throws Exception {
        String email = "admin." + UUID.randomUUID() + "@example.com";
        jdbc.update("INSERT INTO app_users(id,email,password_hash,display_name,role,status,created_at,updated_at) VALUES (?,?,?,?, 'ADMIN','ACTIVE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
            UUID.randomUUID(), email, passwords.encode("admin-password-123"), "Test Administrator");
        return login(email, "admin-password-123");
    }

    private String login(String email, String password) throws Exception {
        return loginResponse(email, password).get("accessToken").asText();
    }

    private JsonNode loginResponse(String email, String password) throws Exception {
        String result = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JSON.readTree(result);
    }

    private UUID createRequest(String token, LocalDate date) throws Exception {
        String body = """
            {"zoneId":"%s","categoryId":"%s","address":"12 Test Road","quantity":2,"unit":"BAG","preferredDate":"%s","notes":"Gate is open"}
            """.formatted(CENTRAL_ZONE, GENERAL_WASTE, date);
        String result = mvc.perform(post("/api/v1/requests").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        JsonNode json = JSON.readTree(result);
        return UUID.fromString(json.get("id").asText());
    }

    private String bearer(String token) { return "Bearer " + token; }
}
