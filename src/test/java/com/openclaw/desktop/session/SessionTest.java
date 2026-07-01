package com.openclaw.desktop.session;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Session 与 Transcript 单元测试 — 补充会话生命周期与消息记录验证。
 */
class SessionTest {

    @Test
    @DisplayName("new session has empty transcript and created event")
    void testNewSession() {
        var session = new Session(SessionKey.main("agent1"));
        assertEquals(0, session.transcript().size());
        assertNotNull(session.createdAt());
        assertEquals(session.createdAt(), session.updatedAt());
    }

    @Test
    @DisplayName("addUserMessage adds entry and updates timestamp")
    void testAddUserMessage() {
        var session = new Session(SessionKey.main("test"));
        session.addUserMessage("hello");

        assertEquals(1, session.transcript().size());
        var entry = session.transcript().last();
        assertEquals("user", entry.role());
        assertEquals("hello", entry.content());
        assertTrue(session.updatedAt().isAfter(session.createdAt())
            || session.updatedAt().equals(session.createdAt()));
    }

    @Test
    @DisplayName("addAssistantMessage adds entry")
    void testAddAssistantMessage() {
        var session = new Session(SessionKey.main("test"));
        session.addAssistantMessage("hi there");

        assertEquals(1, session.transcript().size());
        assertEquals("assistant", session.transcript().last().role());
    }

    @Test
    @DisplayName("reset clears transcript")
    void testReset() {
        var session = new Session(SessionKey.main("test"));
        session.addUserMessage("a");
        session.addAssistantMessage("b");
        assertEquals(2, session.transcript().size());

        session.reset().block();
        assertEquals(0, session.transcript().size());
    }

    @Test
    @DisplayName("multiple messages maintain order")
    void testMessageOrder() {
        var session = new Session(SessionKey.main("test"));
        session.addUserMessage("first");
        session.addAssistantMessage("second");
        session.addUserMessage("third");

        var entries = session.transcript().entries();
        assertEquals(3, entries.size());
        assertEquals("first", entries.get(0).content());
        assertEquals("second", entries.get(1).content());
        assertEquals("third", entries.get(2).content());
    }

    @Test
    @DisplayName("SessionKey.main produces correct toString")
    void testSessionKeyMain() {
        var key = SessionKey.main("agent1");
        assertEquals("main:agent1", key.toString());
        assertEquals(SessionKey.SessionKind.MAIN, key.kind());
    }

    @Test
    @DisplayName("SessionKey.child produces correct toString")
    void testSessionKeyChild() {
        var key = SessionKey.child("agent1", "slack", "user1", "thread1");
        assertEquals("child:agent1:slack:user1:thread1", key.toString());
        assertEquals(SessionKey.SessionKind.CHILD, key.kind());
    }

    @Test
    @DisplayName("Transcript last returns null when empty")
    void testTranscriptLastEmpty() {
        var t = new Transcript();
        assertNull(t.last());
        assertEquals(0, t.size());
    }

    @Test
    @DisplayName("Transcript entries returns immutable copy")
    void testTranscriptEntriesImmutable() {
        var t = new Transcript();
        t.add(new TranscriptEntry("1", "user", "hi", null, Instant.now()));
        var entries = t.entries();
        assertEquals(1, entries.size());
        // 修改返回的列表不应影响原 transcript
        assertThrows(UnsupportedOperationException.class, () -> entries.add(
            new TranscriptEntry("2", "user", "x", null, Instant.now())));
    }
}
