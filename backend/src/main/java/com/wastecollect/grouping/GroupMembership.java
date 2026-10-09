package com.wastecollect.grouping;

import com.wastecollect.auth.User;
import com.wastecollect.pickup.PickupRequest;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "group_memberships")
public class GroupMembership {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "group_id") private CollectionGroup group;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "pickup_request_id") private PickupRequest request;
    @Column(nullable = false) private boolean active;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "added_by") private User addedBy;
    @Column(nullable = false) private Instant addedAt;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "removed_by") private User removedBy;
    private Instant removedAt;

    protected GroupMembership() {}
    public GroupMembership(CollectionGroup group, PickupRequest request, User actor) {
        id = UUID.randomUUID(); this.group = group; this.request = request; addedBy = actor;
        active = true; addedAt = Instant.now();
    }
    public UUID getId() { return id; }
    public CollectionGroup getGroup() { return group; }
    public PickupRequest getRequest() { return request; }
    public boolean isActive() { return active; }
}
