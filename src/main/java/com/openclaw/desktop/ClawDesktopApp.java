package com.openclaw.desktop;

import com.openclaw.desktop.config.ClawConfig;
import com.openclaw.desktop.config.ConfigLoader;
import com.openclaw.desktop.config.ConfigWatcher;
import com.openclaw.desktop.gateway.server.GatewayServer;
import com.openclaw.desktop.agent.Agent;
import com.openclaw.desktop.agent.AgentConfig;
import com.openclaw.desktop.llm.LlmProviderRegistry;
import com.openclaw.desktop.llm.provider.OpenAiProvider;
import com.openclaw.desktop.tool.ToolRegistry;
import com.openclaw.desktop.tool.core.ReadFileTool;
import com.openclaw.desktop.tool.core.WriteFileTool;
import com.openclaw.desktop.session.SessionManager;
import com.openclaw.desktop.channel.ChannelRegistry;
import com.openclaw.desktop.memory.MemoryDatabase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ClawDesktop 应用入口 — 主应用类。
 * 负责初始化和组装所有核心组件。
 */
public class ClawDesktopApp {

    private static final Logger log = LoggerFactory.getLogger(ClawDesktopApp.class);

    private ClawConfig config;
    private GatewayServer gatewayServer;
    private ConfigWatcher configWatcher;
    private LlmProviderRegistry llmRegistry;
    private ToolRegistry toolRegistry;
    private SessionManager sessionManager;
    private ChannelRegistry channelRegistry;
    private MemoryDatabase memoryDatabase;

    public void start() throws Exception {
        log.info("ClawDesktop starting...");

        // 1. 加载配置
        config = ConfigLoader.load();
        log.info("Config loaded: gateway port={}", config.gateway().port());

        // 2. 初始化记忆数据库
        memoryDatabase = new MemoryDatabase(config.memory().dbPath());
        memoryDatabase.initialize();

        // 3. 注册 LLM Providers
        llmRegistry = new LlmProviderRegistry();
        var openaiConfig = config.llm().providers().get("openai");
        if (openaiConfig != null && openaiConfig.apiKey() != null) {
            llmRegistry.register(new OpenAiProvider(openaiConfig.apiKey(), openaiConfig.baseUrl()));
            log.info("OpenAI provider registered");
        }

        // 4. 注册工具
        toolRegistry = new ToolRegistry();
        toolRegistry.register(new ReadFileTool());
        toolRegistry.register(new WriteFileTool());
        log.info("Tools registered: {}", toolRegistry.size());

        // 5. 初始化会话管理器
        sessionManager = new SessionManager();

        // 6. 初始化通道注册表
        channelRegistry = new ChannelRegistry();

        // 7. 启动网关服务器
        gatewayServer = new GatewayServer(config);
        gatewayServer.start().block();
        log.info("GatewayServer started on port {}", gatewayServer.port());

        // 8. 配置热重载
        configWatcher = new ConfigWatcher(
            java.nio.file.Paths.get("application.conf"),
            newConfig -> {
                log.info("Config reloaded, applying...");
                this.config = newConfig;
                // TODO: hot-rechain components
            }
        );
        // configWatcher.start(); // 启动文件监听

        log.info("ClawDesktop started successfully!");
        log.info("HTTP API: http://{}:{}", config.gateway().bindAddress(), config.gateway().port());
    }

    public void stop() {
        log.info("ClawDesktop stopping...");
        if (configWatcher != null) configWatcher.stop();
        if (gatewayServer != null) gatewayServer.stop().block();
        log.info("ClawDesktop stopped");
    }

    public ClawConfig config() { return config; }
    public GatewayServer gateway() { return gatewayServer; }
    public LlmProviderRegistry llmRegistry() { return llmRegistry; }
    public ToolRegistry toolRegistry() { return toolRegistry; }
    public SessionManager sessionManager() { return sessionManager; }
    public ChannelRegistry channelRegistry() { return channelRegistry; }
    public MemoryDatabase memoryDatabase() { return memoryDatabase; }

    public static void main(String[] args) throws Exception {
        var app = new ClawDesktopApp();

        // 优雅关闭
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            app.stop();
        }));

        app.start();

        // 保持运行
        Thread.currentThread().join();
    }
}
