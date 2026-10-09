package com.wastecollect.auth;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import javax.crypto.SecretKey;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.UUID;

@Service
public class AuthService {
    private final UserRepository users;
    private final RefreshSessionRepository sessions;
    private final PasswordEncoder passwordEncoder;
    private final SecretKey signingKey;
    private final long accessMinutes;
    private final long refreshDays;

    public AuthService(UserRepository users, RefreshSessionRepository sessions, PasswordEncoder passwordEncoder,
                       @Value("${app.security.jwt-secret}") String secret,
                       @Value("${app.security.access-token-minutes}") long accessMinutes,
                       @Value("${app.security.refresh-token-days}") long refreshDays) {
        this.users = users; this.sessions = sessions; this.passwordEncoder = passwordEncoder;
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessMinutes = accessMinutes; this.refreshDays = refreshDays;
    }

    @Transactional
    public User register(String email, String password, String displayName) {
        String normalized = email.trim().toLowerCase();
        if (users.existsByEmailIgnoreCase(normalized)) throw new IllegalArgumentException("Email is already registered");
        return users.save(new User(normalized, passwordEncoder.encode(password), displayName.trim()));
    }

    public User authenticate(String email, String password) {
        User user = users.findByEmailIgnoreCase(email.trim()).orElseThrow(() -> new IllegalArgumentException("Invalid credentials"));
        if (user.getStatus() != AccountStatus.ACTIVE || !passwordEncoder.matches(password, user.getPasswordHash()))
            throw new IllegalArgumentException("Invalid credentials");
        return user;
    }

    @Transactional
    public User provision(String email, String password, String displayName, UserRole role) {
        String normalized = email.trim().toLowerCase();
        if (users.existsByEmailIgnoreCase(normalized)) throw new IllegalArgumentException("Email is already registered");
        if (role == UserRole.RESIDENT) throw new IllegalArgumentException("Residents must use self-registration");
        return users.save(new User(normalized, passwordEncoder.encode(password), displayName.trim(), role));
    }

    @Transactional
    public TokenPair issueTokens(User user) {
        Instant now = Instant.now();
        String access = Jwts.builder().subject(user.getId().toString()).claim("role", user.getRole().name())
                .issuedAt(java.util.Date.from(now)).expiration(java.util.Date.from(now.plus(accessMinutes, ChronoUnit.MINUTES)))
                .signWith(signingKey).compact();
        String refresh = UUID.randomUUID() + "." + UUID.randomUUID();
        sessions.save(new RefreshSession(user, hash(refresh), now.plus(refreshDays, ChronoUnit.DAYS)));
        return new TokenPair(access, refresh);
    }

    @Transactional
    public TokenPair refresh(String rawToken) {
        RefreshSession session = sessions.findByTokenHash(hash(rawToken)).orElseThrow(() -> new IllegalArgumentException("Invalid refresh token"));
        if (!session.isUsable()) throw new IllegalArgumentException("Refresh token is expired or revoked");
        session.revoke();
        return issueTokens(session.getUser());
    }

    @Transactional
    public void logout(String rawToken) {
        sessions.findByTokenHash(hash(rawToken)).ifPresent(RefreshSession::revoke);
    }

    public String userId(String token) {
        return Jwts.parser().verifyWith(signingKey).build().parseSignedClaims(token).getPayload().getSubject();
    }

    private String hash(String raw) {
        try { return Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception e) { throw new IllegalStateException("Unable to hash session token", e); }
    }

    public record TokenPair(String accessToken, String refreshToken) {}
}
