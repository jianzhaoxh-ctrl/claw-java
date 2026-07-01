package com.openclaw.desktop.session;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

/**
 * SessionManager 测试。
 */
class SessionManagerTest {

    @Test
    void testCreateAndGet() {
        var manager = new SessionManager();
        var key = SessionKey.main("default");
        var session = manager.getOrCreate(key).block();

        assertNotNull(session);
        assertEquals(key, session.key());

        // get again should return same session
        var same = manager.get(key).block();
        assertNotNull(same);
        assertEquals(session, same);
    }

    @Test
    void testAddMessages() {
        var manager = new SessionManager();
        var key = SessionKey.main("default");
        var session = manager.getOrCreate(key).block();

        session.addUserMessage("Hello");
        session.addAssistantMessage("Hi there!");

        assertEquals(2, session.transcript().size());
    }

    @Test
    void testDelete() {
        var manager = new SessionManager();
        var key = SessionKey.main("default");
        manager.getOrCreate(key).block();

        assertEquals(1, manager.count());

        manager.delete(key).block();
        assertEquals(0, manager.count());
    }
}
