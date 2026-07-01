package com.openclaw.desktop.memory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 记忆数据库 — 基于 SQLite 的记忆存储。
 */
public class MemoryDatabase {

    private static final Logger log = LoggerFactory.getLogger(MemoryDatabase.class);

    private final String dbPath;

    public MemoryDatabase(String dbPath) {
        this.dbPath = dbPath;
    }

    public void initialize() throws SQLException {
        // ensure parent dir exists
        var path = java.nio.file.Paths.get(dbPath);
        if (path.getParent() != null) {
            try {
                java.nio.file.Files.createDirectories(path.getParent());
            } catch (Exception e) {
                log.warn("Failed to create memory dir: {}", e.getMessage());
            }
        }

        try (var conn = getConnection(); var stmt = conn.createStatement()) {
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS memories (
                    id TEXT PRIMARY KEY,
                    content TEXT NOT NULL,
                    embedding BLOB,
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL,
                    session_key TEXT,
                    tags TEXT
                )
            """);
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_memories_session ON memories(session_key)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_memories_created ON memories(created_at)");
            log.info("Memory database initialized at: {}", dbPath);
        }
    }

    public void save(MemoryEntry entry) throws SQLException {
        try (var conn = getConnection();
             var ps = conn.prepareStatement(
                 "INSERT OR REPLACE INTO memories (id, content, created_at, updated_at, session_key, tags) VALUES (?, ?, ?, ?, ?, ?)"
             )) {
            ps.setString(1, entry.id());
            ps.setString(2, entry.content());
            ps.setLong(3, entry.createdAt().toEpochMilli());
            ps.setLong(4, entry.updatedAt().toEpochMilli());
            ps.setString(5, entry.sessionKey());
            ps.setString(6, entry.tags() != null ? String.join(",", entry.tags()) : null);
            ps.executeUpdate();
        }
    }

    public Optional<MemoryEntry> findById(String id) throws SQLException {
        try (var conn = getConnection();
             var ps = conn.prepareStatement("SELECT * FROM memories WHERE id = ?")) {
            ps.setString(1, id);
            var rs = ps.executeQuery();
            if (rs.next()) {
                return Optional.of(toEntry(rs));
            }
            return Optional.empty();
        }
    }

    public List<MemoryEntry> search(String query, int limit) throws SQLException {
        try (var conn = getConnection();
             var ps = conn.prepareStatement(
                 "SELECT * FROM memories WHERE content LIKE ? ORDER BY created_at DESC LIMIT ?"
             )) {
            ps.setString(1, "%" + query + "%");
            ps.setInt(2, limit);
            var rs = ps.executeQuery();
            var results = new ArrayList<MemoryEntry>();
            while (rs.next()) {
                results.add(toEntry(rs));
            }
            return results;
        }
    }

    public List<MemoryEntry> recent(int limit) throws SQLException {
        try (var conn = getConnection();
             var ps = conn.prepareStatement(
                 "SELECT * FROM memories ORDER BY created_at DESC LIMIT ?"
             )) {
            ps.setInt(1, limit);
            var rs = ps.executeQuery();
            var results = new ArrayList<MemoryEntry>();
            while (rs.next()) {
                results.add(toEntry(rs));
            }
            return results;
        }
    }

    public void delete(String id) throws SQLException {
        try (var conn = getConnection();
             var ps = conn.prepareStatement("DELETE FROM memories WHERE id = ?")) {
            ps.setString(1, id);
            ps.executeUpdate();
        }
    }

    // ---- internal ----

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + dbPath);
    }

    private static MemoryEntry toEntry(ResultSet rs) throws SQLException {
        var tagsStr = rs.getString("tags");
        return new MemoryEntry(
            rs.getString("id"),
            rs.getString("content"),
            rs.getString("session_key"),
            tagsStr != null ? tagsStr.split(",") : null,
            Instant.ofEpochMilli(rs.getLong("created_at")),
            Instant.ofEpochMilli(rs.getLong("updated_at"))
        );
    }
}
