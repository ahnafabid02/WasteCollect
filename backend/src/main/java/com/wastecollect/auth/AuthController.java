package com.wastecollect.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class AuthController {
    private final AuthService auth;
    private final UserRepository users;
    public AuthController(AuthService auth, UserRepository users) { this.auth = auth; this.users = users; }

    @PostMapping("/auth/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(UserResponse.from(auth.register(request.email(), request.password(), request.displayName())));
    }

    @PostMapping("/auth/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return TokenResponse.from(auth.issueTokens(auth.authenticate(request.email(), request.password())));
    }

    @PostMapping("/auth/refresh")
    public TokenResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return TokenResponse.from(auth.refresh(request.refreshToken()));
    }

    @PostMapping("/auth/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@Valid @RequestBody RefreshRequest request) { auth.logout(request.refreshToken()); }

    @GetMapping("/users/me")
    public UserResponse me(@AuthenticationPrincipal User user) { return UserResponse.from(user); }

    @PatchMapping("/users/me")
    public UserResponse update(@AuthenticationPrincipal User user, @Valid @RequestBody UpdateProfileRequest request) {
        user.updateProfile(request.displayName());
        return UserResponse.from(users.save(user));
    }

    public record RegisterRequest(@NotBlank @Email String email, @NotBlank @Size(min = 12, max = 128) String password,
                                  @NotBlank @Size(max = 120) String displayName) {}
    public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {}
    public record RefreshRequest(@NotBlank String refreshToken) {}
    public record UpdateProfileRequest(@NotBlank @Size(max = 120) String displayName) {}
    public record TokenResponse(String accessToken, String refreshToken) {
        static TokenResponse from(AuthService.TokenPair pair) { return new TokenResponse(pair.accessToken(), pair.refreshToken()); }
    }
    public record UserResponse(UUID id, String email, String displayName, UserRole role, AccountStatus status) {
        static UserResponse from(User user) { return new UserResponse(user.getId(), user.getEmail(), user.getDisplayName(), user.getRole(), user.getStatus()); }
    }
}
