package com.wastecollect;

import com.wastecollect.auth.User;
import com.wastecollect.auth.UserRepository;
import com.wastecollect.grouping.GroupingService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
class GroupingConcurrencyTests {
    private static final UUID ZONE = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID CATEGORY = UUID.fromString("00000000-0000-0000-0000-000000000001");
    @Autowired GroupingService grouping;
    @Autowired UserRepository users;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwords;
    private UUID adminId;
    private UUID residentId;
    private UUID requestId;

    @Test
    void concurrentConfirmationsCannotCreateDuplicateActiveMemberships() throws Exception {
        adminId = UUID.randomUUID(); residentId = UUID.randomUUID(); requestId = UUID.randomUUID();
        LocalDate date = LocalDate.now().plusDays(3);
        jdbc.update("INSERT INTO app_users(id,email,password_hash,display_name,role,status,created_at,updated_at) VALUES (?,?,?,?,?,'ACTIVE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)", adminId, "concurrent-admin-" + adminId + "@example.com", passwords.encode("admin-password-123"), "Admin", "ADMIN");
        jdbc.update("INSERT INTO app_users(id,email,password_hash,display_name,role,status,created_at,updated_at) VALUES (?,?,?,?,?,'ACTIVE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)", residentId, "concurrent-resident-" + residentId + "@example.com", passwords.encode("resident-password-123"), "Resident", "RESIDENT");
        jdbc.update("INSERT INTO pickup_requests(id,public_code,resident_id,service_zone_id,waste_category_id,address,quantity,unit,preferred_date,status,created_at,updated_at) VALUES (?,?,?,?,?,?,?,?,?,'PENDING',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)", requestId, "WC-" + requestId.toString().substring(0, 8), residentId, ZONE, CATEGORY, "Concurrency Test Road", BigDecimal.ONE, "BAG", date);
        User admin = users.findById(adminId).orElseThrow();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger conflicts = new AtomicInteger();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        Callable<Void> attempt = () -> {
            ready.countDown(); start.await(5, TimeUnit.SECONDS);
            try { grouping.confirm(admin, ZONE, date, List.of(requestId)); successes.incrementAndGet(); }
            catch (IllegalStateException exception) { conflicts.incrementAndGet(); }
            return null;
        };
        Future<Void> first = executor.submit(attempt);
        Future<Void> second = executor.submit(attempt);
        ready.await(5, TimeUnit.SECONDS); start.countDown();
        first.get(10, TimeUnit.SECONDS); second.get(10, TimeUnit.SECONDS);
        executor.shutdownNow();

        assertEquals(1, successes.get());
        assertEquals(1, conflicts.get());
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM group_memberships WHERE pickup_request_id=? AND active", Integer.class, requestId));
    }

    @AfterEach
    void cleanUp() {
        if (requestId == null) return;
        jdbc.update("DELETE FROM group_membership_history WHERE membership_id IN (SELECT id FROM group_memberships WHERE pickup_request_id=?)", requestId);
        jdbc.update("DELETE FROM group_memberships WHERE pickup_request_id=?", requestId);
        jdbc.update("DELETE FROM pickup_status_history WHERE pickup_request_id=?", requestId);
        jdbc.update("DELETE FROM audit_logs WHERE actor_id=?", adminId);
        jdbc.update("DELETE FROM collection_groups WHERE created_by=?", adminId);
        jdbc.update("DELETE FROM pickup_requests WHERE id=?", requestId);
        jdbc.update("DELETE FROM app_users WHERE id IN (?,?)", adminId, residentId);
    }
}
