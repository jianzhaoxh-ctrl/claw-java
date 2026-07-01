package com.openclaw.desktop.hook;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 钩子注册表 — 管理 HookDefinition 的注册、查找、启禁用。
 * 对应 OpenClaw 的 HookRegistry。
 */
public class HookRegistry {

    private static final Logger log = LoggerFactory.getLogger(HookRegistry.class);

    private final Map<String, HookDefinition> hooks = new ConcurrentHashMap<>();

    /**
     * 注册一个钩子。
     */
    public void register(HookDefinition hook) {
        hooks.put(hook.id(), hook);
        log.debug("Hook registered: {} (phase={}, trigger={}, action={}, priority={})",
            hook.id(), hook.phase(), hook.trigger(), hook.action(), hook.priority());
    }

    /**
     * 取消注册一个钩子。
     */
    public void unregister(String hookId) {
        var removed = hooks.remove(hookId);
        if (removed != null) {
            log.debug("Hook unregistered: {}", hookId);
        } else {
            log.warn("Hook not found for unregistration: {}", hookId);
        }
    }

    /**
     * 获取一个钩子。
     */
    public HookDefinition get(String hookId) {
        return hooks.get(hookId);
    }

    /**
     * 获取所有注册的钩子。
     */
    public List<HookDefinition> all() {
        return List.copyOf(hooks.values());
    }

    /**
     * 查找匹配指定触发器和阶段的钩子，按优先级排序。
     */
    public List<HookDefinition> findMatching(HookDefinition.HookTrigger trigger,
                                              HookDefinition.HookPhase phase) {
        return hooks.values().stream()
            .filter(h -> h.trigger() == trigger && h.phase() == phase && h.enabled())
            .sorted(java.util.Comparator.comparingInt(HookDefinition::priority))
            .collect(Collectors.toList());
    }

    /**
     * 启用一个钩子。
     */
    public void enable(String hookId) {
        var hook = hooks.get(hookId);
        if (hook != null) {
            hooks.put(hookId, new HookDefinition(
                hook.id(), hook.name(), hook.phase(), hook.trigger(),
                hook.action(), hook.priority(), true, hook.createdAt()
            ));
            log.debug("Hook enabled: {}", hookId);
        }
    }

    /**
     * 禁用一个钩子。
     */
    public void disable(String hookId) {
        var hook = hooks.get(hookId);
        if (hook != null) {
            hooks.put(hookId, new HookDefinition(
                hook.id(), hook.name(), hook.phase(), hook.trigger(),
                hook.action(), hook.priority(), false, hook.createdAt()
            ));
            log.debug("Hook disabled: {}", hookId);
        }
    }

    /**
     * 获取已注册钩子数量。
     */
    public int count() {
        return hooks.size();
    }

    /**
     * 清除所有钩子。
     */
    public void clear() {
        hooks.clear();
        log.debug("All hooks cleared");
    }
}
