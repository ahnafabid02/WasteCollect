package com.wastecollect.operations;

import com.wastecollect.auth.User;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

/** Administrator operations use explicit SQL so locking and historical writes share one transaction. */
@Service
@Transactional
public class OperationsService {
    private final NamedParameterJdbcTemplate db;
    public OperationsService(NamedParameterJdbcTemplate db) { this.db = db; }

    public record Page(List<Map<String, Object>> items, long total, int page, int size) {}
    public record Settings(int maxGroupRequests, int minimumNoticeHours, String serviceTimezone) {}

    @Transactional(readOnly = true)
    public LocalDate serviceToday() { return LocalDate.now(ZoneId.of(settings().serviceTimezone())); }

    @Transactional(readOnly = true)
    public Settings settings() {
        return db.queryForObject("SELECT * FROM operation_settings WHERE id=1", Map.of(), (rs, n) ->
            new Settings(rs.getInt("max_group_requests"), rs.getInt("minimum_notice_hours"), rs.getString("service_timezone")));
    }

    public Settings updateSettings(User actor, int maximum, int notice) {
        if (maximum < 1 || maximum > 500 || notice < 0 || notice > 168) throw new IllegalArgumentException("Invalid operational settings");
        db.update("UPDATE operation_settings SET max_group_requests=:maximum, minimum_notice_hours=:notice WHERE id=1",
            Map.of("maximum", maximum, "notice", notice));
        audit(actor, "SETTINGS_UPDATED", "SETTINGS", new UUID(0, 1), "Maximum group size=" + maximum + "; minimum notice hours=" + notice);
        return settings();
    }

    @Transactional(readOnly = true)
    public Page search(String kind, String query, String status, UUID zone, int page, int size, String sort, String direction) {
        if (page < 0 || size < 1 || size > 100) throw new IllegalArgumentException("Page must be nonnegative and size between 1 and 100");
        boolean requests = kind.equals("requests");
        if (!List.of("requests", "groups").contains(kind)) throw new IllegalArgumentException("Unsupported search resource");
        if (status != null && !status.isBlank() && !(requests
                ? List.of("PENDING", "GROUPED", "SCHEDULED", "IN_PROGRESS", "FAILED", "COMPLETED", "CANCELLED")
                : List.of("DRAFT", "SCHEDULED", "IN_PROGRESS", "COMPLETED", "CANCELLED")).contains(status))
            throw new IllegalArgumentException("Unsupported status filter");
        String table = requests ? "pickup_requests" : "collection_groups";
        Map<String, String> sorts = Map.of("createdAt", "x.created_at", "preferredDate", "x.preferred_date", "status", "x.status", "publicCode", "x.public_code");
        if (!sorts.containsKey(sort) || !(direction.equalsIgnoreCase("asc") || direction.equalsIgnoreCase("desc")))
            throw new IllegalArgumentException("Unsupported sort or direction");
        Map<String, Object> args = new HashMap<>();
        args.put("query", "%" + (query == null ? "" : query.trim()).toLowerCase(Locale.ROOT) + "%");
        String where = " WHERE (lower(x.public_code) LIKE :query OR lower(z.name) LIKE :query" + (requests ? " OR lower(x.address) LIKE :query OR lower(u.display_name) LIKE :query" : "") + ")";
        if (status != null && !status.isBlank()) { where += " AND x.status=:status"; args.put("status", status); }
        if (zone != null) { where += " AND x.service_zone_id=:zone"; args.put("zone", zone); }
        String from = " FROM " + table + " x JOIN service_zones z ON z.id=x.service_zone_id" +
            (requests ? " JOIN app_users u ON u.id=x.resident_id JOIN waste_categories c ON c.id=x.waste_category_id" : "");
        long total = db.queryForObject("SELECT count(*)" + from + where, args, Long.class);
        String columns = "x.id, x.public_code AS \"publicCode\", x.status, z.name AS \"zoneName\", x.preferred_date AS \"preferredDate\"" +
            (requests ? ", u.display_name AS \"residentName\", c.name AS \"categoryName\", x.address, x.quantity, x.unit" :
                ", x.scheduled_start AS \"scheduledStart\", x.scheduled_end AS \"scheduledEnd\", (SELECT count(*) FROM group_memberships m WHERE m.group_id=x.id AND m.active) AS \"memberCount\", (SELECT a.collector_id FROM collector_assignments a WHERE a.group_id=x.id AND a.closed_at IS NULL) AS \"collectorId\", (SELECT u.display_name FROM collector_assignments a JOIN app_users u ON u.id=a.collector_id WHERE a.group_id=x.id AND a.closed_at IS NULL) AS \"collectorName\"");
        args.put("limit", size); args.put("offset", (long) page * size);
        return new Page(db.queryForList("SELECT " + columns + from + where + " ORDER BY " + sorts.get(sort) + " " + direction + ", x.id LIMIT :limit OFFSET :offset", args), total, page, size);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> dashboard() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("requests", db.queryForList("SELECT status, count(*) AS total FROM pickup_requests GROUP BY status ORDER BY status", Map.of()));
        result.put("groups", db.queryForList("SELECT status, count(*) AS total FROM collection_groups GROUP BY status ORDER BY status", Map.of()));
        result.put("activeCollectors", db.queryForObject("SELECT count(*) FROM app_users WHERE role='COLLECTOR' AND status='ACTIVE'", Map.of(), Long.class));
        result.put("unassignedGroups", db.queryForObject("SELECT count(*) FROM collection_groups g WHERE status='SCHEDULED' AND NOT EXISTS (SELECT 1 FROM collector_assignments a WHERE a.group_id=g.id AND a.closed_at IS NULL)", Map.of(), Long.class));
        return result;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> requestDetail(UUID id) {
        List<Map<String, Object>> rows = db.queryForList("SELECT p.id,p.public_code AS \"publicCode\",p.status,p.address,p.quantity,p.unit,p.notes,p.preferred_date AS \"preferredDate\",z.name AS \"zoneName\",c.name AS \"categoryName\",u.display_name AS \"residentName\" FROM pickup_requests p JOIN service_zones z ON z.id=p.service_zone_id JOIN waste_categories c ON c.id=p.waste_category_id JOIN app_users u ON u.id=p.resident_id WHERE p.id=:id", Map.of("id", id));
        if (rows.isEmpty()) throw new NoSuchElementException("Request not found");
        Map<String, Object> result = rows.getFirst();
        result.put("history", db.queryForList("SELECT h.previous_status AS \"previousStatus\",h.next_status AS \"nextStatus\",h.reason,h.created_at AS \"createdAt\",u.display_name AS \"actorName\" FROM pickup_status_history h JOIN app_users u ON u.id=h.actor_id WHERE h.pickup_request_id=:id ORDER BY h.created_at,h.id", Map.of("id", id)));
        return result;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> collectors() {
        return db.queryForList("SELECT u.id, u.email, u.display_name AS \"displayName\", u.status, (SELECT count(*) FROM collector_assignments a WHERE a.collector_id=u.id AND a.closed_at IS NULL) AS \"activeAssignments\" FROM app_users u WHERE role='COLLECTOR' ORDER BY u.display_name, u.id", Map.of());
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> availableCollectors(UUID group, Instant start, Instant end) {
        validateWindow(start, end);
        return db.queryForList("SELECT u.id, u.display_name AS \"displayName\", u.email FROM app_users u WHERE u.role='COLLECTOR' AND u.status='ACTIVE' AND NOT EXISTS (SELECT 1 FROM collector_assignments a WHERE a.collector_id=u.id AND a.closed_at IS NULL AND a.group_id<>:group AND a.starts_at < :end AND a.ends_at > :start) ORDER BY u.display_name,u.id",
            Map.of("group", group == null ? new UUID(0, 0) : group, "start", Timestamp.from(start), "end", Timestamp.from(end)));
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> assignmentHistory(UUID group) {
        if (db.queryForObject("SELECT count(*) FROM collection_groups WHERE id=:id", Map.of("id", group), Long.class) == 0)
            throw new NoSuchElementException("Group not found");
        return db.queryForList("SELECT a.id,u.display_name AS \"collectorName\",a.collector_id AS \"collectorId\",a.starts_at AS \"startsAt\",a.ends_at AS \"endsAt\",a.assigned_at AS \"assignedAt\",a.closed_at AS \"closedAt\",a.reason FROM collector_assignments a JOIN app_users u ON u.id=a.collector_id WHERE a.group_id=:id ORDER BY a.assigned_at,a.id", Map.of("id", group));
    }

    public void collectorStatus(User actor, UUID id, String status) {
        if (!List.of("ACTIVE", "SUSPENDED").contains(status)) throw new IllegalArgumentException("Use ACTIVE or SUSPENDED");
        lockCollector(id);
        if (status.equals("SUSPENDED") && db.queryForObject("SELECT count(*) FROM collector_assignments WHERE collector_id=:id AND closed_at IS NULL", Map.of("id", id), Long.class) > 0)
            throw new IllegalStateException("Reassign active work before suspending this collector");
        db.update("UPDATE app_users SET status=:status, updated_at=CURRENT_TIMESTAMP WHERE id=:id", Map.of("id", id, "status", status));
        audit(actor, "COLLECTOR_STATUS_CHANGED", "USER", id, status);
    }

    private Map<String, Object> lockGroup(UUID id) {
        List<Map<String, Object>> rows = db.queryForList("SELECT * FROM collection_groups WHERE id=:id FOR UPDATE", Map.of("id", id));
        if (rows.isEmpty()) throw new NoSuchElementException("Group not found");
        return rows.getFirst();
    }
    private Map<String, Object> lockCollector(UUID id) {
        List<Map<String, Object>> rows = db.queryForList("SELECT * FROM app_users WHERE id=:id AND role='COLLECTOR' FOR UPDATE", Map.of("id", id));
        if (rows.isEmpty()) throw new NoSuchElementException("Collector not found");
        return rows.getFirst();
    }
    private List<Map<String, Object>> lockMembers(UUID group) {
        return db.queryForList("SELECT p.id, p.status, m.id AS membership_id FROM pickup_requests p JOIN group_memberships m ON m.pickup_request_id=p.id WHERE m.group_id=:id AND m.active ORDER BY p.id FOR UPDATE OF p, m", Map.of("id", group));
    }
    private List<Map<String, Object>> assignment(UUID group) {
        return db.queryForList("SELECT * FROM collector_assignments WHERE group_id=:id AND closed_at IS NULL", Map.of("id", group));
    }
    private void validateWindow(Instant start, Instant end) {
        if (start == null || end == null || !end.isAfter(start)) throw new IllegalArgumentException("End must be after start");
        if (start.isBefore(Instant.now().plusSeconds(settings().minimumNoticeHours() * 3600L))) throw new IllegalArgumentException("Schedule violates the minimum notice period");
        if (end.isAfter(start.plusSeconds(86400))) throw new IllegalArgumentException("A collection window cannot exceed 24 hours");
    }
    private void availability(UUID collector, UUID group, Instant start, Instant end) {
        Map<String, Object> user = lockCollector(collector);
        if (!user.get("status").equals("ACTIVE")) throw new IllegalStateException("Collector is not active");
        Long overlaps = db.queryForObject("SELECT count(*) FROM collector_assignments WHERE collector_id=:collector AND group_id<>:group AND closed_at IS NULL AND starts_at < :end AND ends_at > :start",
            Map.of("collector", collector, "group", group, "start", Timestamp.from(start), "end", Timestamp.from(end)), Long.class);
        if (overlaps > 0) throw new IllegalStateException("Collector already has an overlapping assignment");
    }
    private void closeAssignment(UUID group) {
        db.update("UPDATE collector_assignments SET closed_at=CURRENT_TIMESTAMP WHERE group_id=:id AND closed_at IS NULL", Map.of("id", group));
    }
    private void insertAssignment(User actor, UUID group, UUID collector, Instant start, Instant end, String reason) {
        db.update("INSERT INTO collector_assignments(id,group_id,collector_id,starts_at,ends_at,assigned_by,reason) VALUES (:id,:group,:collector,:start,:end,:actor,:reason)",
            Map.of("id", UUID.randomUUID(), "group", group, "collector", collector, "start", Timestamp.from(start), "end", Timestamp.from(end), "actor", actor.getId(), "reason", reason));
    }
    private void requestStatus(User actor, Map<String, Object> request, String next, String reason) {
        UUID id = (UUID) request.get("id");
        db.update("UPDATE pickup_requests SET status=:next,updated_at=CURRENT_TIMESTAMP WHERE id=:id", Map.of("next", next, "id", id));
        db.update("INSERT INTO pickup_status_history(id,pickup_request_id,previous_status,next_status,actor_id,reason,created_at) VALUES (:history,:id,:previous,:next,:actor,:reason,CURRENT_TIMESTAMP)",
            Map.of("history", UUID.randomUUID(), "id", id, "previous", request.get("status"), "next", next, "actor", actor.getId(), "reason", reason));
    }

    public void schedule(User actor, UUID id, Instant start, Instant end, String reason) {
        validateReason(reason);
        Map<String, Object> group = lockGroup(id);
        if (!List.of("DRAFT", "SCHEDULED").contains(group.get("status"))) throw new IllegalStateException("Only draft or scheduled groups can be scheduled");
        if (group.get("scheduled_start") != null && !((Timestamp) group.get("scheduled_start")).toInstant().isAfter(Instant.now()))
            throw new IllegalStateException("Cannot reschedule after the scheduled window starts");
        if (!Boolean.TRUE.equals(db.queryForObject("SELECT active FROM service_zones WHERE id=:id", Map.of("id", group.get("service_zone_id")), Boolean.class)))
            throw new IllegalStateException("Service zone is inactive");
        validateWindow(start, end);
        LocalDate serviceDate = start.atZone(ZoneId.of(settings().serviceTimezone())).toLocalDate();
        if (serviceDate.isBefore(((java.sql.Date) group.get("preferred_date")).toLocalDate()))
            throw new IllegalArgumentException("Schedule cannot precede the group's preferred date");
        List<Map<String, Object>> members = lockMembers(id);
        if (members.isEmpty() || members.stream().anyMatch(m -> !List.of("GROUPED", "SCHEDULED").contains(m.get("status"))))
            throw new IllegalStateException("Group must contain eligible active members");
        if (group.get("status").equals("SCHEDULED") && Timestamp.from(start).equals(group.get("scheduled_start")) && Timestamp.from(end).equals(group.get("scheduled_end"))) return;
        List<Map<String, Object>> current = assignment(id);
        if (!current.isEmpty()) {
            UUID collector = (UUID) current.getFirst().get("collector_id");
            availability(collector, id, start, end);
            closeAssignment(id); insertAssignment(actor, id, collector, start, end, reason);
        }
        db.update("UPDATE collection_groups SET status='SCHEDULED',scheduled_start=:start,scheduled_end=:end,updated_at=CURRENT_TIMESTAMP WHERE id=:id",
            Map.of("id", id, "start", Timestamp.from(start), "end", Timestamp.from(end)));
        for (Map<String, Object> member : members) requestStatus(actor, member, "SCHEDULED", reason);
        audit(actor, group.get("status").equals("DRAFT") ? "GROUP_SCHEDULED" : "GROUP_RESCHEDULED", "COLLECTION_GROUP", id, start + " to " + end + "; " + reason);
    }

    public void assign(User actor, UUID id, UUID collector, String reason) {
        validateReason(reason);
        Map<String, Object> group = lockGroup(id);
        if (!group.get("status").equals("SCHEDULED")) throw new IllegalStateException("Schedule the group before assignment");
        List<Map<String, Object>> current = assignment(id);
        // Serialize old/new collector operations in UUID order to avoid deadlocks during swaps.
        TreeSet<UUID> locks = new TreeSet<>(); locks.add(collector);
        if (!current.isEmpty()) locks.add((UUID) current.getFirst().get("collector_id"));
        for (UUID user : locks) lockCollector(user);
        Instant start = ((Timestamp) group.get("scheduled_start")).toInstant();
        Instant end = ((Timestamp) group.get("scheduled_end")).toInstant();
        if (!start.isAfter(Instant.now())) throw new IllegalStateException("Cannot assign a collection window that has started");
        availability(collector, id, start, end);
        if (!current.isEmpty() && collector.equals(current.getFirst().get("collector_id"))) return;
        closeAssignment(id); insertAssignment(actor, id, collector, start, end, reason);
        audit(actor, current.isEmpty() ? "COLLECTOR_ASSIGNED" : "COLLECTOR_REASSIGNED", "COLLECTION_GROUP", id, "Collector=" + collector + "; " + reason);
    }

    public void cancel(User actor, UUID id, String reason) {
        validateReason(reason);
        Map<String, Object> group = lockGroup(id);
        if (!List.of("DRAFT", "SCHEDULED").contains(group.get("status"))) throw new IllegalStateException("Only draft or scheduled groups can be cancelled");
        if (group.get("scheduled_start") != null && !((Timestamp) group.get("scheduled_start")).toInstant().isAfter(Instant.now()))
            throw new IllegalStateException("Cannot cancel after the scheduled window starts");
        List<Map<String, Object>> members = lockMembers(id);
        if (members.stream().anyMatch(m -> !List.of("GROUPED", "SCHEDULED").contains(m.get("status")))) throw new IllegalStateException("Group contains operational work");
        List<Map<String, Object>> current = assignment(id);
        if (!current.isEmpty()) lockCollector((UUID) current.getFirst().get("collector_id"));
        closeAssignment(id);
        for (Map<String, Object> member : members) {
            UUID membership = (UUID) member.get("membership_id");
            db.update("UPDATE group_memberships SET active=FALSE,removed_by=:actor,removed_at=CURRENT_TIMESTAMP WHERE id=:id", Map.of("actor", actor.getId(), "id", membership));
            db.update("INSERT INTO group_membership_history(id,membership_id,action,actor_id,reason,created_at) VALUES (:id,:membership,'REMOVED',:actor,:reason,CURRENT_TIMESTAMP)",
                Map.of("id", UUID.randomUUID(), "membership", membership, "actor", actor.getId(), "reason", reason));
            requestStatus(actor, member, "PENDING", "Group cancelled; returned to queue: " + reason);
        }
        db.update("UPDATE collection_groups SET status='CANCELLED',updated_at=CURRENT_TIMESTAMP WHERE id=:id", Map.of("id", id));
        audit(actor, "GROUP_CANCELLED", "COLLECTION_GROUP", id, reason);
    }

    @Transactional(readOnly = true)
    public Page auditHistory(UUID entityId, int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw new IllegalArgumentException("Invalid pagination");
        Map<String, Object> args = new HashMap<>(); args.put("limit", size); args.put("offset", (long) page * size);
        String where = "";
        if (entityId != null) { where = " WHERE a.entity_id=:entity"; args.put("entity", entityId); }
        long total = db.queryForObject("SELECT count(*) FROM audit_logs a" + where, args, Long.class);
        return new Page(db.queryForList("SELECT a.id,a.action,a.entity_type AS \"entityType\",a.entity_id AS \"entityId\",a.details,a.created_at AS \"createdAt\",u.display_name AS \"actorName\" FROM audit_logs a JOIN app_users u ON u.id=a.actor_id" + where + " ORDER BY a.created_at DESC,a.id LIMIT :limit OFFSET :offset", args), total, page, size);
    }
    public void audit(User actor, String action, String type, UUID entity, String details) {
        db.update("INSERT INTO audit_logs(id,actor_id,action,entity_type,entity_id,details,created_at) VALUES (:id,:actor,:action,:type,:entity,:details,CURRENT_TIMESTAMP)",
            Map.of("id", UUID.randomUUID(), "actor", actor.getId(), "action", action, "type", type, "entity", entity, "details", details));
    }
    private void validateReason(String reason) {
        if (reason == null || reason.isBlank() || reason.length() > 500) throw new IllegalArgumentException("A reason of 1 to 500 characters is required");
    }
}
