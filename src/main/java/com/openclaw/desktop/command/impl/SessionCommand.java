package com.openclaw.desktop.command.impl;

import com.openclaw.desktop.command.*;
import reactor.core.publisher.Mono;

/**
 * /session 命令 — 列出/切换/管理会话。
 */
public class SessionCommand implements CommandHandler {
    @Override
    public CommandDefinition definition() {
        return CommandDefinition.mutation("session", "List, switch, or manage sessions", "/session [list|reset|delete] [id]");
    }

    @Override
    public Mono<CommandResult> execute(CommandInput input, CommandContext context) {
        var action = input.firstArg();
        if (action == null || action.equals("list")) {
            if (context.sessionManager() == null) {
                return Mono.just(CommandResult.ok("Current session: " + (context.session() != null ? context.session().key().toString() : "none")));
            }
            var sessions = context.sessionManager().list("default").collectList().block();
            var sb = new StringBuilder("Sessions (" + (sessions != null ? sessions.size() : 0) + "):\n");
            if (sessions != null) {
                for (var s : sessions) {
                    sb.append("  • ").append(s.key()).append(" — ").append(s.transcript().size()).append(" messages\n");
                }
            }
            return Mono.just(CommandResult.ok(sb.toString()));
        }

        if (action.equals("reset")) {
            if (context.session() != null) {
                context.session().reset().block();
                return Mono.just(CommandResult.ok("Session reset: " + context.session().key()));
            }
            return Mono.just(CommandResult.error("No active session to reset"));
        }

        if (action.equals("delete")) {
            return Mono.just(CommandResult.error("Session delete not yet implemented"));
        }

        return Mono.just(CommandResult.error("Unknown session action: " + action + ". Use: list, reset, delete"));
    }
}
