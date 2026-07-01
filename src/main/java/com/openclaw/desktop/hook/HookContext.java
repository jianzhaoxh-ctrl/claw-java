package com.openclaw.desktop.hook;

/**
 * 钩子上下文 — 钩子执行时传入的上下文信息。
 * sealed 接口，不同触发器有不同的上下文。
 */
public sealed interface HookContext {

    /** 聊天钩子上下文 */
    record ChatHookContext(
        String agentId,
        String modelId,
        String message,
        java.util.Map<String, Object> metadata
    ) implements HookContext {}

    /** 工具调用钩子上下文 */
    record ToolCallHookContext(
        String agentId,
        String toolName,
        String toolCallId,
        String arguments,
        java.util.Map<String, Object> metadata
    ) implements HookContext {}

    /** 会话钩子上下文 */
    record SessionHookContext(
        String sessionKey,
        String agentId,
        java.util.Map<String, Object> metadata
    ) implements HookContext {}

    /** 配置重载钩子上下文 */
    record ConfigReloadHookContext(
        String configPath,
        boolean successful,
        String errorMessage
    ) implements HookContext {}
}
