package com.wastecollect.operations;

import com.wastecollect.auth.User;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

@Service
@Transactional
public class CollectorOperationsService {
    private final NamedParameterJdbcTemplate db;

    public CollectorOperationsService(NamedParameterJdbcTemplate db) {
        this.db = db;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> dashboard(User collector) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("today", LocalDate.now());
        result.put("assignedGroups", groups(collector, null, null));
        result.put("completedRequests", db.queryForObject("""
            SELECT count(*) FROM pickup_attempts
            WHERE collector_id=:collector AND outcome='COMPLETED'
              AND attempted_at >= CURRENT_DATE AND attempted_at < CURRENT_DATE + INTERVAL '1 day'
            """, Map.of("collector", collector.getId()), Long.class));
        result.put("failedRequests", db.queryForObject("""
            SELECT count(*) FROM pickup_attempts
            WHERE collector_id=:collector AND outcome='FAILED'
              AND attempted_at >= CURRENT_DATE AND attempted_at < CURRENT_DATE + INTERVAL '1 day'
            """, Map.of("collector", collector.getId()), Long.class));
        return result;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> groups(User collector, LocalDate date, String status) {
        Map<String, Object> args = new HashMap<>();
        args.put("collector", collector.getId());
        String where = " WHERE a.collector_id=:collector AND a.closed_at IS NULL";
        if (date != null) {
            where += " AND g.scheduled_start::date=:date";
            args.put("date", date);
        }
        if (status != null && !status.isBlank()) {
            if (!List.of("SCHEDULED", "IN_PROGRESS", "COMPLETED").contains(status))
                throw new IllegalArgumentException("Unsupported group status");
            where += " AND g.status=:status";
            args.put("status", status);
        }
        return db.queryForList("""
            SELECT g.id, g.public_code AS "publicCode", g.status, z.name AS "zoneName",
                   g.preferred_date AS "preferredDate", g.scheduled_start AS "scheduledStart",
                   g.scheduled_end AS "scheduledEnd",
                   count(m.id) FILTER (WHERE m.active) AS "memberCount",
                   count(m.id) FILTER (WHERE m.active AND p.status='COMPLETED') AS "completedCount"
            FROM collection_groups g
            JOIN collector_assignments a ON a.group_id=g.id
            JOIN service_zones z ON z.id=g.service_zone_id
            LEFT JOIN group_memberships m ON m.group_id=g.id
            LEFT JOIN pickup_requests p ON p.id=m.pickup_request_id
            """ + where + """
            GROUP BY g.id, z.name, a.id
            ORDER BY g.scheduled_start NULLS LAST, g.id
            """, args);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> group(User collector, UUID groupId) {
        requireAssignment(collector, groupId);
        List<Map<String, Object>> groups = db.queryForList("""
            SELECT g.id, g.public_code AS "publicCode", g.status, z.name AS "zoneName",
                   g.preferred_date AS "preferredDate", g.scheduled_start AS "scheduledStart",
                   g.scheduled_end AS "scheduledEnd"
            FROM collection_groups g JOIN service_zones z ON z.id=g.service_zone_id
            WHERE g.id=:group
            """, Map.of("group", groupId));
        if (groups.isEmpty()) throw new NoSuchElementException("Collection group not found");
        Map<String, Object> result = new LinkedHashMap<>(groups.getFirst());
        result.put("requests", db.queryForList("""
            SELECT p.id, p.public_code AS "publicCode", p.status, p.address, p.quantity, p.unit,
                   p.notes, c.name AS "categoryName",
                   (SELECT count(*) FROM pickup_attempts a WHERE a.pickup_request_id=p.id) AS attempts
            FROM group_memberships m JOIN pickup_requests p ON p.id=m.pickup_request_id
            JOIN waste_categories c ON c.id=p.waste_category_id
            WHERE m.group_id=:group AND m.active ORDER BY p.address, p.id
            """, Map.of("group", groupId)));
        result.put("attempts", db.queryForList("""
            SELECT a.id, a.pickup_request_id AS "requestId", p.public_code AS "publicCode",
                   a.attempt_number AS "attemptNumber", a.outcome, a.reason,
                   a.retry_at AS "retryAt", a.attempted_at AS "attemptedAt"
            FROM pickup_attempts a JOIN pickup_requests p ON p.id=a.pickup_request_id
            WHERE a.group_id=:group ORDER BY a.attempted_at, a.id
            """, Map.of("group", groupId)));
        return result;
    }

    public void start(User collector, UUID groupId) {
        Map<String, Object> assignment = requireAssignment(collector, groupId);
        Map<String, Object> group = lockGroup(groupId);
        if (!List.of("SCHEDULED", "IN_PROGRESS").contains(group.get("status")))
            throw new IllegalStateException("Only scheduled groups can be started");
        if ("SCHEDULED".equals(group.get("status"))) {
            db.update("UPDATE collection_groups SET status='IN_PROGRESS', updated_at=CURRENT_TIMESTAMP WHERE id=:group",
                Map.of("group", groupId));
            updateMembers(collector, groupId, "SCHEDULED", "IN_PROGRESS", "Collector started work");
        }
    }

    public void attempt(User collector, UUID groupId, UUID requestId, String outcome, String reason, Instant retryAt) {
        Map<String, Object> assignment = requireAssignment(collector, groupId);
        Map<String, Object> group = lockGroup(groupId);
        if (!"IN_PROGRESS".equals(group.get("status"))) throw new IllegalStateException("Start the group before recording work");
        Map<String, Object> request = lockAssignedRequest(groupId, requestId);
        if (!"IN_PROGRESS".equals(request.get("status")))
            throw new IllegalStateException("This request is not ready for an attempt");
        if (!List.of("COMPLETED", "FAILED").contains(outcome))
            throw new IllegalArgumentException("Outcome must be COMPLETED or FAILED");
        validateReason(reason);
        if ("COMPLETED".equals(outcome) && retryAt != null)
            throw new IllegalArgumentException("Completed work cannot have a retry time");
        if ("FAILED".equals(outcome) && retryAt != null && !retryAt.isAfter(Instant.now()))
            throw new IllegalArgumentException("Retry time must be in the future");
        int attemptNumber = db.queryForObject("SELECT COALESCE(max(attempt_number),0)+1 FROM pickup_attempts WHERE pickup_request_id=:request",
            Map.of("request", requestId), Integer.class);
        Map<String, Object> attemptArgs = new HashMap<>();
        attemptArgs.put("id", UUID.randomUUID());
        attemptArgs.put("request", requestId);
        attemptArgs.put("group", groupId);
        attemptArgs.put("assignment", assignment.get("id"));
        attemptArgs.put("collector", collector.getId());
        attemptArgs.put("number", attemptNumber);
        attemptArgs.put("outcome", outcome);
        attemptArgs.put("reason", reason);
        attemptArgs.put("retryAt", retryAt == null ? null : Timestamp.from(retryAt));
        db.update("""
            INSERT INTO pickup_attempts(id,pickup_request_id,group_id,assignment_id,collector_id,attempt_number,outcome,reason,retry_at)
            VALUES (:id,:request,:group,:assignment,:collector,:number,:outcome,:reason,:retryAt)
            """, attemptArgs);
        updateRequestStatus(request, outcome, reason, collector);
        if ("COMPLETED".equals(outcome) && db.queryForObject("""
            SELECT count(*) FROM group_memberships m JOIN pickup_requests p ON p.id=m.pickup_request_id
            WHERE m.group_id=:group AND m.active AND p.status <> 'COMPLETED'
            """, Map.of("group", groupId), Long.class) == 0) {
            db.update("UPDATE collection_groups SET status='COMPLETED', updated_at=CURRENT_TIMESTAMP WHERE id=:group", Map.of("group", groupId));
        }
    }

    public void retry(User collector, UUID groupId, UUID requestId, String reason) {
        requireAssignment(collector, groupId);
        lockGroup(groupId);
        Map<String, Object> request = lockAssignedRequest(groupId, requestId);
        if (!"FAILED".equals(request.get("status"))) throw new IllegalStateException("Only failed requests can be retried");
        validateReason(reason);
        updateRequestStatus(request, "SCHEDULED", reason, collector);
    }

    private Map<String, Object> requireAssignment(User collector, UUID groupId) {
        List<Map<String, Object>> rows = db.queryForList("""
            SELECT a.* FROM collector_assignments a
            WHERE a.group_id=:group AND a.collector_id=:collector AND a.closed_at IS NULL
            FOR UPDATE
            """, Map.of("group", groupId, "collector", collector.getId()));
        if (rows.isEmpty()) throw new NoSuchElementException("Collection group is not assigned to this collector");
        return rows.getFirst();
    }

    private Map<String, Object> lockGroup(UUID groupId) {
        List<Map<String, Object>> rows = db.queryForList("SELECT * FROM collection_groups WHERE id=:group FOR UPDATE", Map.of("group", groupId));
        if (rows.isEmpty()) throw new NoSuchElementException("Collection group not found");
        return rows.getFirst();
    }

    private Map<String, Object> lockAssignedRequest(UUID groupId, UUID requestId) {
        List<Map<String, Object>> rows = db.queryForList("""
            SELECT p.* FROM pickup_requests p JOIN group_memberships m ON m.pickup_request_id=p.id
            WHERE m.group_id=:group AND m.active AND p.id=:request FOR UPDATE OF p
            """, Map.of("group", groupId, "request", requestId));
        if (rows.isEmpty()) throw new NoSuchElementException("Request is not part of this assigned group");
        return rows.getFirst();
    }

    private void updateMembers(User actor, UUID groupId, String from, String to, String reason) {
        List<Map<String, Object>> members = db.queryForList("""
            SELECT p.* FROM pickup_requests p JOIN group_memberships m ON m.pickup_request_id=p.id
            WHERE m.group_id=:group AND m.active AND p.status=:from FOR UPDATE OF p
            """, Map.of("group", groupId, "from", from));
        for (Map<String, Object> member : members) updateRequestStatus(member, to, reason, actor);
    }

    private void updateRequestStatus(Map<String, Object> request, String next, String reason, User actor) {
        db.update("UPDATE pickup_requests SET status=:next, updated_at=CURRENT_TIMESTAMP WHERE id=:id",
            Map.of("next", next, "id", request.get("id")));
        db.update("""
            INSERT INTO pickup_status_history(id,pickup_request_id,previous_status,next_status,actor_id,reason,created_at)
            VALUES (:id,:request,:previous,:next,:actor,:reason,CURRENT_TIMESTAMP)
            """, Map.of("id", UUID.randomUUID(), "request", request.get("id"), "previous", request.get("status"),
            "next", next, "actor", actor.getId(), "reason", reason));
    }

    private void validateReason(String reason) {
        if (reason == null || reason.isBlank() || reason.length() > 500) throw new IllegalArgumentException("A reason is required");
    }
}
