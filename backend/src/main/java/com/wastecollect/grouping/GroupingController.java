package com.wastecollect.grouping;

import com.wastecollect.auth.User;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/groups")
public class GroupingController {
    private final GroupingService grouping;
    public GroupingController(GroupingService grouping) { this.grouping = grouping; }

    @PostMapping("/suggestions")
    public List<GroupingService.Suggestion> suggestions() { return grouping.suggestions(); }

    @PostMapping
    public ResponseEntity<GroupingService.GroupView> confirm(@AuthenticationPrincipal User admin,
                                                              @Valid @RequestBody ConfirmGroupRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(grouping.confirm(admin, request.zoneId(), request.preferredDate(), request.requestIds()));
    }

    @GetMapping
    public List<GroupingService.GroupView> groups() { return grouping.groups(); }

    public record ConfirmGroupRequest(@NotNull UUID zoneId, @NotNull LocalDate preferredDate,
                                      @NotEmpty List<UUID> requestIds) {}
}
