package com.openclaw.desktop.agent;

import java.time.Instant;
import java.util.List;

/**
 * Agent 配置。
 */
public record AgentConfig(
    String id,
    String name,
    String modelId,
    String systemPrompt,
    ReasoningLevel reasoningLevel,
    int maxTokens,
    double temperature,
    List<String> toolNames,
    Instant createdAt
) {
    public static AgentConfig defaults() {
        return new AgentConfig(
            "default", "ClawDesktop", "gpt-4o",
            "You are ClawDesktop, a helpful personal AI assistant.",
            ReasoningLevel.OFF, 4096, 0.7,
            List.of(), Instant.now()
        );
    }
}
