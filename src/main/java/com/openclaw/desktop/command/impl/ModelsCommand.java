package com.openclaw.desktop.command.impl;

import com.openclaw.desktop.command.*;
import reactor.core.publisher.Mono;

/**
 * /models 命令 — 列出可用模型。
 */
public class ModelsCommand implements CommandHandler {
    @Override
    public CommandDefinition definition() {
        return CommandDefinition.query("models", "List available LLM models", "/models [--provider=id]");
    }

    @Override
    public Mono<CommandResult> execute(CommandInput input, CommandContext context) {
        var providerId = input.option("provider");
        if (context.providerRegistry() == null) {
            return Mono.just(CommandResult.error("No provider registry available"));
        }
        var providers = context.providerRegistry().all().stream().toList();
        if (providerId != null) {
            providers = providers.stream().filter(p -> p.id().equals(providerId)).toList();
        }
        if (providers.isEmpty()) {
            return Mono.just(CommandResult.error("No provider found: " + (providerId != null ? providerId : "any")));
        }
        var sb = new StringBuilder("Available Models:\n");
        for (var p : providers) {
            sb.append("\n[").append(p.name()).append("]\n");
            var models = p.listModels().collectList().block();
            for (var m : models) {
                sb.append("  • ").append(m.id()).append(" — ").append(m.name()).append("\n");
            }
        }
        return Mono.just(CommandResult.ok(sb.toString()));
    }
}
