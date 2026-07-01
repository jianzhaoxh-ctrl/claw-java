package com.openclaw.desktop;

import com.openclaw.desktop.session.Session;
import com.openclaw.desktop.session.SessionKey;
import com.openclaw.desktop.session.SessionManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 会话系统测试。
 */
class SessionSystemTest {

    @Test
    @DisplayName("SessionManager should create and retrieve sessions")
    void testSessionCreation() {
        var manager = new SessionManager();
        var key = SessionKey.main("test-agent");

        var session = manager.getOrCreate(key).block();
        assertNotNull(session);
        assertEquals(key, session.key());

        // Same key should return same session
        var session2 = manager.getOrCreate(key).block();
        assertEquals(session, session2);
    }

    @Test
    @DisplayName("Session should track messages")
    void testSessionMessages() {
        var key = SessionKey.main("test-agent");
        var session = new Session(key);

        session.addUserMessage("Hello");
        session.addAssistantMessage("Hi there!");

        var transcript = session.transcript();
        assertEquals(2, transcript.entries().size());
        assertEquals("user", transcript.entries().get(0).role());
        assertEquals("Hello", transcript.entries().get(0).content());
        assertEquals("assistant", transcript.entries().get(1).role());
    }

    @Test
    @DisplayName("Session should track events")
    void testSessionEvents() {
        var key = SessionKey.main("test-agent");
        var session = new Session(key);

        session.addUserMessage("Test");

        var events = session.events();
        assertFalse(events.isEmpty());
    }
}
