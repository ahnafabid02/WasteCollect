package com.wastecollect.pickup;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Component
public class PickupGroupingGateway {
    private final PickupRequestRepository requests;
    private final PickupStatusHistoryRepository history;

    public PickupGroupingGateway(PickupRequestRepository requests, PickupStatusHistoryRepository history) {
        this.requests = requests;
        this.history = history;
    }

    public List<PickupRequest> eligible(LocalDate today) { return requests.findGroupingEligible(today); }
    public List<PickupRequest> lock(Collection<UUID> ids) { return requests.lockAllById(ids); }
    public PickupRequest save(PickupRequest request) { return requests.save(request); }
    public void record(PickupStatusHistory event) { history.save(event); }
}
