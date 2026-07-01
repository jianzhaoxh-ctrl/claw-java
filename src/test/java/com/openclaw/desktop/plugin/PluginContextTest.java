package com.openclaw.desktop.plugin;

import com.openclaw.desktop.config.ClawConfig;
import com.openclaw.desktop.cron.CronScheduler;
import com.openclaw.desktop.llm.LlmProviderRegistry;
import com.openclaw.desktop.session.SessionManager;
import com.openclaw.desktop.skill.SkillRegistry;
import com.openclaw.desktop.tool.ToolRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PluginContext 与 PluginEvent 单元测试。
 */
class PluginContextTest {

    @Test
    @DisplayName("forPlugin creates context with different pluginId")
    void testForPlugin() {
        var ctx = new PluginContext(
            ClawConfig.defaults(),
            new LlmProviderRegistry(),
            new ToolRegistry(),
            new SessionManager(),
            new CronScheduler(job -> {}),
            new SkillRegistry(Path.of("skills")),
            new EventBus(),
            Path.of("/data"),
            "core"
        );
        var childCtx = ctx.forPlugin("my-plugin");
        assertEquals("my-plugin", childCtx.pluginId());
        // 共享同一引用
        assertSame(ctx.config(), childCtx.config());
        assertSame(ctx.eventBus(), childCtx.eventBus());
    }

    @Test
    @DisplayName("pluginDataDir resolves dataDir/pluginId")
    void testPluginDataDir() {
        var ctx = new PluginContext(
            ClawConfig.defaults(),
            new LlmProviderRegistry(),
            new ToolRegistry(),
            new SessionManager(),
            new CronScheduler(job -> {}),
            new SkillRegistry(Path.of("skills")),
            new EventBus(),
            Path.of("/data"),
            "my-plugin"
        );
        var dataDir = ctx.pluginDataDir();
        assertEquals(Path.of("/data/my-plugin"), dataDir);
    }

    @Test
    @DisplayName("PluginEvent sealed subtypes are records")
    void testPluginEventTypes() {
        var loaded = new PluginEvent.PluginLoaded("p1", "Plugin One");
        assertEquals("p1", loaded.pluginId());
        assertEquals("Plugin One", loaded.pluginName());

        var unloaded = new PluginEvent.PluginUnloaded("p1");
        assertEquals("p1", unloaded.pluginId());

        var toolReg = new PluginEvent.ToolRegistered("echo", "p1");
        assertEquals("echo", toolReg.toolName());

        var custom = new PluginEvent.CustomEvent("my.type", "payload", "src");
        assertEquals("my.type", custom.type());
        assertEquals("payload", custom.payload());
        assertEquals("src", custom.sourcePluginId());
    }
}
