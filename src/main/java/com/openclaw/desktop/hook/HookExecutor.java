package com.openclaw.desktop.hook;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 钩子执行器 — 按优先级顺序执行钩子链。
 *
 * <p>对应 OpenClaw 的 Hookable / HookChain 机制。
 * 核心逻辑：
 * <ul>
 *   <li>按 trigger + phase 匹配所有注册的钩子</li>
 *   <li>按 priority 排序（低优先级先执行）</li>
 *   <li>链式执行：每个钩子的结果影响后续钩子</li>
 *   <li>如果任何钩子返回 block，立即中断链</li>
 * </ul>
 */
public class HookExecutor {

    private static final Logger log = LoggerFactory.getLogger(HookExecutor.class);
    private final CopyOnWriteArrayList<HookDefinition> hooks = new CopyOnWriteArrayList<>();

    /**
     * 注册钩子。
     */
    public void register(HookDefinition hook) {
        hooks.add(hook);
        log.info("Hook registered: id={}, trigger={}, phase={}, priority={}",
            hook.id(), hook.trigger(), hook.phase(), hook.priority());
    }

    /**
     * 移除钩子。
     */
    public void unregister(String hookId) {
        hooks.removeIf(h -> h.id().equals(hookId));
        log.info("Hook unregistered: id={}", hookId);
    }

    /**
     * 执行钩子链。
     *
     * @param trigger 触发事件
     * @param phase 触发阶段
     * @param context 钩子上下文
     * @return 最终结果
     */
    public Mono<HookResult> execute(
        HookDefinition.HookTrigger trigger,
        HookDefinition.HookPhase phase,
        HookContext context
    ) {
        var matching = hooks.stream()
            .filter(h -> h.enabled())
            .filter(h -> h.trigger() == trigger)
            .filter(h -> h.phase() == phase)
            .sorted(Comparator.comparingInt(HookDefinition::priority))
            .toList();

        if (matching.isEmpty()) {
            return Mono.just(HookResult.pass());
        }

        log.debug("Executing {} hooks for trigger={}, phase={}",
            matching.size(), trigger, phase);

        var currentContent = extractContent(context);
        String blockedByHookId = null;
        java.util.List<String> injectContents = new java.util.ArrayList<>();

        for (var hook : matching) {
            if (blockedByHookId != null) break;

            var result = executeHook(hook, context, currentContent);
            if (!result.shouldContinue()) {
                log.info("Hook {} blocked the chain: reason={}",
                    hook.id(), result.blockReason());
                blockedByHookId = hook.id();
            }
            if (result.modifiedContent() != null) {
                currentContent = result.modifiedContent();
            }
            if (result.injectContent() != null) {
                injectContents.add(result.injectContent());
            }
        }

        if (blockedByHookId != null) {
            return Mono.just(HookResult.block("Blocked by hook: " + blockedByHookId));
        }

        var injectContent = injectContents.isEmpty() ? null : String.join("\n", injectContents);

        return Mono.just(new HookResult(
            true,
            currentContent.equals(extractContent(context)) ? null : currentContent,
            injectContent,
            null,
            java.util.Map.of()
        ));
    }

    /**
     * 获取所有注册的钩子。
     */
    public Flux<HookDefinition> listAll() {
        return Flux.fromIterable(hooks);
    }

    /**
     * 获取匹配的钩子。
     */
    public List<HookDefinition> getMatching(
        HookDefinition.HookTrigger trigger,
        HookDefinition.HookPhase phase
    ) {
        return hooks.stream()
            .filter(h -> h.enabled() && h.trigger() == trigger && h.phase() == phase)
            .sorted(Comparator.comparingInt(HookDefinition::priority))
            .toList();
    }

    /**
     * 启用/禁用钩子。
     */
    public void setEnabled(String hookId, boolean enabled) {
        hooks.stream()
            .filter(h -> h.id().equals(hookId))
            .findFirst()
            .ifPresentOrElse(
                h -> {
                    hooks.remove(h);
                    hooks.add(new HookDefinition(
                        h.id(), h.name(), h.phase(), h.trigger(),
                        h.action(), h.priority(), enabled, h.createdAt()
                    ));
                },
                () -> log.warn("Hook not found: {}", hookId)
            );
    }

    // ---- 内部方法 ----

    private HookResult executeHook(HookDefinition hook, HookContext context, String currentContent) {
        // 简化实现：根据 action 类型返回结果
        return switch (hook.action()) {
            case HookDefinition.HookAction.LOG -> {
                log.info("Hook {}: logging for trigger={}", hook.id(), hook.trigger());
                yield HookResult.pass();
            }
            case HookDefinition.HookAction.BLOCK -> HookResult.block("Blocked by " + hook.id());
            case HookDefinition.HookAction.INJECT -> HookResult.inject("Injected by " + hook.id());
            case HookDefinition.HookAction.MODIFY -> {
                if (currentContent != null) {
                    yield HookResult.modify(currentContent + " [modified by " + hook.id() + "]");
                } else {
                    yield HookResult.pass();
                }
            }
            case HookDefinition.HookAction.EXECUTE -> HookResult.pass();
        };
    }

    private String extractContent(HookContext context) {
        return switch (context) {
            case HookContext.ChatHookContext(var a, var m, var msg, var meta) -> msg;
            case HookContext.ToolCallHookContext(var a, var t, var id, var args, var meta) -> args;
            case HookContext.SessionHookContext(var s, var a, var meta) -> s;
            case HookContext.ConfigReloadHookContext(var p, var ok, var err) -> p;
        };
    }
}
