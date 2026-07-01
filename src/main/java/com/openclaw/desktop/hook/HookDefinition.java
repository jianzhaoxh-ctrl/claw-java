package com.openclaw.desktop.hook;

import java.time.Instant;
import java.util.Map;

/**
 * 钩子定义 — 对应 OpenClaw 的 Hook / Hookable 机制。
 *
 * <p>钩子是在特定生命周期事件触发时执行的用户自定义逻辑：
 * <ul>
 *   <li>BeforeChat — LLM 请求发送前</li>
 *   <li>AfterChat — LLM 响应接收后</li>
 *   <li>BeforeToolCall — 工具调用前</li>
 *   <li>AfterToolCall — 工具调用后</li>
 *   <li>OnSessionStart — 会话开始时</li>
 *   <li>OnSessionEnd — 会话结束时</li>
 *   <li>OnConfigReload — 配置热重载时</li>
 * </ul>
 */
public record HookDefinition(
    String id,
    String name,
    HookPhase phase,
    HookTrigger trigger,
    HookAction action,
    int priority,
    boolean enabled,
    Instant createdAt
) {
    /**
     * 钩子触发阶段。
     */
    public enum HookPhase {
        BEFORE, AFTER, ON_ERROR
    }

    /**
     * 钩子触发事件。
     */
    public enum HookTrigger {
        CHAT, TOOL_CALL, SESSION_START, SESSION_END,
        CONFIG_RELOAD, AGENT_START, AGENT_END,
        CONTEXT_COMPACT, MESSAGE_SENT
    }

    /**
     * 钩子动作类型。
     */
    public enum HookAction {
        /** 修改请求/响应内容 */
        MODIFY,
        /** 阻止操作 */
        BLOCK,
        /** 注入额外消息 */
        INJECT,
        /** 仅记录日志 */
        LOG,
        /** 执行自定义代码 */
        EXECUTE
    }

    public static HookDefinition simple(String name, HookTrigger trigger, HookPhase phase) {
        return new HookDefinition(
            name.toLowerCase().replace(" ", "-"), name, phase, trigger,
            HookAction.LOG, 0, true, Instant.now()
        );
    }
}
