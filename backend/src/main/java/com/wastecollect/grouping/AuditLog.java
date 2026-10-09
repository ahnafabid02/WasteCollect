package com.wastecollect.grouping;

import com.wastecollect.auth.User;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "audit_logs")
public class AuditLog {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "actor_id") private User actor;
    @Column(nullable = false) private String action;
    @Column(name = "entity_type", nullable = false) private String entityType;
    @Column(name = "entity_id", nullable = false) private UUID entityId;
    private String details;
    @Column(nullable = false) private Instant createdAt;
    protected AuditLog() {}
    public AuditLog(User actor, String action, String entityType, UUID entityId, String details) {
        id = UUID.randomUUID(); this.actor = actor; this.action = action; this.entityType = entityType;
        this.entityId = entityId; this.details = details; createdAt = Instant.now();
    }
}
