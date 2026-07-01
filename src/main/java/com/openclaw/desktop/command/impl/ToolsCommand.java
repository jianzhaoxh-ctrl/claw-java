package com.openclaw.desktop.command.impl;

import com.openclaw.desktop.command.*;
import reactor.core.publisher.Mono;

/**
 * /tools 命令 — 列出可用工具。
 */
public class ToolsCommand implements CommandHandler {
    @Override
    public CommandDefinition definition() {
        return CommandDefinition.query("tools", "List available tools", "/tools [--verbose]");
    }

    @Override
    public Mono<CommandResult> execute(CommandInput input, CommandContext context) {
        if (context.toolRegistry() == null) {
            return Mono.just(CommandResult.error("No tool registry available"));
        }
        var verbose = input.hasOption("verbose") || input.hasOption("v");
        var descriptors = context.toolRegistry().listDescriptors();
        var sb = new StringBuilder("Available Tools (" + descriptors.size() + "):\n");
        for (var t : descriptors) {
            sb.append("  • ").append(t.name());
            if (verbose) {
                sb.append(" — ").append(t.description());
            }
            sb.append("\n");
        }
        return Mono.just(CommandResult.ok(sb.toString()));
    }
}
