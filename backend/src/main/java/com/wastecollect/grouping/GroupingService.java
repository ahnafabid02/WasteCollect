package com.wastecollect.grouping;

import com.wastecollect.auth.User;
import com.wastecollect.pickup.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;

@Service
public class GroupingService {
    private final PickupGroupingGateway pickups;
    private final GroupingStrategy strategy;
    private final CollectionGroupRepository groups;
    private final GroupMembershipRepository memberships;
    private final GroupMembershipHistoryRepository membershipHistory;
    private final AuditLogRepository auditLogs;
    private final int maxRequests;

    public GroupingService(PickupGroupingGateway pickups, GroupingStrategy strategy,
                           CollectionGroupRepository groups, GroupMembershipRepository memberships,
                           GroupMembershipHistoryRepository membershipHistory, AuditLogRepository auditLogs,
                           @Value("${app.grouping.max-requests:50}") int maxRequests) {
        this.pickups = pickups; this.strategy = strategy; this.groups = groups; this.memberships = memberships;
        this.membershipHistory = membershipHistory; this.auditLogs = auditLogs; this.maxRequests = maxRequests;
    }

    @Transactional(readOnly = true)
    public List<Suggestion> suggestions() {
        return strategy.partition(pickups.eligible(LocalDate.now())).entrySet().stream()
            .map(entry -> new Suggestion(entry.getKey().zoneId(), entry.getKey().zoneName(), entry.getKey().preferredDate(),
                entry.getValue().stream().map(Candidate::from).toList()))
            .toList();
    }

    @Transactional
    public GroupView confirm(User admin, UUID zoneId, LocalDate preferredDate, List<UUID> requestIds) {
        LinkedHashSet<UUID> uniqueIds = new LinkedHashSet<>(requestIds == null ? List.of() : requestIds);
        if (uniqueIds.isEmpty()) throw new IllegalArgumentException("Select at least one request");
        if (uniqueIds.size() != requestIds.size()) throw new IllegalArgumentException("Duplicate request IDs are not allowed");
        if (uniqueIds.size() > maxRequests) throw new IllegalArgumentException("A group cannot contain more than " + maxRequests + " requests");
        if (preferredDate.isBefore(LocalDate.now())) throw new IllegalArgumentException("Group date cannot be in the past");

        List<PickupRequest> locked = pickups.lock(uniqueIds);
        if (locked.size() != uniqueIds.size()) throw new IllegalArgumentException("One or more requests do not exist");
        for (PickupRequest request : locked) {
            if (request.getStatus() != PickupStatus.PENDING) throw new IllegalStateException("Every request must still be pending");
            if (!request.getZone().getId().equals(zoneId) || !request.getPreferredDate().equals(preferredDate))
                throw new IllegalArgumentException("All requests must match the selected zone and date");
            if (memberships.existsByRequestIdAndActiveTrue(request.getId()))
                throw new IllegalStateException("A request already belongs to an active group");
        }

        CollectionGroup group = groups.save(new CollectionGroup(locked.getFirst().getZone(), preferredDate, admin));
        for (PickupRequest request : locked) {
            GroupMembership membership = memberships.save(new GroupMembership(group, request, admin));
            membershipHistory.save(new GroupMembershipHistory(membership, admin, "Group confirmed"));
            request.markGrouped();
            pickups.save(request);
            pickups.record(new PickupStatusHistory(request, PickupStatus.PENDING, PickupStatus.GROUPED, admin, "Added to " + group.getPublicCode()));
        }
        auditLogs.save(new AuditLog(admin, "GROUP_CONFIRMED", "COLLECTION_GROUP", group.getId(),
            "Confirmed " + locked.size() + " request(s) using " + strategy.getClass().getSimpleName()));
        return GroupView.from(group, locked);
    }

    @Transactional(readOnly = true)
    public List<GroupView> groups() {
        return groups.findAllByOrderByCreatedAtDesc().stream().map(group -> GroupView.from(group,
            memberships.findByGroupIdAndActiveTrueOrderByAddedAt(group.getId()).stream().map(GroupMembership::getRequest).toList())).toList();
    }

    public record Candidate(UUID id, String publicCode, String categoryName, String address, java.math.BigDecimal quantity, String unit) {
        static Candidate from(PickupRequest request) { return new Candidate(request.getId(), request.getPublicCode(), request.getCategory().getName(), request.getAddress(), request.getQuantity(), request.getUnit()); }
    }
    public record Suggestion(UUID zoneId, String zoneName, LocalDate preferredDate, List<Candidate> requests) {}
    public record GroupView(UUID id, String publicCode, UUID zoneId, String zoneName, LocalDate preferredDate,
                            GroupStatus status, List<Candidate> requests) {
        static GroupView from(CollectionGroup group, List<PickupRequest> requests) {
            return new GroupView(group.getId(), group.getPublicCode(), group.getZone().getId(), group.getZone().getName(),
                group.getPreferredDate(), group.getStatus(), requests.stream().map(Candidate::from).toList());
        }
    }
}
