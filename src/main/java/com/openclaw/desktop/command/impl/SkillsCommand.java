package com.openclaw.desktop.command.impl;

import com.openclaw.desktop.command.*;
import com.openclaw.desktop.skill.SkillDefinition;
import reactor.core.publisher.Mono;

/**
 * /skills 命令 — 列出可用技能。
 */
public class SkillsCommand implements CommandHandler {
    @Override
    public CommandDefinition definition() {
        return CommandDefinition.query("skills", "List available skills", "/skills [--verbose]");
    }

    @Override
    public Mono<CommandResult> execute(CommandInput input, CommandContext context) {
        if (context.skillManager() == null) {
            return Mono.just(CommandResult.error("No skill manager available"));
        }
        var verbose = input.hasOption("verbose") || input.hasOption("v");
        var skills = context.skillManager().all();
        var sb = new StringBuilder("Available Skills (" + skills.size() + "):\n");
        for (var s : skills) {
            sb.append("  • ").append(s.name());
            if (verbose) {
                sb.append(" — ").append(s.description());
            }
            sb.append("\n");
        }
        return Mono.just(CommandResult.ok(sb.toString()));
    }
}
