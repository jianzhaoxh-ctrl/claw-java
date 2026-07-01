package com.openclaw.desktop;

import com.openclaw.desktop.cron.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Cron 系统测试。
 */
class CronSystemTest {

    @Test
    @DisplayName("CronScheduler should add and list jobs")
    void testAddAndList() {
        var executor = new CronExecutor() {
            final AtomicInteger count = new AtomicInteger(0);
            @Override
            public void execute(CronJob job) {
                count.incrementAndGet();
            }
        };

        var scheduler = new CronScheduler(executor);
        var job = CronJob.create("test-job",
            CronSchedule.every(60000),
            CronPayload.systemEvent("test"));

        var added = scheduler.add(job);
        assertNotNull(added.id());
        assertEquals(1, scheduler.list().size());

        scheduler.remove(added.id());
        assertEquals(0, scheduler.list().size());
    }

    @Test
    @DisplayName("CronSchedule.every should calculate next run correctly")
    void testEverySchedule() {
        var executor = (CronExecutor) job -> {};
        var scheduler = new CronScheduler(executor);

        var job = CronJob.create("test",
            CronSchedule.every(3600000), // 1 hour
            CronPayload.systemEvent("test"));

        var added = scheduler.add(job);
        assertNotNull(added.nextRunAt());
        assertTrue(added.nextRunAt().isAfter(Instant.now()));
    }
}
