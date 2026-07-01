package com.openclaw.desktop.command;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 命令注册表 — 注册和查找所有斜杠命令。
 * 对应 OpenClaw 的 CommandRegistry。
 *
 * <p>命令格式： /commandName [args...] [--options]
 *
 * <p>内置命令列表（对应 OpenClaw 的 CLI commands））：
 * <ul>
 *   <li>/status — 知看网关和 Agent 状态</li>
 *   <li>/models — 列出可用模型</li>
 *   <li>/config — 查看/修改配置</li>
 *   <li>/session — 列出/切换会话</li>
 *   <li>/help — 显示帮助</li>
 *   <li>/compact — 手动压缩上下文</li>
 *   <li>/reset — 重置会话</li>
 *   <li>/tools — 列出可用工具</li>
 *   <li>/skills — 列出可用技能</li>
 *   <li>/doctor -- 运行诊断检查</li>
 * </ul>
 */
public class CommandRegistry {

    private static final Logger log = LoggerFactory.getLogger(CommandRegistry.class);

    private final Map<String, CommandHandler> handlers = new ConcurrentHashMap<>();
    private final Map<String, CommandHandler> aliasMap = new ConcurrentHashMap<>();

    public CommandRegistry() {
        // 内置命令在 CommandManager 中统一注册
    }

    /**
     * 注册一个命令处理器。
     */
    public void register(CommandHandler handler) {
        var def = handler.definition();
        handlers.put(def.name(), handler);
        for (var alias : def.aliases()) {
            aliasMap.put(alias, handler);
            log.debug("Command alias registered: {} -> {}", alias, def.name());
        }
        log.info("Command registered: {}", def.name());
    }

    /**
     * 注销一个命令处理器。
     */
    public void unregister(String name) {
        var handler = handlers.remove(name);
        if (handler != null) {
            for (var alias : handler.definition().aliases()) {
                aliasMap.remove(alias);
            }
            log.info("Command unregistered: {}", name);
        }
    }

    /**
     * 查找命令处理器。
     */
    public Optional<CommandHandler> find(String name) {
        var handler = handlers.get(name);
        if (handler != null) return Optional.of(handler);
        handler = aliasMap.get(name);
        if (handler != null) return Optional.of(handler);
        return Optional.empty();
    }

    /**
     * 执行命令。
     */
    public Mono<CommandResult> execute(CommandInput input, CommandContext context) {
        if (!input.isCommand()) {
            return Mono.just(CommandResult.error("Not a command: " + input.rawText()));
        }

        var handler = find(input.commandName());
        if (handler.isEmpty()) {
            return Mono.just(CommandResult.error("Unknown command: /" + input.commandName()));
        }

        log.debug("Executing command: /{} args={} opts={}",
            input.commandName(), input.arguments().size(), input.options().size());

        return handler.get().execute(input, context)
            .doOnSuccess(result -> {
                if (result.success()) {
                    log.info("Command /{} succeeded", input.commandName());
                } else {
                    log.warn("Command /{} failed: {}", input.commandName(), result.errorMessage());
                }
            })
            .onErrorResume(e -> {
                log.error("Command /{} error: {}", input.commandName(), e.getMessage());
                return Mono.just(CommandResult.error(e.getMessage()));
            });
    }

    /**
     * 列出所有已注册命令。
     */
    public Flux<CommandDefinition> listDefinitions() {
        return Flux.fromIterable(handlers.values().stream().map(CommandHandler::definition).toList());
    }

    /**
     * 列出所有命令名称。
     */
    public Set<String> commandNames() {
        var names = new HashSet<>(handlers.keySet());
        names.addAll(aliasMap.keySet());
        return names;
    }

    /**
     * 判断命令是否存在。
     */
    public boolean hasCommand(String name) {
        return handlers.containsKey(name) || aliasMap.containsKey(name);
    }

    /**
     * 获取命令数量。
     */
    public int size() {
        return handlers.size();
    }
}
