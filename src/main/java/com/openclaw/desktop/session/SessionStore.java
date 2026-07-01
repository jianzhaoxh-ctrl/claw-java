package com.openclaw.desktop.session;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * 会话存储 — 磁盘持久化、压缩归档、自动恢复。
 * 对应 OpenClaw 的 session-archive + session-transcript-files。
 *
 * 功能：
 * - 保存会话到 ~/.clawdesktop/sessions/{sessionKey}.json
 * - 压缩归档旧会话（>7天自动归档为 .json.gz）
 * - 启动时自动恢复活跃会话
 * - 会话列表查询
 */
public class SessionStore {

    private static final Logger log = LoggerFactory.getLogger(SessionStore.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Path sessionsDir;
    private final Path archiveDir;
    private final long archiveAfterDays;

    public SessionStore() {
        this(Path.of(System.getProperty("user.home"), ".clawdesktop", "sessions"));
    }

    public SessionStore(Path baseDir) {
        this.sessionsDir = baseDir;
        this.archiveDir = baseDir.resolve("archive");
        this.archiveAfterDays = 7;
        try {
            Files.createDirectories(sessionsDir);
            Files.createDirectories(archiveDir);
        } catch (IOException e) {
            log.warn("Failed to create session dirs: {}", e.getMessage());
        }
    }

    /**
     * 保存会话到磁盘。
     */
    public void save(Session session) {
        var key = sanitizeKey(session.key().toString());
        var file = sessionsDir.resolve(key + ".json");
        try {
            var json = serializeSession(session);
            Files.writeString(file, json);
            log.debug("Session saved: {} ({}KB)", key, file.toFile().length() / 1024);
        } catch (Exception e) {
            log.error("Failed to save session {}: {}", key, e.getMessage());
        }
    }

    /**
     * 从磁盘加载会话。
     */
    public Optional<Session> load(SessionKey key) {
        var keyStr = sanitizeKey(key.toString());
        var file = sessionsDir.resolve(keyStr + ".json");
        if (!Files.exists(file)) return Optional.empty();
        try {
            var json = Files.readString(file);
            return Optional.of(deserializeSession(json, key));
        } catch (Exception e) {
            log.error("Failed to load session {}: {}", keyStr, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * 列出所有持久化会话。
     */
    public List<SessionKey> listSessions() {
        var keys = new ArrayList<SessionKey>();
        try (var files = Files.list(sessionsDir)) {
            files
                .filter(f -> f.toString().endsWith(".json"))
                .forEach(f -> {
                    var name = f.getFileName().toString().replace(".json", "");
                    var key = desanitizeKey(name);
                    if (key != null) keys.add(key);
                });
        } catch (IOException e) {
            log.warn("Failed to list sessions: {}", e.getMessage());
        }
        return keys;
    }

    /**
     * 删除会话。
     */
    public boolean delete(SessionKey key) {
        var file = sessionsDir.resolve(sanitizeKey(key.toString()) + ".json");
        try {
            return Files.deleteIfExists(file);
        } catch (IOException e) {
            log.error("Failed to delete session: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 归档旧会话（压缩为 .gz）。
     */
    public int archiveOldSessions() {
        var archived = 0;
        var cutoff = Instant.now().minusSeconds(archiveAfterDays * 86400);
        try (var files = Files.list(sessionsDir)) {
            var oldFiles = files
                .filter(f -> f.toString().endsWith(".json"))
                .filter(f -> {
                    try {
                        return Files.getLastModifiedTime(f).toInstant().isBefore(cutoff);
                    } catch (IOException e) { return false; }
                })
                .toList();

            for (var file : oldFiles) {
                var archiveFile = archiveDir.resolve(file.getFileName() + ".gz");
                compressFile(file, archiveFile);
                Files.delete(file);
                archived++;
                log.info("Archived session: {}", file.getFileName());
            }
        } catch (IOException e) {
            log.warn("Failed to archive sessions: {}", e.getMessage());
        }
        return archived;
    }

    /**
     * 从归档恢复会话。
     */
    public Optional<Session> restoreFromArchive(SessionKey key) {
        var archiveFile = archiveDir.resolve(sanitizeKey(key.toString()) + ".json.gz");
        if (!Files.exists(archiveFile)) return Optional.empty();
        try {
            var json = decompressFile(archiveFile);
            return Optional.of(deserializeSession(json, key));
        } catch (Exception e) {
            log.error("Failed to restore from archive: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * 恢复所有活跃会话（启动时调用）。
     */
    public List<Session> restoreAll() {
        var sessions = new ArrayList<Session>();
        for (var key : listSessions()) {
            load(key).ifPresent(sessions::add);
        }
        log.info("Restored {} sessions from disk", sessions.size());
        return sessions;
    }

    // ---- serialization ----

    private String serializeSession(Session session) throws Exception {
        var root = MAPPER.createObjectNode();
        root.put("key", session.key().toString());
        root.put("createdAt", session.createdAt().toString());
        root.put("updatedAt", session.updatedAt().toString());

        var transcriptArray = MAPPER.createArrayNode();
        for (var entry : session.transcript().entries()) {
            var e = MAPPER.createObjectNode();
            e.put("id", entry.id());
            e.put("role", entry.role());
            e.put("content", entry.content());
            if (entry.toolCallId() != null) e.put("toolCallId", entry.toolCallId());
            e.put("timestamp", entry.timestamp().toString());
            transcriptArray.add(e);
        }
        root.set("transcript", transcriptArray);

        return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(root);
    }

    private Session deserializeSession(String json, SessionKey key) {
        try {
            var root = MAPPER.readTree(json);
            var session = new Session(key);
            var transcript = root.path("transcript");
            if (transcript.isArray()) {
                for (var entry : transcript) {
                    var id = entry.path("id").asText();
                    var role = entry.path("role").asText("user");
                    var content = entry.path("content").asText("");
                    var toolCallId = entry.has("toolCallId") ? entry.path("toolCallId").asText() : null;
                    var ts = entry.path("timestamp").asText("");
                    var timestamp = ts.isEmpty() ? Instant.now() : Instant.parse(ts);
                    session.transcript().add(new TranscriptEntry(id, role, content, toolCallId, timestamp));
                }
            }
            return session;
        } catch (Exception e) {
            log.error("Failed to deserialize session: {}", e.getMessage());
            return new Session(key);
        }
    }

    // ---- utils ----

    private void compressFile(Path source, Path target) throws IOException {
        try (var fis = Files.newInputStream(source);
             var gos = new GZIPOutputStream(Files.newOutputStream(target))) {
            fis.transferTo(gos);
        }
    }

    private String decompressFile(Path source) throws IOException {
        try (var gis = new GZIPInputStream(Files.newInputStream(source))) {
            return new String(gis.readAllBytes());
        }
    }

    private String sanitizeKey(String key) {
        return key.replaceAll("[^a-zA-Z0-9_-]", "_");
    }

    private SessionKey desanitizeKey(String sanitized) {
        // 简化：只支持 main: 格式
        if (sanitized.startsWith("main_")) {
            return SessionKey.main(sanitized.substring(5));
        }
        return null;
    }
}
