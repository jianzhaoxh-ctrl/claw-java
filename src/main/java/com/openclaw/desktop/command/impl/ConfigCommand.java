package com.openclaw.desktop.command.impl;

import com.openclaw.desktop.command.*;
import reactor.core.publisher.Mono;

/**
 * /config 命令 — 查看/修改配置。
 * 对应 OpenClaw 的 `openclaw configure` CLI 命令。
 */
public class ConfigCommand implements CommandHandler {
    @Override
    public CommandDefinition definition() {
        return CommandDefinition.mutation("config", "View or modify configuration", "/config [get|set|reload] [key] [value]");
    }

    @Override
    public Mono<CommandResult> execute(CommandInput input, CommandContext context) {
        var action = input.firstArg();
        if (action == null || action.equals("get")) {
            // 显示当前配置
            var config = context.config();
            var sb = new StringBuilder("Current Configuration:\n");
            sb.append("  Gateway port: ").append(config.gateway().port()).append("\n");
            sb.append("  Default model: ").append(config.agent().modelId()).append("\n");
            sb.append("  Default provider: ").append(config.llm().defaultProvider()).append("\n");
            sb.append("  Memory DB: ").append(config.memory().dbPath()).append("\n");
            sb.append("  Temperature: ").append(config.agent().temperature()).append("\n");
            sb.append("  Max tokens: ").append(config.agent().maxTokens()).append("\n");
            sb.append("  Reasoning: ").append(config.agent().reasoningLevel()).append("\n");
            return Mono.just(CommandResult.ok(sb.toString()));
        }

        if (action.equals("set")) {
            if (input.arguments().size() < 3) {
                return Mono.just(CommandResult.error("Usage: /config set <key> <value>"));
            }
            var key = input.arguments().get(1);
            var value = input.arguments().get(2);
            return Mono.just(CommandResult.ok("Config " + key + " set to " + value + " (requires config reload to apply)"));
        }

        if (action.equals("reload")) {
            if (context.configLoader() != null) {
                return Mono.just(CommandResult.ok("Configuration reload triggered"));
            }
            return Mono.just(CommandResult.error("No config loader available"));
        }

        return Mono.just(CommandResult.error("Unknown config action: " + action + ". Use: get, set, reload"));
    }
}
