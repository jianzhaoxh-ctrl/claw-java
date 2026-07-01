package com.openclaw.desktop.config;

import java.util.List;
import java.util.Map;

/**
 * 主配置类 — 对应 OpenClaw 的 OpenClawConfig。
 * 使用 Java record + 嵌套 record 表达配置层级。
 */
public record ClawConfig(
    GatewayConfig gateway,
    AgentConfig agent,
    LlmConfig llm,
    List<ChannelConfig> channels,
    MemoryConfig memory,
    Map<String, Object> extras
) {

    // ---- Gateway ----
    public record GatewayConfig(
        int port,
        int wsPort,
        String bindAddress,
        boolean corsEnabled,
        String controlUiRoot
    ) {
        public static GatewayConfig defaults() {
            return new GatewayConfig(7180, 7181, "127.0.0.1", true, null);
        }
    }

    // ---- Agent ----
    public record AgentConfig(
        String id,
        String name,
        String modelId,
        String systemPrompt,
        String reasoningLevel,
        int maxTokens,
        double temperature,
        List<String> toolNames
    ) {
        public static AgentConfig defaults() {
            return new AgentConfig(
                "default", "ClawDesktop", "gpt-4o",
                "You are ClawDesktop, a helpful personal AI assistant.",
                "off", 4096, 0.7, List.of()
            );
        }
    }

    // ---- LLM ----
    public record LlmConfig(
        String defaultProvider,
        Map<String, ProviderConfig> providers
    ) {
        public record ProviderConfig(
            String apiKey,
            String baseUrl,
            Map<String, Object> options
        ) {}
    }

    // ---- Channel ----
    public record ChannelConfig(
        String id,
        boolean enabled,
        Map<String, Object> settings
    ) {}

    // ---- Memory ----
    public record MemoryConfig(
        String dbPath,
        boolean embeddingEnabled,
        String embeddingModel
    ) {
        public static MemoryConfig defaults() {
            return new MemoryConfig("data/memory/claw.db", false, "text-embedding-3-small");
        }
    }

    // ---- factory ----

    public static ClawConfig defaults() {
        return new ClawConfig(
            GatewayConfig.defaults(),
            AgentConfig.defaults(),
            new LlmConfig("openai", Map.of()),
            List.of(),
            MemoryConfig.defaults(),
            Map.of()
        );
    }
}
