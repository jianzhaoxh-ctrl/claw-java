package com.openclaw.desktop.session;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 会话管理器 — 管理所有活跃会话的生命周期，支持磁盘持久化。
 *
 * <p>每次创建/更新会话后自动保存到 ~/.clawdesktop/sessions/ 目录。
 * 启动时自动恢复历史会话。
 */
public class SessionManager {

    private static final Logger log = LoggerFactory.getLogger(SessionManager.class);

    private final ConcurrentMap<String, Session> sessions = new ConcurrentHashMap<>();
    private final SessionStore store;
    private final boolean persistenceEnabled;

    public SessionManager() {
        this(null, false);
    }

    public SessionManager(SessionStore store, boolean persistenceEnabled) {
        this.store = store;
        this.persistenceEnabled = persistenceEnabled;
        if (persistenceEnabled) {
            restoreAll();
        }
    }

    /**
     * 获取或创建会话 — 先查缓存，再查磁盘，最后创建新的。
     */
    public Mono<Session> getOrCreate(SessionKey key) {
        var existing = sessions.get(key.toString());
        if (existing != null) return Mono.just(existing);

        // 尝试从磁盘加载
        if (persistenceEnabled) {
            var loaded = store.load(key);
            if (loaded.isPresent()) {
                sessions.put(key.toString(), loaded.get());
                log.info("Restored session from disk: {}", key);
                return Mono.just(loaded.get());
            }
        }

        log.info("Creating new session: {}", key);
        var session = new Session(key);
        sessions.put(key.toString(), session);
        return Mono.just(session);
    }

    public Mono<Session> get(SessionKey key) {
        return Mono.fromCallable(() -> sessions.get(key.toString()));
    }

    /**
     * 删除会话 — 从内存和磁盘同时删除。
     */
    public Mono<Void> delete(SessionKey key) {
        return Mono.fromRunnable(() -> {
            var removed = sessions.remove(key.toString());
            if (removed != null) {
                log.info("Deleted session: {}", key);
            }
            if (persistenceEnabled) {
                store.delete(key);
            }
        }).then();
    }

    public Flux<Session> list(String agentId) {
        return Flux.fromIterable(sessions.values())
            .filter(s -> s.key().agentId().equals(agentId));
    }

    /**
     * 列出所有会话（内存 + 磁盘）。
     */
    public Flux<Session> all() {
        return Flux.fromIterable(sessions.values());
    }

    /**
     * 列出所有会话的信息（含磁盘上的）。
     */
    public List<SessionInfo> listAllSessions() {
        var result = new ArrayList<SessionInfo>();
        for (var session : sessions.values()) {
            var entries = session.transcript().entries();
            var title = entries.isEmpty() ? "新对话" :
                entries.stream()
                    .filter(e -> "user".equals(e.role()))
                    .map(TranscriptEntry::content)
                    .findFirst()
                    .map(s -> s.length() > 30 ? s.substring(0, 30) + "..." : s)
                    .orElse("新对话");
            result.add(new SessionInfo(session.key(), title,
                session.createdAt(), session.updatedAt(), entries.size()));
        }
        return result;
    }

    public int count() {
        return sessions.size();
    }

    /**
     * 保存会话到磁盘。
     */
    public void save(Session session) {
        if (persistenceEnabled && session != null) {
            store.save(session);
        }
    }

    /**
     * 从磁盘恢复所有会话。
     */
    private void restoreAll() {
        var restored = store.restoreAll();
        for (var session : restored) {
            sessions.put(session.key().toString(), session);
        }
        if (!restored.isEmpty()) {
            log.info("Restored {} sessions from disk", restored.size());
        }
    }

    /**
     * 会话信息摘要。
     */
    public record SessionInfo(
        SessionKey key,
        String title,
        java.time.Instant createdAt,
        java.time.Instant updatedAt,
        int messageCount
    ) {}
}
