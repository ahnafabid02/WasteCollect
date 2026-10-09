package com.wastecollect;

import com.wastecollect.auth.User;
import com.wastecollect.auth.UserRepository;
import com.wastecollect.operations.OperationsService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class AssignmentConcurrencyTests {
    @Autowired OperationsService operations;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder passwords;
    private OperationsTestData data;
    @Test void concurrentOverlappingAssignmentsCommitOnlyOne() throws Exception {
        data = new OperationsTestData(jdbc, passwords.encode("test-password-123"));
        UUID first = data.group(true), second = data.group(true);
        User admin = users.findById(data.admin).orElseThrow();
        CountDownLatch ready = new CountDownLatch(2), start = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger(), conflicts = new AtomicInteger();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Callable<Void> one = attempt(admin, first, ready, start, successes, conflicts);
            Callable<Void> two = attempt(admin, second, ready, start, successes, conflicts);
            Future<Void> a = executor.submit(one), b = executor.submit(two);
            assertTrue(ready.await(5, TimeUnit.SECONDS)); start.countDown();
            a.get(10, TimeUnit.SECONDS); b.get(10, TimeUnit.SECONDS);
            assertEquals(1, successes.get()); assertEquals(1, conflicts.get());
            assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM collector_assignments WHERE collector_id=? AND closed_at IS NULL", Integer.class, data.collector));
            assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE actor_id=? AND action='COLLECTOR_ASSIGNED'", Integer.class, data.admin));
        } finally { start.countDown(); executor.shutdownNow(); }
    }
    private Callable<Void> attempt(User admin, UUID group, CountDownLatch ready, CountDownLatch start, AtomicInteger successes, AtomicInteger conflicts) {
        return () -> { ready.countDown(); if (!start.await(5, TimeUnit.SECONDS)) throw new TimeoutException();
            try { operations.assign(admin, group, data.collector, "Concurrent assignment"); successes.incrementAndGet(); }
            catch (IllegalStateException expected) { conflicts.incrementAndGet(); }
            return null;
        };
    }
    @AfterEach void cleanup() { if (data != null) data.cleanup(); }
}
