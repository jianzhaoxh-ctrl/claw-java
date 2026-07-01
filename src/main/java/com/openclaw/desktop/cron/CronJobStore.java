package com.openclaw.desktop.cron;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Cron 任务持久化存储 — 基于 SQLite。
 *
 * <p>负责 CronJob 的保存、加载、删除，使定时任务在应用重启后能恢复。
 * {@link CronSchedule}（sealed）与 {@link CronPayload}（record）序列化为 JSON 存储。
 */
public class CronJobStore {

    private static final Logger log = LoggerFactory.getLogger(CronJobStore.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String dbPath;

    public CronJobStore(String dbPath) {
        this.dbPath = dbPath;
    }

    /** 初始化表结构。 */
    public void initialize() throws SQLException {
        var path = java.nio.file.Paths.get(dbPath);
        if (path.getParent() != null) {
            try {
                java.nio.file.Files.createDirectories(path.getParent());
            } catch (Exception e) {
                log.warn("Failed to create cron db dir: {}", e.getMessage());
            }
        }
        try (var conn = getConnection(); var stmt = conn.createStatement()) {
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS cron_jobs (
                    id TEXT PRIMARY KEY,
                    name TEXT NOT NULL,
                    schedule TEXT NOT NULL,
                    payload TEXT NOT NULL,
                    enabled INTEGER NOT NULL,
                    created_at INTEGER NOT NULL,
                    last_run_at INTEGER,
                    next_run_at INTEGER,
                    run_count INTEGER NOT NULL,
                    failure_count INTEGER NOT NULL
                )
            """);
            log.info("CronJobStore initialized at: {}", dbPath);
        }
    }

    /** 保存或更新任务（upsert）。 */
    public void save(CronJob job) throws SQLException {
        try (var conn = getConnection();
             var ps = conn.prepareStatement("""
                 INSERT OR REPLACE INTO cron_jobs
                 (id, name, schedule, payload, enabled, created_at, last_run_at, next_run_at, run_count, failure_count)
                 VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                 """)) {
            ps.setString(1, job.id());
            ps.setString(2, job.name());
            ps.setString(3, serializeSchedule(job.schedule()));
            ps.setString(4, serializePayload(job.payload()));
            ps.setInt(5, job.enabled() ? 1 : 0);
            ps.setLong(6, job.createdAt().toEpochMilli());
            ps.setObject(7, job.lastRunAt() != null ? job.lastRunAt().toEpochMilli() : null);
            ps.setObject(8, job.nextRunAt() != null ? job.nextRunAt().toEpochMilli() : null);
            ps.setInt(9, job.runCount());
            ps.setInt(10, job.failureCount());
            ps.executeUpdate();
        }
    }

    /** 加载所有任务。 */
    public List<CronJob> loadAll() throws SQLException {
        try (var conn = getConnection();
             var ps = conn.prepareStatement("SELECT * FROM cron_jobs");
             var rs = ps.executeQuery()) {
            var jobs = new ArrayList<CronJob>();
            while (rs.next()) {
                try {
                    jobs.add(toJob(rs));
                } catch (Exception e) {
                    log.error("Failed to deserialize cron job id={}: {}", rs.getString("id"), e.getMessage());
                }
            }
            return jobs;
        }
    }

    /** 按 ID 加载。 */
    public Optional<CronJob> findById(String id) throws SQLException {
        try (var conn = getConnection();
             var ps = conn.prepareStatement("SELECT * FROM cron_jobs WHERE id = ?")) {
            ps.setString(1, id);
            var rs = ps.executeQuery();
            if (rs.next()) {
                try { return Optional.of(toJob(rs)); }
                catch (Exception e) { log.error("Failed to deserialize cron job {}: {}", id, e.getMessage()); }
            }
            return Optional.empty();
        }
    }

    /** 删除任务。 */
    public boolean delete(String id) throws SQLException {
        try (var conn = getConnection();
             var ps = conn.prepareStatement("DELETE FROM cron_jobs WHERE id = ?")) {
            ps.setString(1, id);
            return ps.executeUpdate() > 0;
        }
    }

    /** 更新运行结果（原子更新，避免覆盖 schedule/name）。 */
    public void updateRunResult(String id, boolean success, Instant runAt, Instant nextRunAt) throws SQLException {
        try (var conn = getConnection();
             var ps = conn.prepareStatement("""
                 UPDATE cron_jobs SET
                   last_run_at = ?,
                   next_run_at = ?,
                   run_count = run_count + 1,
                   failure_count = failure_count + ?
                 WHERE id = ?
                 """)) {
            ps.setLong(1, runAt.toEpochMilli());
            ps.setObject(2, nextRunAt != null ? nextRunAt.toEpochMilli() : null);
            ps.setInt(3, success ? 0 : 1);
            ps.setString(4, id);
            ps.executeUpdate();
        }
    }

    public int count() throws SQLException {
        try (var conn = getConnection();
             var ps = conn.prepareStatement("SELECT COUNT(*) FROM cron_jobs");
             var rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    // ---- 序列化 ----

    private static String serializeSchedule(CronSchedule s) {
        var node = MAPPER.createObjectNode();
        switch (s) {
            case CronSchedule.At at -> {
                node.put("type", "at");
                node.put("at", at.at().toEpochMilli());
            }
            case CronSchedule.Every every -> {
                node.put("type", "every");
                node.put("everyMs", every.everyMs());
                if (every.anchorMs() != null) node.put("anchorMs", every.anchorMs());
            }
            case CronSchedule.Cron cron -> {
                node.put("type", "cron");
                node.put("expr", cron.expr());
                if (cron.tz() != null) node.put("tz", cron.tz());
            }
        }
        return node.toString();
    }

    private static CronSchedule deserializeSchedule(String json) throws Exception {
        var node = (JsonNode) MAPPER.readTree(json);
        var type = node.path("type").asText();
        return switch (type) {
            case "at" -> new CronSchedule.At(Instant.ofEpochMilli(node.path("at").asLong()));
            case "every" -> {
                var everyMs = node.path("everyMs").asLong();
                var anchorMs = node.has("anchorMs") ? node.path("anchorMs").asLong() : null;
                yield new CronSchedule.Every(everyMs, anchorMs);
            }
            case "cron" -> {
                var expr = node.path("expr").asText();
                var tz = node.has("tz") ? node.path("tz").asText() : null;
                yield new CronSchedule.Cron(expr, tz);
            }
            default -> throw new IllegalArgumentException("Unknown schedule type: " + type);
        };
    }

    private static String serializePayload(CronPayload p) {
        try { return MAPPER.writeValueAsString(p); }
        catch (Exception e) { throw new RuntimeException("Failed to serialize payload", e); }
    }

    private static CronPayload deserializePayload(String json) throws Exception {
        return MAPPER.readValue(json, CronPayload.class);
    }

    private static CronJob toJob(ResultSet rs) throws Exception {
        var id = rs.getString("id");
        var name = rs.getString("name");
        var schedule = deserializeSchedule(rs.getString("schedule"));
        var payload = deserializePayload(rs.getString("payload"));
        var enabled = rs.getInt("enabled") == 1;
        var createdAt = Instant.ofEpochMilli(rs.getLong("created_at"));
        var lastRunAt = rs.getObject("last_run_at") != null
            ? Instant.ofEpochMilli(rs.getLong("last_run_at")) : null;
        var nextRunAt = rs.getObject("next_run_at") != null
            ? Instant.ofEpochMilli(rs.getLong("next_run_at")) : null;
        var runCount = rs.getInt("run_count");
        var failureCount = rs.getInt("failure_count");
        return new CronJob(id, name, schedule, payload, enabled, createdAt,
            lastRunAt, nextRunAt, runCount, failureCount);
    }

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + dbPath);
    }
}
