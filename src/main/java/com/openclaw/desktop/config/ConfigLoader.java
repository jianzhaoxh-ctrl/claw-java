package com.openclaw.desktop.config;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 配置加载器 — 从 HOCON 文件加载配置，支持环境变量覆盖。
 */
public final class ConfigLoader {

    private static final Logger log = LoggerFactory.getLogger(ConfigLoader.class);

    private static final String CONFIG_FILE = "application.conf";
    private static final String ENV_PREFIX = "CLAW_";

    private ConfigLoader() {}

    public static ClawConfig load() {
        // 1. 先尝试用户配置目录
        var userConfig = getUserConfigPath();
        if (userConfig != null && Files.exists(userConfig)) {
            return load(userConfig);
        }
        // 2. 回退到 classpath
        return load(null);
    }

    private static Path getUserConfigPath() {
        var home = System.getProperty("user.home");
        if (home == null) return null;
        return Path.of(home, ".clawdesktop", CONFIG_FILE);
    }

    public static ClawConfig load(Path configPath) {
        Config hocon;

        if (configPath != null && Files.exists(configPath)) {
            log.info("Loading config from: {}", configPath);
            hocon = ConfigFactory.parseFile(configPath.toFile()).resolve();
        } else {
            // try user config dir first
            var userConfig = getUserConfigPath();
            if (userConfig != null && Files.exists(userConfig)) {
                log.info("Loading config from: {}", userConfig);
                hocon = ConfigFactory.parseFile(userConfig.toFile()).resolve();
            } else {
                // fallback to classpath
                log.info("Loading config from classpath: {}", CONFIG_FILE);
                hocon = ConfigFactory.load(CONFIG_FILE);
            }
        }

        // apply env overrides
        hocon = applyEnvOverrides(hocon);

        return toClawConfig(hocon);
    }

    // ---- internal ----

    private static Config applyEnvOverrides(Config base) {
        var overrides = new HashMap<String, Object>();
        for (var entry : System.getenv().entrySet()) {
            if (entry.getKey().startsWith(ENV_PREFIX)) {
                var key = entry.getKey()
                    .substring(ENV_PREFIX.length())
                    .toLowerCase()
                    .replace('_', '.');
                overrides.put(key, entry.getValue());
                log.debug("Env override: {} = ***", key);
            }
        }
        if (overrides.isEmpty()) return base;
        return ConfigFactory.parseMap(overrides, "env-overrides").withFallback(base);
    }

    private static ClawConfig toClawConfig(Config c) {
        var gateway = new ClawConfig.GatewayConfig(
            c.hasPath("gateway.port") ? c.getInt("gateway.port") : 7180,
            c.hasPath("gateway.ws-port") ? c.getInt("gateway.ws-port")
            : c.hasPath("gateway.wsPort") ? c.getInt("gateway.wsPort") : 7181,
            c.hasPath("gateway.bind-address") ? c.getString("gateway.bind-address")
            : c.hasPath("gateway.bindAddress") ? c.getString("gateway.bindAddress") : "127.0.0.1",
            c.hasPath("gateway.cors-enabled") ? c.getBoolean("gateway.cors-enabled")
            : c.hasPath("gateway.corsEnabled") ? c.getBoolean("gateway.corsEnabled") : true,
            c.hasPath("gateway.control-ui-root") ? c.getString("gateway.control-ui-root")
            : c.hasPath("gateway.controlUiRoot") ? c.getString("gateway.controlUiRoot") : null
        );

        var agent = new ClawConfig.AgentConfig(
            c.hasPath("agent.id") ? c.getString("agent.id") : "default",
            c.hasPath("agent.name") ? c.getString("agent.name") : "ClawDesktop",
            c.hasPath("agent.model-id") ? c.getString("agent.model-id")
            : c.hasPath("agent.modelId") ? c.getString("agent.modelId") : "gpt-4o",
            c.hasPath("agent.system-prompt") ? c.getString("agent.system-prompt")
            : c.hasPath("agent.systemPrompt") ? c.getString("agent.systemPrompt") : "You are ClawDesktop, a helpful personal AI assistant.",
            c.hasPath("agent.reasoning-level") ? c.getString("agent.reasoning-level")
            : c.hasPath("agent.reasoningLevel") ? c.getString("agent.reasoningLevel") : "off",
            c.hasPath("agent.max-tokens") ? c.getInt("agent.max-tokens")
            : c.hasPath("agent.maxTokens") ? c.getInt("agent.maxTokens") : 4096,
            c.hasPath("agent.temperature") ? c.getDouble("agent.temperature") : 0.7,
            c.hasPath("agent.tool-names") ? c.getStringList("agent.tool-names")
            : c.hasPath("agent.toolNames") ? c.getStringList("agent.toolNames") : List.of()
        );

        var memory = new ClawConfig.MemoryConfig(
            c.hasPath("memory.db-path") ? c.getString("memory.db-path")
            : c.hasPath("memory.dbPath") ? c.getString("memory.dbPath") : "data/memory/claw.db",
            c.hasPath("memory.embedding-enabled") ? c.getBoolean("memory.embedding-enabled")
            : c.hasPath("memory.embeddingEnabled") ? c.getBoolean("memory.embeddingEnabled") : false,
            c.hasPath("memory.embedding-model") ? c.getString("memory.embedding-model")
            : c.hasPath("memory.embeddingModel") ? c.getString("memory.embeddingModel") : "text-embedding-3-small"
        );

        // LLM providers
        Map<String, ClawConfig.LlmConfig.ProviderConfig> providers = new HashMap<>();
        if (c.hasPath("llm.providers")) {
            var providersConfig = c.getConfig("llm.providers");
            for (var providerId : providersConfig.root().keySet()) {
                var pc = providersConfig.getConfig(providerId);
                providers.put(providerId, new ClawConfig.LlmConfig.ProviderConfig(
                    pc.hasPath("apiKey") ? pc.getString("apiKey")
                    : pc.hasPath("api-key") ? pc.getString("api-key")
                    : pc.hasPath("apikey") ? pc.getString("apikey") : null,
                    pc.hasPath("base-url") ? pc.getString("base-url")
                    : pc.hasPath("baseUrl") ? pc.getString("baseUrl") : null,
                    pc.hasPath("options") ? pc.getObject("options").unwrapped() : Map.of()
                ));
            }
        }

        var llm = new ClawConfig.LlmConfig(
            c.hasPath("llm.default-provider") ? c.getString("llm.default-provider")
            : c.hasPath("llm.defaultProvider") ? c.getString("llm.defaultProvider") : "openai",
            providers
        );

        // channels
        List<ClawConfig.ChannelConfig> channels = List.of();
        if (c.hasPath("channels")) {
            channels = c.getConfigList("channels").stream()
                .map(ch -> new ClawConfig.ChannelConfig(
                    ch.getString("id"),
                    ch.hasPath("enabled") ? ch.getBoolean("enabled") : true,
                    ch.hasPath("settings") ? ch.getObject("settings").unwrapped() : Map.of()
                ))
                .collect(Collectors.toList());
        }

        return new ClawConfig(gateway, agent, llm, channels, memory, Map.of());
    }
}
