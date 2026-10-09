package com.wastecollect.grouping;

import com.wastecollect.auth.User;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "group_membership_history")
public class GroupMembershipHistory {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "membership_id") private GroupMembership membership;
    @Column(nullable = false) private String action;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "actor_id") private User actor;
    @Column(nullable = false) private String reason;
    @Column(nullable = false) private Instant createdAt;
    protected GroupMembershipHistory() {}
    public GroupMembershipHistory(GroupMembership membership, User actor, String reason) {
        id = UUID.randomUUID(); this.membership = membership; this.actor = actor; this.reason = reason;
        action = "ADDED"; createdAt = Instant.now();
    }
}
