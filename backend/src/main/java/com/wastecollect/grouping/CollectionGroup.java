package com.wastecollect.grouping;

import com.wastecollect.auth.User;
import com.wastecollect.pickup.ServiceZone;
import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "collection_groups")
public class CollectionGroup {
    @Id private UUID id;
    @Column(name = "public_code", nullable = false, unique = true) private String publicCode;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "service_zone_id") private ServiceZone zone;
    @Column(name = "preferred_date", nullable = false) private LocalDate preferredDate;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private GroupStatus status;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "created_by") private User createdBy;
    @Column(nullable = false) private Instant createdAt;
    @Column(nullable = false) private Instant updatedAt;

    protected CollectionGroup() {}
    public CollectionGroup(ServiceZone zone, LocalDate preferredDate, User createdBy) {
        id = UUID.randomUUID();
        publicCode = "GRP-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        this.zone = zone; this.preferredDate = preferredDate; this.createdBy = createdBy;
        status = GroupStatus.DRAFT; createdAt = Instant.now(); updatedAt = createdAt;
    }
    public UUID getId() { return id; }
    public String getPublicCode() { return publicCode; }
    public ServiceZone getZone() { return zone; }
    public LocalDate getPreferredDate() { return preferredDate; }
    public GroupStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
}
