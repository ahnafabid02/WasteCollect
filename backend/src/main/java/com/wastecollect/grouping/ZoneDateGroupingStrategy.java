package com.wastecollect.grouping;

import com.wastecollect.pickup.PickupRequest;
import org.springframework.stereotype.Component;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class ZoneDateGroupingStrategy implements GroupingStrategy {
    @Override
    public Map<GroupKey, List<PickupRequest>> partition(List<PickupRequest> requests) {
        return requests.stream().collect(Collectors.groupingBy(
            request -> new GroupKey(request.getZone().getId(), request.getZone().getName(), request.getPreferredDate()),
            LinkedHashMap::new, Collectors.toList()));
    }
}
