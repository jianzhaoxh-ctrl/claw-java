package com.openclaw.desktop.plugin;

import com.openclaw.desktop.config.ClawConfig;
import com.openclaw.desktop.cron.CronScheduler;
import com.openclaw.desktop.llm.LlmProviderRegistry;
import com.openclaw.desktop.session.SessionManager;
import com.openclaw.desktop.skill.SkillRegistry;
import com.openclaw.desktop.tool.ToolRegistry;

import java.nio.file.Path;

/**
 * 插件上下文 — 提供插件运行时所需的所有核心服务。
 *
 * <p>插件通过 {@link ClawPlugin#init(PluginContext)} 拿到此上下文，
 * 可访问配置、注册工具/Provider、订阅事件、读取数据目录等。
 *
 * @param config           主配置
 * @param providerRegistry LLM Provider 注册表（插件可注册自定义 Provider）
 * @param toolRegistry     工具注册表（插件可注册自定义工具）
 * @param sessionManager   会话管理器
 * @param cronScheduler    Cron 调度器（插件可注册定时任务）
 * @param skillRegistry    技能注册表（插件可注册技能）
 * @param eventBus         事件总线（插件间通信）
 * @param dataDir          插件可写数据目录
 * @param pluginId         当前插件 ID（便于插件标识自身）
 */
public record PluginContext(
    ClawConfig config,
    LlmProviderRegistry providerRegistry,
    ToolRegistry toolRegistry,
    SessionManager sessionManager,
    CronScheduler cronScheduler,
    SkillRegistry skillRegistry,
    EventBus eventBus,
    Path dataDir,
    String pluginId
) {
    /**
     * 创建一个针对特定插件的上下文视图（复用所有服务，仅 pluginId 不同）。
     */
    public PluginContext forPlugin(String pluginId) {
        return new PluginContext(
            config, providerRegistry, toolRegistry, sessionManager,
            cronScheduler, skillRegistry, eventBus, dataDir, pluginId
        );
    }

    /**
     * 返回当前插件专属的数据目录（dataDir/pluginId）。
     */
    public Path pluginDataDir() {
        return dataDir.resolve(pluginId);
    }
}
