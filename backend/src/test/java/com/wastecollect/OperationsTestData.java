package com.wastecollect;

import org.springframework.jdbc.core.JdbcTemplate;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;

/** Fictional, isolated records; usable both inside rollback tests and committed concurrency tests. */
final class OperationsTestData {
    final JdbcTemplate jdbc;
    final UUID admin = UUID.randomUUID();
    final UUID resident = UUID.randomUUID();
    final UUID collector = UUID.randomUUID();
    final UUID replacement = UUID.randomUUID();
    final List<UUID> groups = new ArrayList<>();
    final List<UUID> requests = new ArrayList<>();
    final String marker = "m5-" + UUID.randomUUID();
    final Instant start = Instant.now().plus(Duration.ofDays(30)).truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
    OperationsTestData(JdbcTemplate jdbc, String passwordHash) {
        this.jdbc = jdbc;
        user(admin, "ADMIN", passwordHash); user(resident, "RESIDENT", passwordHash);
        user(collector, "COLLECTOR", passwordHash); user(replacement, "COLLECTOR", passwordHash);
    }
    String email(UUID id) { return "m5-" + id + "@example.com"; }
    private void user(UUID id, String role, String hash) {
        jdbc.update("INSERT INTO app_users(id,email,password_hash,display_name,role,status,created_at,updated_at) VALUES (?,?,?,?,?,'ACTIVE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
            id, email(id), hash, marker + " " + role, role);
    }
    UUID group(boolean scheduled) {
        UUID group = UUID.randomUUID(), request = UUID.randomUUID(), membership = UUID.randomUUID();
        groups.add(group); requests.add(request);
        jdbc.update("INSERT INTO pickup_requests(id,public_code,resident_id,service_zone_id,waste_category_id,address,quantity,unit,preferred_date,status,created_at,updated_at) VALUES (?,?,?,'00000000-0000-0000-0000-000000000001','00000000-0000-0000-0000-000000000001',?,1,'BAG',?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
            request, "WC-" + request.toString().substring(0, 8), resident, marker + " Test Road", LocalDate.now().plusDays(30), scheduled ? "SCHEDULED" : "GROUPED");
        jdbc.update("INSERT INTO collection_groups(id,public_code,service_zone_id,preferred_date,status,created_by,created_at,updated_at,scheduled_start,scheduled_end) VALUES (?,?,'00000000-0000-0000-0000-000000000001',?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,?,?)",
            group, "GRP-" + group.toString().substring(0, 8), LocalDate.now().plusDays(30), scheduled ? "SCHEDULED" : "DRAFT", admin,
            scheduled ? Timestamp.from(start) : null, scheduled ? Timestamp.from(start.plusSeconds(3600)) : null);
        jdbc.update("INSERT INTO group_memberships(id,group_id,pickup_request_id,active,added_by,added_at) VALUES (?,?,?,TRUE,?,CURRENT_TIMESTAMP)", membership, group, request, admin);
        return group;
    }
    void cleanup() {
        jdbc.update("DELETE FROM collector_assignments WHERE assigned_by=?", admin);
        jdbc.update("DELETE FROM group_membership_history WHERE membership_id IN (SELECT m.id FROM group_memberships m JOIN collection_groups g ON g.id=m.group_id WHERE g.created_by=?)", admin);
        jdbc.update("DELETE FROM group_memberships WHERE group_id IN (SELECT id FROM collection_groups WHERE created_by=?)", admin);
        jdbc.update("DELETE FROM pickup_status_history WHERE pickup_request_id IN (SELECT id FROM pickup_requests WHERE resident_id=?)", resident);
        jdbc.update("DELETE FROM audit_logs WHERE actor_id=?", admin);
        jdbc.update("DELETE FROM collection_groups WHERE created_by=?", admin);
        jdbc.update("DELETE FROM pickup_requests WHERE resident_id=?", resident);
        jdbc.update("DELETE FROM refresh_sessions WHERE user_id IN (?,?,?,?)", admin, resident, collector, replacement);
        jdbc.update("DELETE FROM app_users WHERE id IN (?,?,?,?)", admin, resident, collector, replacement);
    }
}
