package com.openclaw.desktop.cron;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Cron 任务持久化存储测试。
 */
class CronJobStoreTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("save and loadAll round-trip")
    void testSaveAndLoad() throws Exception {
        var store = new CronJobStore(tempDir.resolve("cron.db").toString());
        store.initialize();

        var job = CronJob.create("test", CronSchedule.every(60000), CronPayload.systemEvent("hi"));
        store.save(job);

        var loaded = store.loadAll();
        assertEquals(1, loaded.size());
        assertEquals("test", loaded.get(0).name());
        assertEquals(0, loaded.get(0).runCount());
    }

    @Test
    @DisplayName("Cron schedule serialization round-trip")
    void testCronScheduleRoundTrip() throws Exception {
        var store = new CronJobStore(tempDir.resolve("cron2.db").toString());
        store.initialize();

        var job = CronJob.create("cron-job", CronSchedule.parse("0 0 9 * * ?"),
            CronPayload.agentTurn("hello", "gpt-4o"));
        store.save(job);

        var loaded = store.loadAll();
        assertEquals(1, loaded.size());
        assertInstanceOf(CronSchedule.Cron.class, loaded.get(0).schedule());
        assertEquals("0 0 9 * * ?", ((CronSchedule.Cron) loaded.get(0).schedule()).expr());
        assertEquals("hello", loaded.get(0).payload().message());
    }

    @Test
    @DisplayName("At schedule serialization")
    void testAtSchedule() throws Exception {
        var store = new CronJobStore(tempDir.resolve("cron3.db").toString());
        store.initialize();

        var at = Instant.now().plusSeconds(3600);
        var job = CronJob.create("at-job", CronSchedule.at(at), CronPayload.systemEvent("once"));
        store.save(job);

        var loaded = store.loadAll();
        assertEquals(1, loaded.size());
        assertInstanceOf(CronSchedule.At.class, loaded.get(0).schedule());
    }

    @Test
    @DisplayName("delete removes the job")
    void testDelete() throws Exception {
        var store = new CronJobStore(tempDir.resolve("cron4.db").toString());
        store.initialize();

        var job = CronJob.create("to-delete", CronSchedule.every(60000), CronPayload.systemEvent("x"));
        store.save(job);
        assertEquals(1, store.count());

        assertTrue(store.delete(job.id()));
        assertEquals(0, store.count());
    }

    @Test
    @DisplayName("findById locates a saved job")
    void testFindById() throws Exception {
        var store = new CronJobStore(tempDir.resolve("cron5.db").toString());
        store.initialize();

        var job = CronJob.create("find-me", CronSchedule.every(60000), CronPayload.systemEvent("x"));
        store.save(job);

        var found = store.findById(job.id());
        assertTrue(found.isPresent());
        assertEquals("find-me", found.get().name());
    }
}
