package com.openclaw.desktop.command.impl;

import com.openclaw.desktop.command.*;
import com.openclaw.desktop.context.ContextEngine;
import com.openclaw.desktop.context.CompactConfig;
import reactor.core.publisher.Mono;

/**
 * /compact 命令 — 手动压缩对话上下文。
 * 对应 OpenClaw 的 `/compact` 命令。
 */
public class CompactCommand implements CommandHandler {
    @Override
    public CommandDefinition definition() {
        return CommandDefinition.mutation("compact", "Manually compact conversation context", "/compact [--strategy=summary|truncate]");
    }

    @Override
    public Mono<CommandResult> execute(CommandInput input, CommandContext context) {
        if (context.session() == null) {
            return Mono.just(CommandResult.error("No active session to compact"));
        }

        var strategy = input.option("strategy");
        var config = strategy != null
            ? new CompactConfig(strategy, 4000, true, true, 4, "Summarize the conversation concisely")
            : CompactConfig.defaults();

        var engine = new ContextEngine();
        // 将 TranscriptEntry 转为 Message
        var messages = new java.util.ArrayList<com.openclaw.desktop.llm.Message>();
        for (var e : context.session().transcript().entries()) {
            switch (e.role()) {
                case "user"      -> messages.add(new com.openclaw.desktop.llm.Message.UserMessage(e.content()));
                case "assistant" -> messages.add(new com.openclaw.desktop.llm.Message.AssistantMessage(e.content(), java.util.List.of()));
                case "system"    -> messages.add(new com.openclaw.desktop.llm.Message.SystemMessage(e.content()));
                default           -> messages.add(new com.openclaw.desktop.llm.Message.UserMessage(e.content()));
            }
        }
        var result = engine.compact(messages, config);

        var sb = new StringBuilder("Context compacted:\n");
        sb.append("  Original tokens: ").append(result.originalTokens()).append("\n");
        sb.append("  Compacted tokens: ").append(result.compactedTokens()).append("\n");
        sb.append("  Ratio: ").append(String.format("%.1f%%", result.compressionRatio() * 100)).append("\n");

        return Mono.just(CommandResult.ok(sb.toString()));
    }
}
