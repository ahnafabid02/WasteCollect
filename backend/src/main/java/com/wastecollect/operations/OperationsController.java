package com.wastecollect.operations;

import com.wastecollect.auth.User;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.util.*;

@RestController
@RequestMapping("/api/v1/admin")
public class OperationsController {
    private final OperationsService operations;
    public OperationsController(OperationsService operations) { this.operations = operations; }

    @GetMapping("/requests")
    public OperationsService.Page requests(@RequestParam(defaultValue="") String query, @RequestParam(required=false) String status,
        @RequestParam(required=false) UUID zoneId, @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="20") int size,
        @RequestParam(defaultValue="createdAt") String sort, @RequestParam(defaultValue="desc") String direction) {
        return operations.search("requests", query, status, zoneId, page, size, sort, direction);
    }
    @GetMapping("/collections")
    public OperationsService.Page groups(@RequestParam(defaultValue="") String query, @RequestParam(required=false) String status,
        @RequestParam(required=false) UUID zoneId, @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="20") int size,
        @RequestParam(defaultValue="createdAt") String sort, @RequestParam(defaultValue="desc") String direction) {
        return operations.search("groups", query, status, zoneId, page, size, sort, direction);
    }
    @GetMapping("/requests/{id}") public Map<String,Object> request(@PathVariable UUID id) { return operations.requestDetail(id); }
    @GetMapping("/dashboard") public Map<String,Object> dashboard() { return operations.dashboard(); }
    @GetMapping("/collectors") public List<Map<String,Object>> collectors() { return operations.collectors(); }
    @GetMapping("/collectors/availability")
    public List<Map<String,Object>> availability(@RequestParam Instant startsAt, @RequestParam Instant endsAt, @RequestParam(required=false) UUID groupId) { return operations.availableCollectors(groupId, startsAt, endsAt); }
    @GetMapping("/groups/{id}/assignments")
    public List<Map<String,Object>> assignments(@PathVariable UUID id) { return operations.assignmentHistory(id); }
    @PatchMapping("/collectors/{id}/status") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void collectorStatus(@AuthenticationPrincipal User actor, @PathVariable UUID id, @Valid @RequestBody StatusInput input) { operations.collectorStatus(actor, id, input.status()); }
    @PatchMapping("/groups/{id}/schedule") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void schedule(@AuthenticationPrincipal User actor, @PathVariable UUID id, @Valid @RequestBody ScheduleInput input) { operations.schedule(actor, id, input.startsAt(), input.endsAt(), input.reason().trim()); }
    @PostMapping("/groups/{id}/assignment") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void assign(@AuthenticationPrincipal User actor, @PathVariable UUID id, @Valid @RequestBody AssignmentInput input) { operations.assign(actor, id, input.collectorId(), input.reason().trim()); }
    @PatchMapping("/groups/{id}/cancel") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@AuthenticationPrincipal User actor, @PathVariable UUID id, @Valid @RequestBody ReasonInput input) { operations.cancel(actor, id, input.reason().trim()); }
    @GetMapping("/audit")
    public OperationsService.Page audit(@RequestParam(required=false) UUID entityId, @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="20") int size) { return operations.auditHistory(entityId, page, size); }
    @GetMapping("/settings") public OperationsService.Settings settings() { return operations.settings(); }
    @PatchMapping("/settings")
    public OperationsService.Settings settings(@AuthenticationPrincipal User actor, @Valid @RequestBody SettingsInput input) { return operations.updateSettings(actor, input.maxGroupRequests(), input.minimumNoticeHours()); }
    public record ScheduleInput(@NotNull Instant startsAt, @NotNull Instant endsAt, @NotBlank @Size(max=500) String reason) {}
    public record AssignmentInput(@NotNull UUID collectorId, @NotBlank @Size(max=500) String reason) {}
    public record ReasonInput(@NotBlank @Size(max=500) String reason) {}
    public record StatusInput(@NotBlank String status) {}
    public record SettingsInput(@Min(1) @Max(500) int maxGroupRequests, @Min(0) @Max(168) int minimumNoticeHours) {}
}
