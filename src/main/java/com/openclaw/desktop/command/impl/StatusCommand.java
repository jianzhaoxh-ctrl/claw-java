package com.openclaw.desktop.command.impl;

import com.openclaw.desktop.command.*;
import reactor.core.publisher.Mono;

/**
 * /status 命令 — 显示网关和 Agent 状态。
 */
public class StatusCommand implements CommandHandler {
    @Override
    public CommandDefinition definition() {
        return CommandDefinition.query("status", "Show gateway and agent status", "/status [--verbose] [--json]");
    }

    @Override
    public Mono<CommandResult> execute(CommandInput input, CommandContext context) {
        var verbose = input.hasOption("verbose") || input.hasOption("v");

        var sb = new StringBuilder();
        sb.append("=== ClawDesktop Status ===\n");
        sb.append("Gateway: running\n");

        if (context.providerRegistry() != null) {
            var providers = context.providerRegistry().all().stream().toList();
            sb.append("Providers: ").append(providers.size()).append(" registered\n");
            for (var p : providers) {
                sb.append("  • ").append(p.name()).append(" (").append(p.id()).append(")\n");
            }
        }

        if (context.session() != null) {
            sb.append("Session: ").append(context.session().key()).append("\n");
            sb.append("Messages: ").append(context.session().transcript().size()).append("\n");
        }

        if (context.agent() != null) {
            sb.append("Agent: ").append(context.agent().config().modelId()).append("\n");
            sb.append("State: ").append(context.agent().state()).append("\n");
        }

        sb.append("Tools: ").append(context.toolRegistry() != null ? context.toolRegistry().size() : 0).append(" registered\n");

        if (verbose && context.toolRegistry() != null) {
            for (var t : context.toolRegistry().listDescriptors()) {
                sb.append("  • ").append(t.name()).append(" — ").append(t.description()).append("\n");
            }
        }

        return Mono.just(CommandResult.ok(sb.toString()));
    }
}
