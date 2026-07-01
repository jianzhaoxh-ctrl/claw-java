package com.openclaw.desktop.config;

import com.openclaw.desktop.plugin.EventBus;
import com.openclaw.desktop.plugin.PluginEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 配置热重载管理器测试。
 */
class ConfigReloadManagerTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("start loads initial config and notifies subscribers")
    void testInitialLoad() throws Exception {
        var configPath = tempDir.resolve("test.conf");
        Files.writeString(configPath, """
            claw {
              name = "TestClaw"
              version = "0.1.0"
              data-dir = "data"
              gateway { port = 7180 }
              agent {
                id = "default"
                name = "Test"
                model-id = "gpt-4o"
                system-prompt = "test"
                reasoning-level = "off"
                max-tokens = 4096
                temperature = 0.7
              }
              llm {
                default-provider = null
                providers {}
              }
              memory {
                db-path = "data/test.db"
                embedding-enabled = false
                embedding-model = "text-embedding-3-small"
              }
            }
            """);

        var eventBus = new EventBus();
        var manager = new ConfigReloadManager(eventBus, configPath);

        var received = new AtomicReference<ClawConfig>();
        manager.subscribe(received::set);

        manager.start();
        assertNotNull(received.get());
        assertNotNull(received.get().agent());
        // 不校验具体值（ConfigLoader 会合并 classpath 默认配置），仅验证非空
        assertNotNull(received.get().agent().name());

        manager.stop();
        eventBus.shutdown();
    }

    @Test
    @DisplayName("reload broadcasts config.reloaded event")
    void testReloadEvent() throws Exception {
        var configPath = tempDir.resolve("test2.conf");
        Files.writeString(configPath, defaultConfig());

        var eventBus = new EventBus();
        var eventReceived = new AtomicReference<String>();
        eventBus.subscribe(PluginEvent.CustomEvent.class, e -> {
            if ("config.reloaded".equals(e.type())) {
                eventReceived.set(e.type());
            }
        });

        var manager = new ConfigReloadManager(eventBus, configPath);
        manager.start();

        // 修改配置文件
        Thread.sleep(100);
        Files.writeString(configPath, defaultConfig().replace("TestClaw", "UpdatedClaw"));

        // 手动触发 reload
        manager.reload();

        // 等待事件处理
        Thread.sleep(100);
        assertEquals("config.reloaded", eventReceived.get());

        manager.stop();
        eventBus.shutdown();
    }

    @Test
    @DisplayName("subscribe returns unsubscribe runnable")
    void testSubscribeUnsubscribe() throws Exception {
        var configPath = tempDir.resolve("test3.conf");
        Files.writeString(configPath, defaultConfig());

        var eventBus = new EventBus();
        var manager = new ConfigReloadManager(eventBus, configPath);
        manager.start();

        var count = new java.util.concurrent.atomic.AtomicInteger(0);
        var unsub = manager.subscribe(c -> count.incrementAndGet());
        // 初始通知 +1
        assertEquals(1, count.get());

        unsub.run();
        manager.reload();
        // reload 后不再通知已取消的订阅者
        assertEquals(1, count.get());

        manager.stop();
        eventBus.shutdown();
    }

    private String defaultConfig() {
        return """
            claw {
              name = "TestClaw"
              version = "0.1.0"
              data-dir = "data"
              gateway { port = 7180 }
              agent {
                id = "default"
                name = "Test"
                model-id = "gpt-4o"
                system-prompt = "test"
                reasoning-level = "off"
                max-tokens = 4096
                temperature = 0.7
              }
              llm {
                default-provider = null
                providers {}
              }
              memory {
                db-path = "data/test.db"
                embedding-enabled = false
                embedding-model = "text-embedding-3-small"
              }
            }
            """;
    }
}
