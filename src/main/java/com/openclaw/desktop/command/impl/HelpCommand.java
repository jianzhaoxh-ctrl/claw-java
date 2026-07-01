package com.openclaw.desktop.command.impl;

import com.openclaw.desktop.command.*;
import reactor.core.publisher.Mono;

import java.util.Comparator;

/**
 * /help 命令 — 显示帮助信息。
 */
public class HelpCommand implements CommandHandler {
    @Override
    public CommandDefinition definition() {
        return CommandDefinition.query("help", "Show available commands", "/help [command]");
    }

    @Override
    public Mono<CommandResult> execute(CommandInput input, CommandContext context) {
        var target = input.firstArg();
        if (target != null) {
            // 显示特定命令的详细帮助
            // 这个需要 CommandRegistry，但 context 里暂时没有
            return Mono.just(CommandResult.ok("Help for /" + target + " — see /" + target + " for usage"));
        }

        var sb = new StringBuilder("=== Available Commands ===\n\n");
        sb.append("/status    — Show gateway and agent status\n");
        sb.append("/models    — List available LLM models\n");
        sb.append("/config    — View or modify configuration\n");
        sb.append("/session   — List, switch, or manage sessions\n");
        sb.append("/compact   — Manually compact conversation context\n");
        sb.append("/reset     — Reset current session\n");
        sb.append("/tools     — List available tools\n");
        sb.append("/skills    — List available skills\n");
        sb.append("/doctor    — Run diagnostic checks\n");
        sb.append("/help      — Show this help\n");
        sb.append("\nType /help <command> for detailed usage.\n");
        return Mono.just(CommandResult.ok(sb.toString()));
    }
}
