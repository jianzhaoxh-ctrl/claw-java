package com.openclaw.desktop;

import com.openclaw.desktop.config.ClawConfig;
import com.openclaw.desktop.config.ConfigLoader;
import com.openclaw.desktop.cron.CronExecutor;
import com.openclaw.desktop.cron.CronJob;
import com.openclaw.desktop.cron.CronJobStore;
import com.openclaw.desktop.cron.CronScheduler;
import com.openclaw.desktop.gateway.server.GatewayServer;
import com.openclaw.desktop.llm.LlmProviderRegistry;
import com.openclaw.desktop.mcp.McpClientManager;
import com.openclaw.desktop.plugin.EventBus;
import com.openclaw.desktop.plugin.PluginContext;
import com.openclaw.desktop.plugin.PluginManager;
import com.openclaw.desktop.session.SessionManager;
import com.openclaw.desktop.skill.SkillManager;
import com.openclaw.desktop.skill.SkillRegistry;
import com.openclaw.desktop.tool.ToolRegistry;
import com.openclaw.desktop.tool.core.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 核心启动类 — 负责初始化并管理所有子系统生命周期。
 */
public class ClawDesktop {

    private static final Logger log = LoggerFactory.getLogger(ClawDesktop.class);

    private ClawConfig config;
    private GatewayServer gatewayServer;
    private LlmProviderRegistry providerRegistry;
    private ToolRegistry toolRegistry;
    private SessionManager sessionManager;
    private CronScheduler cronScheduler;
    private CronJobStore cronJobStore;
    private SkillRegistry skillRegistry;
    private SkillManager skillManager;
    private EventBus eventBus;
    private PluginManager pluginManager;
    private McpClientManager mcpManager;
    private volatile boolean running;

    public void start() {
        if (running) {
            log.warn("ClawDesktop already running");
            return;
        }
        log.info("=== ClawDesktop Boot Sequence ===");

        // 1. Load config
        config = ConfigLoader.load();
        log.info("Config loaded: agentId={}, model={}", config.agent().id(), config.agent().modelId());

        // 2. Initialize tool registry
        toolRegistry = new ToolRegistry();
        registerBuiltinTools();
        log.info("Tool registry initialized: {} tools", toolRegistry.size());

        // 3. Initialize LLM provider registry
        providerRegistry = new LlmProviderRegistry();
        registerProviders();
        log.info("LLM providers registered: {}", providerRegistry.size());

        // 3.5 Initialize new subsystems: Session / Cron / Skills / EventBus / MCP / Plugins
        sessionManager = new SessionManager();
        eventBus = new EventBus();
        initCronSystem();
        initSkillSystem();
        initMcpSystem();
        initPluginSystem();
        log.info("Subsystems ready: sessions={}, cronJobs={}, skills={}, plugins={}",
            sessionManager.count(), cronScheduler.list().size(),
            skillManager.count(), pluginManager.activeCount());

        // 4. Start gateway server with Agent
        var agentConfig = com.openclaw.desktop.agent.AgentConfig.defaults();
        if (providerRegistry.size() > 0) {
            agentConfig = new com.openclaw.desktop.agent.AgentConfig(
                agentConfig.id(), agentConfig.name(), config.agent().modelId(),
                config.agent().systemPrompt(),
                com.openclaw.desktop.agent.ReasoningLevel.valueOf(config.agent().reasoningLevel().toUpperCase()),
                config.agent().maxTokens(), config.agent().temperature(),
                java.util.List.of(), java.time.Instant.now()
            );
        }
        var defaultProvider = providerRegistry.getDefault();
        if (defaultProvider == null) {
            throw new RuntimeException("No LLM provider available");
        }
        var session = new com.openclaw.desktop.session.Session(
            com.openclaw.desktop.session.SessionKey.main(agentConfig.id())
        );
        var agent = new com.openclaw.desktop.agent.Agent(agentConfig, defaultProvider, toolRegistry, session);

        gatewayServer = new GatewayServer(config, agent, toolRegistry);
        gatewayServer.start().block();
        log.info("Gateway server started on port {}", config.gateway().port());

        running = true;
        log.info("=== ClawDesktop Ready ===");
    }

    // ---- Subsystem initializers (A1/A2/A3/A4) ----

    /** 初始化 Cron 调度系统（含 SQLite 持久化）。 */
    private void initCronSystem() {
        var dbPath = java.nio.file.Paths.get(config.memory().dbPath()).getParent();
        var cronDb = dbPath != null ? dbPath.resolve("cron.db").toString() : "data/cron.db";
        cronJobStore = new CronJobStore(cronDb);
        try {
            cronJobStore.initialize();
        } catch (Exception e) {
            log.warn("Failed to init cron store: {}", e.getMessage());
        }
        cronScheduler = new CronScheduler(new CronExecutor() {
            @Override
            public void execute(CronJob job) {
                log.info("Cron job triggered: {} ({})", job.name(), job.id());
                // TODO: 将 SYSTEM_EVENT / AGENT_TURN 转发到 Agent / 事件总线
                switch (job.payload().kind()) {
                    case SYSTEM_EVENT -> eventBus.publish(
                        new com.openclaw.desktop.plugin.PluginEvent.CustomEvent(
                            "cron." + job.name(), job.payload().text(), "cron"));
                    case AGENT_TURN -> log.info("Cron agent turn: model={} msg={}",
                        job.payload().model(), job.payload().message());
                }
            }
        }, cronJobStore);
        cronScheduler.start();
    }

    /** 初始化技能系统：先加载 classpath 内置技能，再扫描数据目录 skills/。 */
    private void initSkillSystem() {
        var dataDir = java.nio.file.Paths.get(
            System.getProperty("user.home"), ".clawdesktop", "skills");
        skillRegistry = new SkillRegistry(dataDir);
        skillManager = new SkillManager(skillRegistry);
        skillManager.loadBuiltin();
        try { skillManager.loadAll(); }
        catch (Exception e) { log.warn("Skill scan failed: {}", e.getMessage()); }
    }

    /** 初始化 MCP 客户端管理器并连接已配置的 stdio 服务器。 */
    private void initMcpSystem() {
        mcpManager = new McpClientManager(toolRegistry);
        // TODO: 从 config 读取 MCP 服务器列表并 registerServer
        try { mcpManager.connectAll(); }
        catch (Exception e) { log.warn("MCP connect failed: {}", e.getMessage()); }
    }

    /** 初始化插件系统：构建 PluginContext 并加载内置 + 外部插件。 */
    private void initPluginSystem() {
        var dataDir = java.nio.file.Paths.get(
            System.getProperty("user.home"), ".clawdesktop");
        var pluginsDir = dataDir.resolve("plugins");
        var baseCtx = new PluginContext(
            config, providerRegistry, toolRegistry, sessionManager,
            cronScheduler, skillRegistry, eventBus, dataDir, "core"
        );
        pluginManager = new PluginManager(baseCtx, pluginsDir);
        try {
            pluginManager.loadAll();
        } catch (Exception e) {
            log.error("Plugin load failed: {}", e.getMessage(), e);
        }
    }

    private void registerBuiltinTools() {
        toolRegistry.register(new ReadFileTool());
        toolRegistry.register(new WriteFileTool());
        toolRegistry.register(new EditFileTool());
        toolRegistry.register(new DeleteFileTool());
        toolRegistry.register(new ListFilesTool());
        toolRegistry.register(new ShellExecTool());
        toolRegistry.register(new WebSearchTool());
        toolRegistry.register(new WebFetchTool());
        toolRegistry.register(new ProcessTool());
    }

    private void registerProviders() {
        var llmConfig = config.llm();
        var providers = llmConfig.providers();

        // OpenAI
        var openaiCfg = providers.get("openai");
        if (openaiCfg != null && openaiCfg.apiKey() != null) {
            try {
                var provider = new com.openclaw.desktop.llm.provider.OpenAiProvider(
                    openaiCfg.apiKey(), openaiCfg.baseUrl());
                providerRegistry.register(provider);
            } catch (Exception e) {
                log.warn("Failed to register OpenAI provider: {}", e.getMessage());
            }
        }

        // Anthropic
        var anthropicCfg = providers.get("anthropic");
        if (anthropicCfg != null && anthropicCfg.apiKey() != null) {
            try {
                var provider = new com.openclaw.desktop.llm.provider.AnthropicProvider(
                    anthropicCfg.apiKey(), anthropicCfg.baseUrl());
                providerRegistry.register(provider);
            } catch (Exception e) {
                log.warn("Failed to register Anthropic provider: {}", e.getMessage());
            }
        }

        // DeepSeek
        var deepseekCfg = providers.get("deepseek");
        if (deepseekCfg != null && deepseekCfg.apiKey() != null) {
            try {
                var provider = new com.openclaw.desktop.llm.provider.DeepSeekProvider(
                    deepseekCfg.apiKey(), deepseekCfg.baseUrl());
                providerRegistry.register(provider);
            } catch (Exception e) {
                log.warn("Failed to register DeepSeek provider: {}", e.getMessage());
            }
        }

        // 通义千问 (Qwen / DashScope)
        var qwenCfg = providers.get("qwen");
        if (qwenCfg != null && qwenCfg.apiKey() != null) {
            try {
                var provider = new com.openclaw.desktop.llm.provider.QwenProvider(
                    qwenCfg.apiKey(), qwenCfg.baseUrl());
                providerRegistry.register(provider);
                log.info("Qwen provider registered");
            } catch (Exception e) {
                log.warn("Failed to register Qwen provider: {}", e.getMessage());
            }
        }

        // Ollama (本地模型，信创友好)
        var ollamaCfg = providers.get("ollama");
        if (ollamaCfg != null && ollamaCfg.baseUrl() != null) {
            try {
                var provider = new com.openclaw.desktop.llm.provider.OllamaProvider(ollamaCfg.baseUrl());
                providerRegistry.register(provider);
            } catch (Exception e) {
                log.warn("Failed to register Ollama provider: {}", e.getMessage());
            }
        } else {
            // 默认尝试连接本地 Ollama
            try {
                var provider = new com.openclaw.desktop.llm.provider.OllamaProvider();
                providerRegistry.register(provider);
                log.info("Ollama provider registered (default localhost:11434)");
            } catch (Exception e) {
                log.warn("Ollama not available: {}", e.getMessage());
            }
        }

        // 设置默认 Provider
        if (llmConfig.defaultProvider() != null) {
            providerRegistry.setDefault(llmConfig.defaultProvider());
        }
    }

    public void stop() {
        if (!running) return;
        running = false;
        log.info("=== ClawDesktop Shutdown ===");
        try {
            if (pluginManager != null) pluginManager.shutdown();
            if (mcpManager != null) mcpManager.disconnectAll();
            if (cronScheduler != null) cronScheduler.stop();
            if (gatewayServer != null) {
                gatewayServer.stop().block();
            }
        } catch (Exception e) {
            log.error("Error during shutdown", e);
        }
        log.info("=== ClawDesktop Stopped ===");
    }

    public ClawConfig config() { return config; }
    public GatewayServer gatewayServer() { return gatewayServer; }
    public LlmProviderRegistry providerRegistry() { return providerRegistry; }
    public ToolRegistry toolRegistry() { return toolRegistry; }
    public SessionManager sessionManager() { return sessionManager; }
    public CronScheduler cronScheduler() { return cronScheduler; }
    public SkillManager skillManager() { return skillManager; }
    public SkillRegistry skillRegistry() { return skillRegistry; }
    public EventBus eventBus() { return eventBus; }
    public PluginManager pluginManager() { return pluginManager; }
    public McpClientManager mcpManager() { return mcpManager; }
    public boolean isRunning() { return running; }
}
