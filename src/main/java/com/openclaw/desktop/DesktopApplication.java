package com.openclaw.desktop;

import com.openclaw.desktop.agent.Agent;
import com.openclaw.desktop.agent.AgentConfig;
import com.openclaw.desktop.agent.ReasoningLevel;
import com.openclaw.desktop.config.ClawConfig;
import com.openclaw.desktop.config.ConfigLoader;
import com.openclaw.desktop.config.ConfigReloadManager;
import com.openclaw.desktop.desktop.*;
import com.openclaw.desktop.llm.LlmProvider;
import com.openclaw.desktop.llm.LlmProviderRegistry;
import com.openclaw.desktop.llm.provider.*;
import com.openclaw.desktop.session.Session;
import com.openclaw.desktop.session.SessionKey;
import com.openclaw.desktop.session.SessionManager;
import com.openclaw.desktop.tool.ToolRegistry;
import com.openclaw.desktop.tool.core.*;
import com.openclaw.desktop.ui.chat.ChatView;
import com.openclaw.desktop.ui.settings.EnhancedSettingsView;
import com.openclaw.desktop.ui.theme.ThemeManager;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

/**
 * JavaFX 主入口 — ClawDesktop 桌面应用。
 *
 * 启动流程：
 *   1. 加载配置（application.conf + 环境变量覆盖）
 *   2. 初始化工具注册表
 *   3. 初始化 LLM Provider 注册表
 *   4. 构建 ChatView + 设置对话框
 *   5. 初始化桌面集成（托盘/通知/热键）
 */
public class DesktopApplication extends Application {

    private static final Logger log = LoggerFactory.getLogger(DesktopApplication.class);

    // ---- 核心状态 ----
    private ClawConfig config;
    private LlmProviderRegistry providerRegistry;
    private ToolRegistry toolRegistry;
    private SessionManager sessionManager;
    private Agent agent;
    private ThemeManager themeManager = new ThemeManager();

    // ---- UI ----
    private Stage primaryStage;
    private ChatView chatView;
    private EnhancedSettingsView settingsView;
    private Dialog<ButtonType> settingsDialog;

    // ---- Gateway ----
    private com.openclaw.desktop.gateway.server.GatewayServer gatewayServer;

    // ---- 桌面集成 ----
    private SystemTrayManager trayManager;
    private NotificationManager notificationManager;
    private GlobalHotkeyManager hotkeyManager;
    private WindowManager windowManager;
    private ClipboardManager clipboardManager;
    private AutoStartManager autoStartManager;
    private ConfigReloadManager configReloadManager;

    // ======================== 启动 ========================

    @Override
    public void start(Stage stage) {
        this.primaryStage = stage;
        log.info("=== ClawDesktop Boot ===");

        // 1. 加载配置
        config = loadConfig();

        // 2. 工具注册表
        toolRegistry = new ToolRegistry();
        registerBuiltinTools(toolRegistry);

        // 3. LLM Provider 注册表
        providerRegistry = new LlmProviderRegistry();
        sessionManager = new SessionManager(new com.openclaw.desktop.session.SessionStore(), true);
        registerProviders(providerRegistry, config);

        // 4. 默认 Agent
        agent = createAgent(providerRegistry, toolRegistry, sessionManager, config);
        if (agent == null) {
            log.warn("⚠ No LLM provider configured. Use Settings to set API Key.");
        }

        // 5. 构建 UI
        buildUI();

        // 6. 桌面集成
        initDesktopIntegration(stage, config);

        // 7. 启动 Gateway HTTP 服务器
        if (agent != null) {
            try {
                gatewayServer = new com.openclaw.desktop.gateway.server.GatewayServer(config, agent, toolRegistry);
                gatewayServer.start().block(java.time.Duration.ofSeconds(10));
                log.info("Gateway started: http://localhost:{}", config.gateway().port());
            } catch (Exception e) {
                log.warn("Gateway start failed (non-fatal): {}", e.getMessage());
            }
        }

        // 8. 显示主窗口
        primaryStage.show();
        log.info("=== ClawDesktop Ready ===");
    }

    private ClawConfig loadConfig() {
        try {
            return ConfigLoader.load();
        } catch (Exception e) {
            log.warn("Failed to load config, using defaults: {}", e.getMessage());
            return ClawConfig.defaults();
        }
    }

    private void registerBuiltinTools(ToolRegistry registry) {
        // 文件系统工具
        registry.register(new ReadFileTool());
        registry.register(new WriteFileTool());
        registry.register(new EditFileTool());
        registry.register(new DeleteFileTool());
        registry.register(new ListFilesTool());
        // 系统工具
        registry.register(new ShellExecTool());
        registry.register(new ProcessTool());
        // 网络工具
        registry.register(new WebSearchTool());
        registry.register(new WebFetchTool());
        registry.register(new BrowserAutomationTool());
        // 代码执行
        registry.register(new CodeSandboxTool());
        // AI 媒体工具
        registry.register(new ImageGenerationTool());
        registry.register(new VideoGenerationTool());
        registry.register(new MediaUnderstandingTool());
        // 文档工具
        registry.register(new DocumentExtractTool());
        registry.register(new DiffComparisonTool());
        // 信息工具
        registry.register(new CalendarTool());
        registry.register(new EmailTool());
        log.info("Registered {} builtin tools", registry.size());
    }

    private void registerProviders(LlmProviderRegistry registry, ClawConfig cfg) {
        var providers = cfg.llm().providers();

        registerProvider(registry, "openai", providers);
        registerProvider(registry, "anthropic", providers);
        registerProvider(registry, "deepseek", providers);
        registerProvider(registry, "qwen", providers);
        registerProvider(registry, "google", providers);
        registerProvider(registry, "groq", providers);
        registerProvider(registry, "mistral", providers);
        registerOllama(registry, providers);

        var defaultId = cfg.llm().defaultProvider();
        if (defaultId != null) {
            registry.setDefault(defaultId);
        }
    }

    private void registerProvider(LlmProviderRegistry registry, String id,
            java.util.Map<String, ClawConfig.LlmConfig.ProviderConfig> providers) {
        var pc = providers.get(id);
        if (pc == null || pc.apiKey() == null || pc.apiKey().isBlank()) return;

        LlmProvider provider = switch (id) {
            case "openai"    -> new OpenAiProvider(pc.apiKey(), pc.baseUrl());
            case "anthropic" -> new AnthropicProvider(pc.apiKey(), pc.baseUrl());
            case "deepseek"  -> new DeepSeekProvider(pc.apiKey(), pc.baseUrl());
            case "qwen"      -> new QwenProvider(pc.apiKey(), pc.baseUrl());
            case "google"    -> new GoogleProvider(pc.apiKey());
            case "groq"      -> new GroqProvider(pc.apiKey(), pc.baseUrl());
            case "mistral"   -> new MistralProvider(pc.apiKey(), pc.baseUrl());
            default -> null;
        };
        if (provider != null) {
            registry.register(provider);
            log.info("Registered LLM provider: {} ({})", id, provider.name());
        }
    }

    private void registerOllama(LlmProviderRegistry registry,
            java.util.Map<String, ClawConfig.LlmConfig.ProviderConfig> providers) {
        var pc = providers.get("ollama");
        if (pc == null) return;
        try {
            var provider = new OllamaProvider(pc.baseUrl());
            registry.register(provider);
            log.info("Registered Ollama provider: {}", provider.name());
        } catch (Exception e) {
            log.warn("Ollama registration failed: {}", e.getMessage());
        }
    }

    private Agent createAgent(LlmProviderRegistry providers, ToolRegistry tools,
            SessionManager sessions, ClawConfig cfg) {
        var defaultProvider = providers.getDefault();
        if (defaultProvider == null) return null;

        var agentCfg = new AgentConfig(
            cfg.agent().id(),
            cfg.agent().name(),
            cfg.agent().modelId(),
            cfg.agent().systemPrompt(),
            parseReasoningLevel(cfg.agent().reasoningLevel()),
            cfg.agent().maxTokens(),
            cfg.agent().temperature(),
            List.of(),
            Instant.now()
        );
        var session = sessions.getOrCreate(SessionKey.main(cfg.agent().id())).block();
        var ag = new Agent(agentCfg, defaultProvider, tools, session);

        // 审批管理器 — 危险操作弹窗确认
        var approvalMgr = new com.openclaw.desktop.approval.ApprovalManager(
            com.openclaw.desktop.approval.ApprovalPolicy.CONFIRM_DANGEROUS);
        approvalMgr.setCallback(req ->
            Mono.<com.openclaw.desktop.approval.ApprovalResult>fromCallable(() -> {
                var latch = new java.util.concurrent.CountDownLatch(1);
                var result = new java.util.concurrent.atomic.AtomicReference<com.openclaw.desktop.approval.ApprovalResult>();
                Platform.runLater(() -> {
                    var alert = new Alert(Alert.AlertType.CONFIRMATION);
                    alert.setTitle("工具审批");
                    alert.setHeaderText("🔧 " + req.toolName());
                    alert.setContentText(req.operation() + "\n\n是否允许执行？");
                    alert.getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
                    alert.showAndWait().ifPresentOrElse(
                        (javafx.scene.control.ButtonType btn) -> result.set(btn == ButtonType.OK
                            ? com.openclaw.desktop.approval.ApprovalResult.approved(req.id())
                            : com.openclaw.desktop.approval.ApprovalResult.denied(req.id(), "用户拒绝")),
                        () -> result.set(com.openclaw.desktop.approval.ApprovalResult.denied(req.id(), "用户关闭"))
                    );
                    latch.countDown();
                });
                latch.await(60, java.util.concurrent.TimeUnit.SECONDS);
                return result.get() != null ? result.get()
                    : com.openclaw.desktop.approval.ApprovalResult.denied(req.id(), "超时");
            }).subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic())
        );
        ag.setApprovalManager(approvalMgr);

        return ag;
    }

    private ReasoningLevel parseReasoningLevel(String level) {
        try {
            return ReasoningLevel.valueOf(level.toUpperCase());
        } catch (Exception e) {
            return ReasoningLevel.OFF;
        }
    }

    // ======================== UI 构建 ========================

    private void buildUI() {
        // 应用 AtlantaFX 主题（在创建任何 UI 之前）
        Application.setUserAgentStylesheet(new atlantafx.base.theme.PrimerDark().getUserAgentStylesheet());

        // ChatView
        chatView = new ChatView();
        var root = chatView.build();
        chatView.setup(providerRegistry, toolRegistry);
        chatView.setOnSettingsOpen(this::showSettingsDialog);

        // MenuBar
        var menuBar = new MenuBar();
        menuBar.getMenus().addAll(buildFileMenu(), buildViewMenu(), buildHelpMenu());

        // 主布局
        var fullRoot = new VBox(menuBar, root);
        VBox.setVgrow(root, Priority.ALWAYS);

        var scene = new Scene(fullRoot, 1000, 700);
        // AtlantaFX 主题已通过 setUserAgentStylesheet 全局应用，不需要额外 applyToScene

        primaryStage.setTitle("ClawDesktop — Personal AI Assistant");
        primaryStage.setScene(scene);
        primaryStage.setMinWidth(600);
        primaryStage.setMinHeight(400);
        primaryStage.setOnCloseRequest(e -> {
            if (trayManager != null && trayManager.isTraySupported()) {
                e.consume();
                primaryStage.hide();
                trayManager.showTrayMessage("ClawDesktop", "已最小化到托盘");
            } else {
                stop();
                System.exit(0);
            }
        });

        // 构建设置对话框（延迟创建，仅在点击设置按钮时显示）
        buildSettingsDialog();

        // 更新状态
        updateStatus();
    }

    private void buildSettingsDialog() {
        settingsView = new EnhancedSettingsView(config, themeManager);
        settingsView.setOnSave(() -> {
            // 保存成功后，重新加载配置和 Provider
            reloadAfterSave();
        });

        settingsDialog = new Dialog<>();
        settingsDialog.setTitle("ClawDesktop 设置");
        settingsDialog.setHeaderText("⚙  设置");
        settingsDialog.getDialogPane().setContent(settingsView.getNode());
        settingsDialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        var closeBtn = (Button) settingsDialog.getDialogPane().lookupButton(ButtonType.CLOSE);
        closeBtn.setText("关闭");
        settingsDialog.initOwner(primaryStage);
        settingsDialog.initModality(Modality.APPLICATION_MODAL);
        settingsDialog.setResizable(true);
        settingsDialog.getDialogPane().setPrefWidth(700);
        settingsDialog.getDialogPane().setPrefHeight(580);
    }

    private void showSettingsDialog() {
        // 重新加载最新配置（文件可能已被外部修改）
        try {
            config = ConfigLoader.load();
            settingsView = new EnhancedSettingsView(config, themeManager);
            settingsView.setOnSave(this::reloadAfterSave);
            settingsDialog.getDialogPane().setContent(settingsView.getNode());
        } catch (Exception e) {
            log.warn("Reload config failed: {}", e.getMessage());
        }
        settingsDialog.show();
    }

    private void reloadAfterSave() {
        log.info("Reloading config after save...");
        try {
            // 重新加载配置
            config = ConfigLoader.load();

            // 重建 Provider 注册表
            var oldRegistry = providerRegistry;
            providerRegistry = new LlmProviderRegistry();
            registerProviders(providerRegistry, config);

            // 重建 Agent
            agent = createAgent(providerRegistry, toolRegistry, sessionManager, config);

            // 通知 ChatView 更新
            chatView.setup(providerRegistry, toolRegistry);
            updateStatus();

            log.info("Config reloaded: {} providers registered",
                providerRegistry.all().size());

        } catch (Exception e) {
            log.error("Reload after save failed", e);
            Platform.runLater(() -> {
                new Alert(Alert.AlertType.ERROR, "重载配置失败：" + e.getMessage()).show();
            });
        }
    }

    private void updateStatus() {
        var provider = providerRegistry.getDefault();
        if (provider == null) {
            chatView.updateStatusLabel("⚠ 未配置 LLM Provider — 请先在设置中配置");
        } else {
            chatView.updateStatusLabel("✅ " + provider.name() + " 已连接");
        }
    }

    // ======================== 菜单 ========================

    private Menu buildFileMenu() {
        var exitItem = new MenuItem("退出");
        exitItem.setOnAction(e -> { stop(); System.exit(0); });
        var menu = new Menu("文件");
        menu.getItems().add(exitItem);
        return menu;
    }

    private Menu buildViewMenu() {
        var darkItem = new MenuItem("深色主题 (Primer Dark)");
        darkItem.setOnAction(e -> {
            Application.setUserAgentStylesheet(new atlantafx.base.theme.PrimerDark().getUserAgentStylesheet());
        });
        var lightItem = new MenuItem("浅色主题 (Primer Light)");
        lightItem.setOnAction(e -> {
            Application.setUserAgentStylesheet(new atlantafx.base.theme.PrimerLight().getUserAgentStylesheet());
        });
        var cupertinoDark = new MenuItem("macOS 深色 (Cupertino Dark)");
        cupertinoDark.setOnAction(e -> {
            Application.setUserAgentStylesheet(new atlantafx.base.theme.CupertinoDark().getUserAgentStylesheet());
        });
        var cupertinoLight = new MenuItem("macOS 浅色 (Cupertino Light)");
        cupertinoLight.setOnAction(e -> {
            Application.setUserAgentStylesheet(new atlantafx.base.theme.CupertinoLight().getUserAgentStylesheet());
        });
        var dracula = new MenuItem("Dracula 主题");
        dracula.setOnAction(e -> {
            Application.setUserAgentStylesheet(new atlantafx.base.theme.Dracula().getUserAgentStylesheet());
        });
        var sep = new SeparatorMenuItem();
        var settingsItem = new MenuItem("⚙ 设置...");
        settingsItem.setOnAction(e -> showSettingsDialog());

        var menu = new Menu("视图");
        menu.getItems().addAll(darkItem, lightItem, cupertinoDark, cupertinoLight, dracula, sep, settingsItem);
        return menu;
    }

    private Menu buildHelpMenu() {
        var aboutItem = new MenuItem("关于 ClawDesktop");
        aboutItem.setOnAction(e -> {
            var provider = providerRegistry.getDefault();
            var info = new Alert(Alert.AlertType.INFORMATION);
            info.setTitle("关于 ClawDesktop");
            info.setHeaderText("ClawDesktop v0.1.0");
            info.setContentText(
                "Java 21 + JavaFX 21\n" +
                "基于 OpenClaw 架构的桌面 AI 助手\n\n" +
                "LLM Provider: " + (provider != null ? provider.name() : "未配置") + "\n" +
                "工具数量: " + toolRegistry.size()
            );
            info.showAndWait();
        });
        var menu = new Menu("帮助");
        menu.getItems().add(aboutItem);
        return menu;
    }

    // ======================== 桌面集成 ========================

    private void initDesktopIntegration(Stage stage, ClawConfig cfg) {
        log.info("Initializing desktop integration...");

        // 窗口管理器
        windowManager = new WindowManager();
        windowManager.setPrimaryStage(stage);

        // 系统托盘
        trayManager = new SystemTrayManager();
        trayManager.setMenuActionHandler(action -> {
            switch (action) {
                case "settings"  -> Platform.runLater(this::showSettingsDialog);
                case "show"      -> Platform.runLater(() -> { stage.show(); stage.toFront(); });
                case "quit"      -> Platform.runLater(() -> { stop(); System.exit(0); });
                default          -> log.debug("Tray action: {}", action);
            }
        });
        trayManager.init(stage);

        // 通知管理器
        notificationManager = new NotificationManager(trayManager);
        notificationManager.setClickHandler(id -> {
            Platform.runLater(() -> { stage.show(); stage.toFront(); });
        });

        // 全局快捷键已移除

        // 剪贴板
        clipboardManager = new ClipboardManager();

        // 开机自启
        var javaPath = System.getProperty("java.class.path");
        autoStartManager = new AutoStartManager(
            ProcessHandle.current().info().command().orElse("java"),
            "ClawDesktop", null);

        log.info("Desktop ready: tray={} hotkey={} autostart={}",
            trayManager.isTraySupported(),
            false,
            autoStartManager.platform());
    }

    // ======================== 停止 ========================

    @Override
    public void stop() {
        log.info("ClawDesktop shutting down...");
        if (gatewayServer != null) {
            try { gatewayServer.stop().block(); } catch (Exception e) { log.warn("Gateway stop: {}", e.getMessage()); }
        }
        if (notificationManager != null) notificationManager.shutdown();
        if (configReloadManager != null) configReloadManager.stop();
        if (windowManager != null)      windowManager.closeAll();
        if (trayManager != null)         trayManager.dispose();
        log.info("ClawDesktop stopped");
    }

    public static void main(String[] args) {
        launch(args);
    }
}
