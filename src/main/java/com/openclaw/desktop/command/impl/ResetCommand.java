package com.openclaw.desktop.command.impl;

import com.openclaw.desktop.command.*;
import reactor.core.publisher.Mono;

/**
 * /reset 命令 — 重置当前会话。
 */
public class ResetCommand implements CommandHandler {
    @Override
    public CommandDefinition definition() {
        return CommandDefinition.dangerous("reset", "Reset current session (clears all messages)", "/reset");
    }

    @Override
    public Mono<CommandResult> execute(CommandInput input, CommandContext context) {
        if (context.agent() == null) {
            return Mono.just(CommandResult.error("No active agent to reset"));
        }
        return context.agent().reset().then(Mono.just(CommandResult.ok("Session and agent reset successfully")));
    }
}
