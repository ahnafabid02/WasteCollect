package com.wastecollect.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import com.wastecollect.operations.OperationsService;

@RestController
@RequestMapping("/api/v1/admin/users")
public class AdminProvisioningController {
    private final AuthService auth;
    private final OperationsService operations;
    public AdminProvisioningController(AuthService auth, OperationsService operations) { this.auth = auth; this.operations = operations; }

    @PostMapping("/collectors")
    @Transactional
    public ResponseEntity<AuthController.UserResponse> createCollector(@AuthenticationPrincipal User actor, @Valid @RequestBody CreateCollectorRequest request) {
        User collector = auth.provision(request.email(), request.temporaryPassword(), request.displayName(), UserRole.COLLECTOR);
        operations.audit(actor, "COLLECTOR_CREATED", "USER", collector.getId(), collector.getDisplayName());
        return ResponseEntity.status(HttpStatus.CREATED).body(AuthController.UserResponse.from(collector));
    }

    public record CreateCollectorRequest(@NotBlank @Email String email,
                                         @NotBlank @Size(min = 12, max = 128) String temporaryPassword,
                                         @NotBlank @Size(max = 120) String displayName) {}
}
