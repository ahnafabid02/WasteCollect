package com.wastecollect.grouping;

import com.wastecollect.pickup.PickupRequest;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface GroupingStrategy {
    Map<GroupKey, List<PickupRequest>> partition(List<PickupRequest> requests);
    record GroupKey(UUID zoneId, String zoneName, LocalDate preferredDate) {}
}
