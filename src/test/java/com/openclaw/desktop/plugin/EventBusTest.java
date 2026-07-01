package com.openclaw.desktop.plugin;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 事件总线测试。
 */
class EventBusTest {

    @Test
    @DisplayName("subscribe and publish synchronously delivers event")
    void testSubscribeAndPublish() {
        var bus = new EventBus();
        var ref = new AtomicReference<String>();
        bus.subscribe(PluginEvent.PluginLoaded.class, e -> ref.set(e.pluginId()));

        bus.publish(new PluginEvent.PluginLoaded("p1", "Plugin One"));

        assertEquals("p1", ref.get());
        assertEquals(1, bus.publishedCount());
        bus.shutdown();
    }

    @Test
    @DisplayName("wildcard subscription receives all event types")
    void testWildcardSubscription() {
        var bus = new EventBus();
        var count = new AtomicInteger(0);
        bus.subscribeAll(e -> count.incrementAndGet());

        bus.publish(new PluginEvent.PluginLoaded("a", "A"));
        bus.publish(new PluginEvent.ToolRegistered("t", "a"));

        assertEquals(2, count.get());
        bus.shutdown();
    }

    @Test
    @DisplayName("unsubscribe stops further delivery")
    void testUnsubscribe() {
        var bus = new EventBus();
        var count = new AtomicInteger(0);
        var sub = bus.subscribe(PluginEvent.PluginLoaded.class, e -> count.incrementAndGet());

        bus.publish(new PluginEvent.PluginLoaded("a", "A"));
        assertEquals(1, count.get());

        sub.cancel();
        bus.publish(new PluginEvent.PluginLoaded("b", "B"));
        assertEquals(1, count.get());
        bus.shutdown();
    }

    @Test
    @DisplayName("subscriber exception is isolated and does not block others")
    void testExceptionIsolation() {
        var bus = new EventBus();
        var received = new AtomicInteger(0);
        bus.subscribe(PluginEvent.PluginLoaded.class, e -> { throw new RuntimeException("boom"); });
        bus.subscribe(PluginEvent.PluginLoaded.class, e -> received.incrementAndGet());

        bus.publish(new PluginEvent.PluginLoaded("a", "A"));

        assertEquals(1, received.get());
        bus.shutdown();
    }

    @Test
    @DisplayName("publishAsync delivers on virtual thread")
    void testPublishAsync() throws Exception {
        var bus = new EventBus();
        var ref = new AtomicReference<String>();
        var latch = new CountDownLatch(1);
        bus.subscribe(PluginEvent.PluginLoaded.class, e -> {
            ref.set(e.pluginId());
            latch.countDown();
        });

        bus.publishAsync(new PluginEvent.PluginLoaded("async", "Async"));

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertEquals("async", ref.get());
        bus.shutdown();
    }

    @Test
    @DisplayName("custom event is delivered to typed subscribers")
    void testCustomEvent() {
        var bus = new EventBus();
        var ref = new AtomicReference<String>();
        bus.subscribe(PluginEvent.CustomEvent.class, e -> ref.set(e.type()));

        bus.publish(new PluginEvent.CustomEvent("my.type", "payload", "src"));

        assertEquals("my.type", ref.get());
        bus.shutdown();
    }
}
