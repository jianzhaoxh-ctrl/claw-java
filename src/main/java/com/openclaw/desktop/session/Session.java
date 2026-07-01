package com.openclaw.desktop.session;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 会话 — 封装一次对话的上下文和状态。
 */
public class Session {

    private static final Logger log = LoggerFactory.getLogger(Session.class);

    private final SessionKey key;
    private final Transcript transcript;
    private final List<SessionEvent> eventLog;
    private final Instant createdAt;
    private volatile Instant updatedAt;

    public Session(SessionKey key) {
        this.key = key;
        this.transcript = new Transcript();
        this.eventLog = new CopyOnWriteArrayList<>();
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
        eventLog.add(new SessionEvent.Created(key, createdAt));
    }

    public SessionKey key() { return key; }
    public Transcript transcript() { return transcript; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }

    public void addUserMessage(String content) {
        var entry = new TranscriptEntry(
            java.util.UUID.randomUUID().toString(),
            "user", content, null, Instant.now()
        );
        transcript.add(entry);
        updatedAt = Instant.now();
        eventLog.add(new SessionEvent.MessageAdded(key, "user", content, updatedAt));
    }

    public void addAssistantMessage(String content) {
        var entry = new TranscriptEntry(
            java.util.UUID.randomUUID().toString(),
            "assistant", content, null, Instant.now()
        );
        transcript.add(entry);
        updatedAt = Instant.now();
        eventLog.add(new SessionEvent.MessageAdded(key, "assistant", content, updatedAt));
    }

    public void addToolResult(String toolCallId, String content) {
        var entry = new TranscriptEntry(
            java.util.UUID.randomUUID().toString(),
            "tool", content, toolCallId, Instant.now()
        );
        transcript.add(entry);
        updatedAt = Instant.now();
    }

    public Mono<Void> reset() {
        log.info("Resetting session: {}", key);
        transcript.clear();
        eventLog.add(new SessionEvent.Reset(key, Instant.now()));
        return Mono.empty();
    }

    public List<SessionEvent> events() {
        return List.copyOf(eventLog);
    }
}
