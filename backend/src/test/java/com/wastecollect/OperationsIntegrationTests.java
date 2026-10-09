package com.wastecollect;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.EntityManager;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class OperationsIntegrationTests {
    private static final ObjectMapper JSON = new ObjectMapper();
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwords;
    @Autowired EntityManager entities;
    private OperationsTestData data;
    private String admin;
    @BeforeEach void setup() throws Exception {
        data = new OperationsTestData(jdbc, passwords.encode("test-password-123"));
        admin = login(data.admin);
        jdbc.update("UPDATE operation_settings SET minimum_notice_hours=0, max_group_requests=50 WHERE id=1");
    }

    @Test void scheduleAssignRescheduleReassignCancelPreservesHistoryAndCounts() throws Exception {
        UUID group = data.group(false), request = data.requests.getFirst();
        schedule(group, data.start, data.start.plusSeconds(3600), 204);
        mvc.perform(get("/api/v1/requests/{id}", request).header("Authorization", login(data.resident)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SCHEDULED"));
        entities.clear();
        assign(group, data.collector, 204);
        assign(group, data.collector, 204);
        assertEquals(1, count("SELECT count(*) FROM collector_assignments WHERE group_id=?", group));
        mvc.perform(patch("/api/v1/admin/collectors/{id}/status", data.collector).header("Authorization", admin)
            .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"SUSPENDED\"}"))
            .andExpect(status().isConflict());
        schedule(group, data.start.plusSeconds(7200), data.start.plusSeconds(10800), 204);
        schedule(group, data.start.plusSeconds(7200), data.start.plusSeconds(10800), 204);
        assertEquals(2, count("SELECT count(*) FROM collector_assignments WHERE group_id=?", group));
        assign(group, data.replacement, 204);
        assertEquals(3, count("SELECT count(*) FROM collector_assignments WHERE group_id=?", group));
        assertEquals(1, count("SELECT count(*) FROM collector_assignments WHERE group_id=? AND closed_at IS NULL", group));
        mvc.perform(get("/api/v1/admin/collections").header("Authorization", admin).param("query", publicCode(group)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].collectorId").value(data.replacement.toString()))
            .andExpect(jsonPath("$.items[0].scheduledStart").exists());
        mvc.perform(patch("/api/v1/admin/groups/{id}/cancel", group).header("Authorization", admin)
            .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Vehicle maintenance\"}"))
            .andExpect(status().isNoContent());
        assertEquals("PENDING", jdbc.queryForObject("SELECT status FROM pickup_requests WHERE id=?", String.class, request));
        assertEquals("CANCELLED", jdbc.queryForObject("SELECT status FROM collection_groups WHERE id=?", String.class, group));
        assertEquals(0, count("SELECT count(*) FROM group_memberships WHERE group_id=? AND active", group));
        assertEquals(0, count("SELECT count(*) FROM collector_assignments WHERE group_id=? AND closed_at IS NULL", group));
        mvc.perform(get("/api/v1/admin/requests/{id}", request).header("Authorization", admin))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PENDING"))
            .andExpect(jsonPath("$.history.length()").value(3));
        assertEquals(1, count("SELECT count(*) FROM group_membership_history h JOIN group_memberships m ON m.id=h.membership_id WHERE m.group_id=? AND h.action='REMOVED'", group));
        mvc.perform(get("/api/v1/admin/audit").header("Authorization", admin).param("entityId", group.toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(5));
        mvc.perform(get("/api/v1/admin/dashboard").header("Authorization", admin))
            .andExpect(status().isOk()).andExpect(jsonPath("$.unassignedGroups").value(
                jdbc.queryForObject("SELECT count(*) FROM collection_groups g WHERE status='SCHEDULED' AND NOT EXISTS (SELECT 1 FROM collector_assignments a WHERE a.group_id=g.id AND a.closed_at IS NULL)", Integer.class)));
        mvc.perform(patch("/api/v1/admin/collectors/{id}/status", data.collector).header("Authorization", admin)
            .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"SUSPENDED\"}"))
            .andExpect(status().isNoContent());
        schedule(group, data.start, data.start.plusSeconds(3600), 409);
    }

    @Test void conflictsRollbackAndAdjacentWindowsAreAllowed() throws Exception {
        UUID first = data.group(true), second = data.group(true);
        assign(first, data.collector, 204);
        assign(second, data.collector, 409);
        assertEquals(0, count("SELECT count(*) FROM collector_assignments WHERE group_id=?", second));
        schedule(second, data.start.plusSeconds(3600), data.start.plusSeconds(7200), 204);
        assign(second, data.collector, 204);
        schedule(second, data.start.plusSeconds(1800), data.start.plusSeconds(5400), 409);
        assertEquals(data.start.plusSeconds(3600), jdbc.queryForObject("SELECT scheduled_start FROM collection_groups WHERE id=?", Timestamp.class, second).toInstant());
        assertEquals(1, count("SELECT count(*) FROM collector_assignments WHERE group_id=?", second));
    }

    @Test void validatesDatesNoticeReasonsAndStartedWindows() throws Exception {
        UUID group = data.group(false);
        schedule(group, data.start, data.start.minusSeconds(1), 400);
        schedule(group, Instant.now().minusSeconds(3600), Instant.now().plusSeconds(3600), 400);
        schedule(group, data.start, data.start.plusSeconds(86401), 400);
        mvc.perform(patch("/api/v1/admin/settings").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
            .content("{\"maxGroupRequests\":10,\"minimumNoticeHours\":48}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.maxGroupRequests").value(10));
        schedule(group, Instant.now().plusSeconds(3600), Instant.now().plusSeconds(7200), 400);
        mvc.perform(patch("/api/v1/admin/groups/{id}/schedule", group).header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
            .content(JSON.writeValueAsString(Map.of("startsAt", data.start.toString(), "endsAt", data.start.plusSeconds(3600).toString(), "reason", " "))))
            .andExpect(status().isBadRequest());
        assign(group, data.collector, 409);
        UUID scheduled = data.group(true);
        jdbc.update("UPDATE collection_groups SET scheduled_start=?,scheduled_end=? WHERE id=?", Timestamp.from(Instant.now().minusSeconds(60)), Timestamp.from(Instant.now().plusSeconds(3600)), scheduled);
        schedule(scheduled, data.start, data.start.plusSeconds(3600), 409);
        assign(scheduled, data.collector, 409);
        mvc.perform(patch("/api/v1/admin/groups/{id}/cancel", scheduled).header("Authorization", admin).contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Too late\"}"))
            .andExpect(status().isConflict());
        schedule(UUID.randomUUID(), data.start, data.start.plusSeconds(3600), 404);
    }

    @Test void searchIsFilteredPaginatedAndSortInputCannotInjectSql() throws Exception {
        data.group(false); data.group(false);
        mvc.perform(get("/api/v1/admin/requests").header("Authorization", admin).param("query", data.marker).param("status", "GROUPED").param("size", "1").param("sort", "publicCode").param("direction", "asc"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(2)).andExpect(jsonPath("$.items.length()").value(1));
        mvc.perform(get("/api/v1/admin/requests").header("Authorization", admin).param("query", data.marker).param("zoneId", "00000000-0000-0000-0000-000000000002"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0));
        mvc.perform(get("/api/v1/admin/collections").header("Authorization", admin).param("sort", "createdAt; DELETE FROM app_users"))
            .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/admin/requests").header("Authorization", admin).param("size", "101"))
            .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/admin/requests").header("Authorization", admin).param("status", "UNKNOWN"))
            .andExpect(status().isBadRequest());
        mvc.perform(patch("/api/v1/admin/settings").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON).content("{\"maxGroupRequests\":0,\"minimumNoticeHours\":0}"))
            .andExpect(status().isBadRequest());
    }

    @Test void availabilityAndAssignmentHistoryReflectCommittedWork() throws Exception {
        UUID group = data.group(true);
        assign(group, data.collector, 204);
        String json = mvc.perform(get("/api/v1/admin/collectors/availability").header("Authorization", admin)
                .param("startsAt", data.start.toString()).param("endsAt", data.start.plusSeconds(3600).toString()))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        List<String> available = new ArrayList<>(); JSON.readTree(json).forEach(row -> available.add(row.get("id").asText()));
        assertFalse(available.contains(data.collector.toString())); assertTrue(available.contains(data.replacement.toString()));
        mvc.perform(get("/api/v1/admin/groups/{id}/assignments", group).header("Authorization", admin))
            .andExpect(status().isOk()).andExpect(jsonPath("$[0].collectorId").value(data.collector.toString()));
        assign(group, data.replacement, 204);
        mvc.perform(get("/api/v1/admin/groups/{id}/assignments", group).header("Authorization", admin))
            .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
        jdbc.update("UPDATE app_users SET status='SUSPENDED' WHERE id=?", data.collector);
        entities.clear();
        assign(group, data.collector, 409);
        assertEquals(data.replacement, jdbc.queryForObject("SELECT collector_id FROM collector_assignments WHERE group_id=? AND closed_at IS NULL", UUID.class, group));
    }

    @Test void cancellationReleasesRequestsForRegroupingAndSettingsLimitConfirmation() throws Exception {
        UUID first = data.group(false), second = data.group(false);
        for (UUID group : List.of(first, second))
            mvc.perform(patch("/api/v1/admin/groups/{id}/cancel", group).header("Authorization", admin).contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Reorganize route\"}"))
                .andExpect(status().isNoContent());
        jdbc.update("UPDATE operation_settings SET max_group_requests=1 WHERE id=1");
        Map<String, Object> confirmation = Map.of("zoneId", "00000000-0000-0000-0000-000000000001", "preferredDate", java.time.LocalDate.now().plusDays(30).toString(), "requestIds", data.requests);
        mvc.perform(post("/api/v1/admin/groups").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsString(confirmation)))
            .andExpect(status().isBadRequest());
        jdbc.update("UPDATE operation_settings SET max_group_requests=2 WHERE id=1");
        mvc.perform(post("/api/v1/admin/groups").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsString(confirmation)))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.requests.length()").value(2));
        entities.flush();
        assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM group_memberships m JOIN pickup_requests p ON p.id=m.pickup_request_id WHERE p.resident_id=? AND m.active", Integer.class, data.resident));
    }

    @Test void residentsAndCollectorsCannotReadOrChangeAdminOperations() throws Exception {
        UUID group = data.group(true);
        for (UUID user : List.of(data.resident, data.collector)) {
            String denied = login(user);
            for (String path : List.of("dashboard", "requests", "collections", "collectors", "audit", "settings"))
                mvc.perform(get("/api/v1/admin/" + path).header("Authorization", denied)).andExpect(status().isForbidden());
            mvc.perform(post("/api/v1/admin/groups/{id}/assignment", group).header("Authorization", denied)
                .contentType(MediaType.APPLICATION_JSON).content("{\"collectorId\":\"" + data.collector + "\",\"reason\":\"Unauthorized\"}"))
                .andExpect(status().isForbidden());
        }
        mvc.perform(get("/api/v1/admin/dashboard")).andExpect(status().isForbidden());
    }

    private String login(UUID user) throws Exception {
        String result = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content(JSON.writeValueAsString(Map.of("email", data.email(user), "password", "test-password-123"))))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return "Bearer " + JSON.readTree(result).get("accessToken").asText();
    }
    private void schedule(UUID group, Instant start, Instant end, int statusCode) throws Exception {
        mvc.perform(patch("/api/v1/admin/groups/{id}/schedule", group).header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
            .content(JSON.writeValueAsString(Map.of("startsAt", start.toString(), "endsAt", end.toString(), "reason", "Test scheduling"))))
            .andExpect(status().is(statusCode));
    }
    private void assign(UUID group, UUID collector, int statusCode) throws Exception {
        mvc.perform(post("/api/v1/admin/groups/{id}/assignment", group).header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
            .content(JSON.writeValueAsString(Map.of("collectorId", collector.toString(), "reason", "Test assignment"))))
            .andExpect(status().is(statusCode));
    }
    private int count(String sql, UUID id) { return jdbc.queryForObject(sql, Integer.class, id); }
    private String publicCode(UUID id) { return jdbc.queryForObject("SELECT public_code FROM collection_groups WHERE id=?", String.class, id); }
}
