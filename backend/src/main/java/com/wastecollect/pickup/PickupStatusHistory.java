package com.wastecollect.pickup;

import com.wastecollect.auth.User;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name = "pickup_status_history")
public class PickupStatusHistory {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "pickup_request_id") private PickupRequest request;
    @Enumerated(EnumType.STRING) @Column(name = "previous_status") private PickupStatus previousStatus;
    @Enumerated(EnumType.STRING) @Column(name = "next_status", nullable = false) private PickupStatus nextStatus;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "actor_id") private User actor;
    private String reason;
    @Column(nullable = false) private Instant createdAt;
    protected PickupStatusHistory() {}
    public PickupStatusHistory(PickupRequest request, PickupStatus previous, PickupStatus next, User actor, String reason) {
        this.id = UUID.randomUUID(); this.request = request; this.previousStatus = previous; this.nextStatus = next;
        this.actor = actor; this.reason = reason; this.createdAt = Instant.now();
    }
    public PickupStatus getPreviousStatus() { return previousStatus; }
    public PickupStatus getNextStatus() { return nextStatus; }
    public String getReason() { return reason; }
    public Instant getCreatedAt() { return createdAt; }
}
