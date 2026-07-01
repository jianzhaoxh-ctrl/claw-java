package com.openclaw.desktop.plugin;

/**
 * 插件事件 — 通过 {@link EventBus} 在插件之间传递的事件。
 * 使用 sealed 接口限定常用事件类型，插件也可自定义事件（实现 {@link CustomEvent}）。
 */
public sealed interface PluginEvent permits
    PluginEvent.PluginLoaded,
    PluginEvent.PluginUnloaded,
    PluginEvent.ToolRegistered,
    PluginEvent.ToolUnregistered,
    PluginEvent.ProviderRegistered,
    PluginEvent.SessionCreated,
    PluginEvent.SessionReset,
    PluginEvent.AgentTurnStarted,
    PluginEvent.AgentTurnCompleted,
    PluginEvent.CustomEvent {

    /** 插件加载完成。 */
    record PluginLoaded(String pluginId, String pluginName) implements PluginEvent {}

    /** 插件卸载完成。 */
    record PluginUnloaded(String pluginId) implements PluginEvent {}

    /** 工具注册。 */
    record ToolRegistered(String toolName, String ownerPluginId) implements PluginEvent {}

    /** 工具注销。 */
    record ToolUnregistered(String toolName) implements PluginEvent {}

    /** LLM Provider 注册。 */
    record ProviderRegistered(String providerId, String ownerPluginId) implements PluginEvent {}

    /** 会话创建。 */
    record SessionCreated(String sessionKey) implements PluginEvent {}

    /** 会话重置。 */
    record SessionReset(String sessionKey) implements PluginEvent {}

    /** Agent 一轮对话开始。 */
    record AgentTurnStarted(String sessionKey, String modelId) implements PluginEvent {}

    /** Agent 一轮对话完成。 */
    record AgentTurnCompleted(String sessionKey, boolean success, long durationMs) implements PluginEvent {}

    /**
     * 自定义事件 — 插件可发布任意自定义事件。
     * 建议用 type 字符串区分事件种类，payload 携带数据。
     */
    record CustomEvent(String type, Object payload, String sourcePluginId) implements PluginEvent {}
}
