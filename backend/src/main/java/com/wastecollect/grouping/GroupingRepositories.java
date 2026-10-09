package com.wastecollect.grouping;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

interface CollectionGroupRepository extends JpaRepository<CollectionGroup, UUID> {
    List<CollectionGroup> findAllByOrderByCreatedAtDesc();
}
interface GroupMembershipRepository extends JpaRepository<GroupMembership, UUID> {
    List<GroupMembership> findByGroupIdAndActiveTrueOrderByAddedAt(UUID groupId);
    boolean existsByRequestIdAndActiveTrue(UUID requestId);
}
interface GroupMembershipHistoryRepository extends JpaRepository<GroupMembershipHistory, UUID> {}
interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {}
