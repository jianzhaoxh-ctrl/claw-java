package com.openclaw.desktop.hook;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 钩子管理器 — 统管 HookRegistry 和 HookExecutor。
 * 对应 OpenClaw 的 HookManager。
 *
 * <p>提供统一的 API：
 * <ul>
 *   <li>注册/取消注册钩子</li>
 *   <li>查找并执行钩子</li>
 *   <li>启禁用钩子</li>
 * </ul>
 */
public class HookManager {

    private static final Logger log = LoggerFactory.getLogger(HookManager.class);

    private final HookRegistry registry;
    private final HookExecutor executor;

    public HookManager() {
        this.registry = new HookRegistry();
        this.executor = new HookExecutor();
    }

    public HookManager(HookRegistry registry) {
        this.registry = registry;
        this.executor = new HookExecutor();
        // 将 registry 中所有钩子注册到 executor
        for (var hook : registry.all()) {
            executor.register(hook);
        }
    }

    // ---- 注册 ----

    /**
     * 注册一个钩子（同时注册到 Registry 和 Executor）。
     */
    public void register(HookDefinition hook) {
        registry.register(hook);
        executor.register(hook);
    }

    /**
     * 取消注册一个钩子。
     */
    public void unregister(String hookId) {
        registry.unregister(hookId);
        executor.unregister(hookId);
    }

    // ---- 执行 ----

    /**
     * 执行匹配指定触发器和阶段的钩子链。
     */
    public Mono<HookResult> execute(HookDefinition.HookTrigger trigger,
                                     HookDefinition.HookPhase phase,
                                     HookContext context) {
        return executor.execute(trigger, phase, context);
    }

    /**
     * 执行 BeforeChat 钩子。
     */
    public Mono<HookResult> beforeChat(String agentId, String modelId, String message) {
        return execute(HookDefinition.HookTrigger.CHAT, HookDefinition.HookPhase.BEFORE,
            new HookContext.ChatHookContext(agentId, modelId, message, java.util.Map.of()));
    }

    /**
     * 执行 AfterChat 钩子。
     */
    public Mono<HookResult> afterChat(String agentId, String modelId, String response) {
        return execute(HookDefinition.HookTrigger.CHAT, HookDefinition.HookPhase.AFTER,
            new HookContext.ChatHookContext(agentId, modelId, response, java.util.Map.of()));
    }

    /**
     * 执行 BeforeToolCall 钩子。
     */
    public Mono<HookResult> beforeToolCall(String agentId, String toolName, String toolCallId, String arguments) {
        return execute(HookDefinition.HookTrigger.TOOL_CALL, HookDefinition.HookPhase.BEFORE,
            new HookContext.ToolCallHookContext(agentId, toolName, toolCallId, arguments, java.util.Map.of()));
    }

    /**
     * 执行 AfterToolCall 钩子。
     */
    public Mono<HookResult> afterToolCall(String agentId, String toolName, String toolCallId, String result) {
        return execute(HookDefinition.HookTrigger.TOOL_CALL, HookDefinition.HookPhase.AFTER,
            new HookContext.ToolCallHookContext(agentId, toolName, toolCallId, result, java.util.Map.of()));
    }

    /**
     * 执行 OnSessionStart 钩子。
     */
    public Mono<HookResult> onSessionStart(String sessionKey, String agentId) {
        return execute(HookDefinition.HookTrigger.SESSION_START, HookDefinition.HookPhase.BEFORE,
            new HookContext.SessionHookContext(sessionKey, agentId, java.util.Map.of()));
    }

    /**
     * 执行 OnSessionEnd 钩子。
     */
    public Mono<HookResult> onSessionEnd(String sessionKey, String agentId) {
        return execute(HookDefinition.HookTrigger.SESSION_END, HookDefinition.HookPhase.AFTER,
            new HookContext.SessionHookContext(sessionKey, agentId, java.util.Map.of()));
    }

    // ---- 管理 ----

    /**
     * 启用一个钩子。
     */
    public void enable(String hookId) {
        registry.enable(hookId);
        // Executor 使用同一 HookDefinition 对象引用，需要替换
        var hook = registry.get(hookId);
        if (hook != null) {
            executor.unregister(hookId);
            executor.register(hook);
        }
    }

    /**
     * 禁用一个钩子。
     */
    public void disable(String hookId) {
        registry.disable(hookId);
        executor.unregister(hookId);
    }

    /**
     * 获取所有钩子。
     */
    public List<HookDefinition> listAll() {
        return registry.all();
    }

    /**
     * 获取注册表。
     */
    public HookRegistry registry() { return registry; }

    /**
     * 获取执行器。
     */
    public HookExecutor executor() { return executor; }

    /**
     * 获取已注册钩子数量。
     */
    public int count() { return registry.count(); }
}
