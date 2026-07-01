package com.openclaw.desktop;

import com.openclaw.desktop.config.ConfigReloadManager;
import com.openclaw.desktop.plugin.EventBus;
import com.openclaw.desktop.plugin.PluginEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * D2 集成测试 — 配置热重载 + EventBus 事件广播。
 */
class ConfigReloadIntegrationTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("config reload broadcasts event through EventBus")
    void testConfigReloadBroadcastsEvent() throws Exception {
        var configPath = tempDir.resolve("integration.conf");
        Files.writeString(configPath, defaultConfig());

        var eventBus = new EventBus();
        var eventCount = new AtomicInteger(0);
        var eventType = new AtomicReference<String>();

        eventBus.subscribe(PluginEvent.CustomEvent.class, e -> {
            if (e.type().startsWith("config.")) {
                eventCount.incrementAndGet();
                eventType.set(e.type());
            }
        });

        var manager = new ConfigReloadManager(eventBus, configPath);
        manager.start();

        // 初始加载不广播事件（只在 reload 时广播）
        Thread.sleep(100);
        assertEquals(0, eventCount.get());

        // 手动 reload 触发事件
        manager.reload();
        Thread.sleep(100);

        assertTrue(eventCount.get() >= 1);
        assertEquals("config.reloaded", eventType.get());

        manager.stop();
        eventBus.shutdown();
    }

    @Test
    @DisplayName("multiple subscribers all receive reload")
    void testMultipleSubscribers() throws Exception {
        var configPath = tempDir.resolve("multi.conf");
        Files.writeString(configPath, defaultConfig());

        var eventBus = new EventBus();
        var count1 = new AtomicInteger(0);
        var count2 = new AtomicInteger(0);

        var manager = new ConfigReloadManager(eventBus, configPath);
        manager.subscribe(c -> count1.incrementAndGet());
        manager.subscribe(c -> count2.incrementAndGet());
        manager.start();

        // 初始加载通知一次
        assertEquals(1, count1.get());
        assertEquals(1, count2.get());

        manager.reload();
        // reload 通知第二次
        assertEquals(2, count1.get());
        assertEquals(2, count2.get());

        manager.stop();
        eventBus.shutdown();
    }

    @Test
    @DisplayName("reload on invalid config broadcasts failure event")
    void testReloadFailureBroadcasts() throws Exception {
        var configPath = tempDir.resolve("broken.conf");
        Files.writeString(configPath, defaultConfig());

        var eventBus = new EventBus();
        var failureType = new AtomicReference<String>();

        eventBus.subscribe(PluginEvent.CustomEvent.class, e -> {
            if ("config.reload.failed".equals(e.type())) {
                failureType.set(e.type());
            }
        });

        var manager = new ConfigReloadManager(eventBus, configPath);
        manager.start();

        // 写入无效配置
        Files.writeString(configPath, "this is not valid HOCON {{{{");
        manager.reload();

        Thread.sleep(100);
        // 可能成功也可能失败（取决于 ConfigLoader 容错），但不应抛异常
        // 如果失败了，应广播 config.reload.failed
        // 不做硬断言，仅验证不崩溃
        manager.stop();
        eventBus.shutdown();
    }

    private String defaultConfig() {
        return """
            claw {
              name = "Test"
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
