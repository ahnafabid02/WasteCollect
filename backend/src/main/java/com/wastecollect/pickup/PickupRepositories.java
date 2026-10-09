package com.wastecollect.pickup;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

interface ZoneRepository extends JpaRepository<ServiceZone, UUID> { List<ServiceZone> findByActiveTrueOrderByName(); }
interface CategoryRepository extends JpaRepository<WasteCategory, UUID> { List<WasteCategory> findByActiveTrueOrderByName(); }
interface PickupRequestRepository extends JpaRepository<PickupRequest, UUID> { List<PickupRequest> findByResidentIdOrderByCreatedAtDesc(UUID residentId); Optional<PickupRequest> findByIdAndResidentId(UUID id, UUID residentId); }
interface PickupStatusHistoryRepository extends JpaRepository<PickupStatusHistory, UUID> {}
