package com.openclaw.desktop.memory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MemoryDatabase 单元测试。
 */
class MemoryDatabaseTest {

    @TempDir
    Path tempDir;

    private MemoryDatabase db;

    @BeforeEach
    void setUp() throws Exception {
        db = new MemoryDatabase(tempDir.resolve("test-memory.db").toString());
        db.initialize();
    }

    @Test
    @DisplayName("save and findById round-trip")
    void testSaveAndFindById() throws Exception {
        var entry = new MemoryEntry("id1", "hello world", "session1",
            new String[]{"tag1", "tag2"}, Instant.now(), Instant.now());
        db.save(entry);

        var found = db.findById("id1");
        assertTrue(found.isPresent());
        assertEquals("hello world", found.get().content());
        assertEquals("session1", found.get().sessionKey());
        assertArrayEquals(new String[]{"tag1", "tag2"}, found.get().tags());
    }

    @Test
    @DisplayName("save upserts existing entry")
    void testUpsert() throws Exception {
        var entry = new MemoryEntry("id1", "original", null, null, Instant.now(), Instant.now());
        db.save(entry);

        var updated = new MemoryEntry("id1", "updated content", null, null,
            entry.createdAt(), Instant.now());
        db.save(updated);

        var found = db.findById("id1");
        assertTrue(found.isPresent());
        assertEquals("updated content", found.get().content());
    }

    @Test
    @DisplayName("search by keyword returns matching entries")
    void testSearch() throws Exception {
        db.save(new MemoryEntry("1", "hello world", null, null, Instant.now(), Instant.now()));
        db.save(new MemoryEntry("2", "goodbye world", null, null, Instant.now(), Instant.now()));
        db.save(new MemoryEntry("3", "unrelated text", null, null, Instant.now(), Instant.now()));

        var results = db.search("world", 10);
        assertEquals(2, results.size());
    }

    @Test
    @DisplayName("search respects limit")
    void testSearchLimit() throws Exception {
        for (int i = 0; i < 5; i++) {
            db.save(new MemoryEntry("id" + i, "common word " + i, null, null, Instant.now(), Instant.now()));
        }
        var results = db.search("common", 2);
        assertEquals(2, results.size());
    }

    @Test
    @DisplayName("search returns empty for no match")
    void testSearchNoMatch() throws Exception {
        db.save(new MemoryEntry("1", "hello", null, null, Instant.now(), Instant.now()));
        var results = db.search("nonexistent", 10);
        assertTrue(results.isEmpty());
    }

    @Test
    @DisplayName("recent returns entries ordered by created_at desc")
    void testRecent() throws Exception {
        var t1 = Instant.parse("2026-01-01T00:00:00Z");
        var t2 = Instant.parse("2026-06-01T00:00:00Z");
        var t3 = Instant.parse("2026-03-01T00:00:00Z");
        db.save(new MemoryEntry("1", "old", null, null, t1, t1));
        db.save(new MemoryEntry("2", "newest", null, null, t2, t2));
        db.save(new MemoryEntry("3", "middle", null, null, t3, t3));

        var results = db.recent(3);
        assertEquals(3, results.size());
        assertEquals("newest", results.get(0).content());
    }

    @Test
    @DisplayName("recent respects limit")
    void testRecentLimit() throws Exception {
        for (int i = 0; i < 5; i++) {
            db.save(new MemoryEntry("id" + i, "entry " + i, null, null,
                Instant.now().plusSeconds(i), Instant.now()));
        }
        assertEquals(2, db.recent(2).size());
    }

    @Test
    @DisplayName("delete removes entry")
    void testDelete() throws Exception {
        db.save(new MemoryEntry("1", "to delete", null, null, Instant.now(), Instant.now()));
        assertTrue(db.findById("1").isPresent());

        db.delete("1");
        assertTrue(db.findById("1").isEmpty());
    }

    @Test
    @DisplayName("findById returns empty for non-existent")
    void testFindByIdNonExistent() throws Exception {
        assertTrue(db.findById("nonexistent").isEmpty());
    }

    @Test
    @DisplayName("save entry with null tags")
    void testSaveNullTags() throws Exception {
        var entry = new MemoryEntry("1", "content", "session", null, Instant.now(), Instant.now());
        db.save(entry);
        var found = db.findById("1");
        assertTrue(found.isPresent());
        assertNull(found.get().tags());
    }

    @Test
    @DisplayName("initialize creates table without error on re-init")
    void testReInit() throws Exception {
        // 再次初始化不应抛异常
        db.initialize();
        db.initialize();
    }
}
