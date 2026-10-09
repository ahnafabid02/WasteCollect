package com.wastecollect.pickup;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.*;

interface ZoneRepository extends JpaRepository<ServiceZone, UUID> { List<ServiceZone> findByActiveTrueOrderByName(); }
interface CategoryRepository extends JpaRepository<WasteCategory, UUID> { List<WasteCategory> findByActiveTrueOrderByName(); }
interface PickupRequestRepository extends JpaRepository<PickupRequest, UUID> {
    List<PickupRequest> findByResidentIdOrderByCreatedAtDesc(UUID residentId);
    Optional<PickupRequest> findByIdAndResidentId(UUID id, UUID residentId);
    @Query("select p from PickupRequest p where p.status = com.wastecollect.pickup.PickupStatus.PENDING and p.preferredDate >= :today order by p.zone.name, p.preferredDate, p.createdAt")
    List<PickupRequest> findGroupingEligible(LocalDate today);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PickupRequest p where p.id in :ids order by p.id")
    List<PickupRequest> lockAllById(Collection<UUID> ids);
}
interface PickupStatusHistoryRepository extends JpaRepository<PickupStatusHistory, UUID> {
    List<PickupStatusHistory> findByRequestIdOrderByCreatedAtAsc(UUID requestId);
}
