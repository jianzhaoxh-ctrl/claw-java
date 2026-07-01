package com.openclaw.desktop.command;

import com.openclaw.desktop.agent.Agent;
import com.openclaw.desktop.config.ClawConfig;
import com.openclaw.desktop.config.ConfigLoader;
import com.openclaw.desktop.session.Session;
import com.openclaw.desktop.session.SessionKey;
import com.openclaw.desktop.session.SessionManager;
import com.openclaw.desktop.llm.LlmProvider;
import com.openclaw.desktop.llm.LlmProviderRegistry;
import com.openclaw.desktop.llm.Model;
import com.openclaw.desktop.tool.Tool;
import com.openclaw.desktop.tool.ToolRegistry;
import com.openclaw.desktop.memory.MemoryDatabase;
import com.openclaw.desktop.plugin.PluginManager;
import com.openclaw.desktop.skill.SkillManager;
import com.openclaw.desktop.mcp.McpClientManager;
import com.openclaw.desktop.channel.ChannelRegistry;
import com.openclaw.desktop.hook.HookExecutor;

/**
 * 命令上下文 — 命令执行时的环境依赖。
 */
public record CommandContext(
    ClawConfig config,
    Session session,
    Agent agent,
    SessionManager sessionManager,
    LlmProviderRegistry providerRegistry,
    ToolRegistry toolRegistry,
    MemoryDatabase memoryDatabase,
    PluginManager pluginManager,
    SkillManager skillManager,
    McpClientManager mcpClientManager,
    ChannelRegistry channelRegistry,
    ConfigLoader configLoader,
    HookExecutor hookExecutor
) {
    /**
     * 快捷工厂 — 创建测试用的最小上下文。
     */
    public static CommandContext minimal() {
        return new CommandContext(
            ClawConfig.defaults(),
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null
        );
    }
}
