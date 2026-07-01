package com.openclaw.desktop.tool;

import com.openclaw.desktop.agent.AgentContext;
import com.openclaw.desktop.session.Session;

import java.nio.file.Path;
import java.util.Map;

/**
 * 工具执行上下文 — v2.0 重构。
 *
 * <p>使用 {@link AgentContext} 替代原来的 {@code Object} 占位符。
 * 工具实现可以通过 {@code agentContext()} 访问 Agent 运行时状态。
 */
public record ToolContext(
    Session session,
    AgentContext agentContext,
    Path workspaceDir,
    Map<String, String> env
) {}
