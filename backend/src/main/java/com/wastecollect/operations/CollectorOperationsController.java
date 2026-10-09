package com.wastecollect.operations;

import com.wastecollect.auth.User;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/api/v1/collector")
public class CollectorOperationsController {
    private final CollectorOperationsService operations;

    public CollectorOperationsController(CollectorOperationsService operations) {
        this.operations = operations;
    }

    @GetMapping("/dashboard")
    public Map<String, Object> dashboard(@AuthenticationPrincipal User collector) {
        return operations.dashboard(collector);
    }

    @GetMapping("/groups")
    public List<Map<String, Object>> groups(@AuthenticationPrincipal User collector,
                                            @RequestParam(required = false) LocalDate date,
                                            @RequestParam(required = false) String status) {
        return operations.groups(collector, date, status);
    }

    @GetMapping("/groups/{id}")
    public Map<String, Object> group(@AuthenticationPrincipal User collector, @PathVariable UUID id) {
        return operations.group(collector, id);
    }

    @PostMapping("/groups/{id}/start")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void start(@AuthenticationPrincipal User collector, @PathVariable UUID id) {
        operations.start(collector, id);
    }

    @PostMapping("/groups/{id}/attempts")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void attempt(@AuthenticationPrincipal User collector, @PathVariable UUID id,
                        @Valid @RequestBody AttemptInput input) {
        operations.attempt(collector, id, input.requestId(), input.outcome(), input.reason().trim(), input.retryAt());
    }

    @PostMapping("/groups/{id}/requests/{requestId}/retry")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void retry(@AuthenticationPrincipal User collector, @PathVariable UUID id, @PathVariable UUID requestId,
                      @Valid @RequestBody ReasonInput input) {
        operations.retry(collector, id, requestId, input.reason().trim());
    }

    public record AttemptInput(@NotNull UUID requestId, @NotBlank String outcome,
                               @NotBlank @Size(max = 500) String reason, Instant retryAt) {}
    public record ReasonInput(@NotBlank @Size(max = 500) String reason) {}
}
