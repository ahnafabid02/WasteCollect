package com.wastecollect.pickup;

import com.wastecollect.auth.User;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/api/v1")
public class PickupController {
    private final ZoneRepository zones;
    private final CategoryRepository categories;
    private final PickupRequestRepository requests;
    private final PickupStatusHistoryRepository history;
    public PickupController(ZoneRepository zones, CategoryRepository categories, PickupRequestRepository requests, PickupStatusHistoryRepository history) {
        this.zones = zones; this.categories = categories; this.requests = requests; this.history = history;
    }

    @GetMapping("/zones")
    public List<ZoneResponse> zones() { return zones.findByActiveTrueOrderByName().stream().map(z -> new ZoneResponse(z.getId(), z.getCode(), z.getName())).toList(); }

    @GetMapping("/waste-categories")
    public List<CategoryResponse> categories() { return categories.findByActiveTrueOrderByName().stream().map(c -> new CategoryResponse(c.getId(), c.getCode(), c.getName(), c.getAllowedUnit())).toList(); }

    @PostMapping("/requests")
    public ResponseEntity<RequestResponse> create(@AuthenticationPrincipal User user, @Valid @RequestBody CreateRequest input) {
        if (input.preferredDate().isBefore(LocalDate.now())) throw new IllegalArgumentException("Preferred date cannot be in the past");
        ServiceZone zone = zones.findById(input.zoneId()).orElseThrow(() -> new IllegalArgumentException("Unknown service zone"));
        WasteCategory category = categories.findById(input.categoryId()).orElseThrow(() -> new IllegalArgumentException("Unknown waste category"));
        if (!category.getAllowedUnit().equals(input.unit())) throw new IllegalArgumentException("Unit is not permitted for this waste category");
        PickupRequest request = requests.save(new PickupRequest(user, zone, category, input.address().trim(), input.quantity(), input.unit(), input.preferredDate(), input.notes()));
        history.save(new PickupStatusHistory(request, null, PickupStatus.PENDING, user, "Request created"));
        return ResponseEntity.status(HttpStatus.CREATED).body(RequestResponse.from(request));
    }

    @GetMapping("/requests/my")
    public List<RequestResponse> mine(@AuthenticationPrincipal User user) {
        return requests.findByResidentIdOrderByCreatedAtDesc(user.getId()).stream().map(RequestResponse::from).toList();
    }

    @GetMapping("/requests/{id}")
    public RequestResponse detail(@AuthenticationPrincipal User user, @PathVariable UUID id) {
        return RequestResponse.from(requests.findByIdAndResidentId(id, user.getId()).orElseThrow(() -> new NoSuchElementException("Request not found")));
    }

    @PatchMapping("/requests/{id}/cancel")
    public RequestResponse cancel(@AuthenticationPrincipal User user, @PathVariable UUID id) {
        PickupRequest request = requests.findByIdAndResidentId(id, user.getId()).orElseThrow(() -> new NoSuchElementException("Request not found"));
        PickupStatus previous = request.getStatus(); request.cancel(); requests.save(request);
        history.save(new PickupStatusHistory(request, previous, PickupStatus.CANCELLED, user, "Cancelled by resident"));
        return RequestResponse.from(request);
    }

    public record CreateRequest(@NotNull UUID zoneId, @NotNull UUID categoryId, @NotBlank @Size(max = 300) String address,
                                @NotNull @DecimalMin("0.01") @Digits(integer = 8, fraction = 2) BigDecimal quantity,
                                @NotBlank String unit, @NotNull LocalDate preferredDate, @Size(max = 1000) String notes) {}
    public record ZoneResponse(UUID id, String code, String name) {}
    public record CategoryResponse(UUID id, String code, String name, String allowedUnit) {}
    public record RequestResponse(UUID id, String publicCode, UUID zoneId, String zoneName, UUID categoryId, String categoryName,
                                  String address, BigDecimal quantity, String unit, LocalDate preferredDate, String notes, PickupStatus status) {
        static RequestResponse from(PickupRequest r) { return new RequestResponse(r.getId(), r.getPublicCode(), r.getZone().getId(), r.getZone().getName(),
            r.getCategory().getId(), r.getCategory().getName(), r.getAddress(), r.getQuantity(), r.getUnit(), r.getPreferredDate(), r.getNotes(), r.getStatus()); }
    }
}
