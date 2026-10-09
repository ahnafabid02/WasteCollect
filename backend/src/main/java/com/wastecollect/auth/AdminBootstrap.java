package com.wastecollect.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class AdminBootstrap implements ApplicationRunner {
    private final AuthService auth;
    private final UserRepository users;
    private final String email;
    private final String password;
    private final String displayName;

    public AdminBootstrap(AuthService auth, UserRepository users,
                          @Value("${app.bootstrap.admin-email:}") String email,
                          @Value("${app.bootstrap.admin-password:}") String password,
                          @Value("${app.bootstrap.admin-name:Local Administrator}") String displayName) {
        this.auth = auth; this.users = users; this.email = email; this.password = password; this.displayName = displayName;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (email.isBlank() && password.isBlank()) return;
        if (email.isBlank() || password.length() < 12)
            throw new IllegalStateException("Bootstrap admin email and a password of at least 12 characters are both required");
        if (!users.existsByEmailIgnoreCase(email.trim())) auth.provision(email, password, displayName, UserRole.ADMIN);
    }
}
