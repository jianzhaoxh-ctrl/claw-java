package com.openclaw.desktop.ui;

import com.openclaw.desktop.agent.Agent;
import com.openclaw.desktop.agent.AgentConfig;
import com.openclaw.desktop.config.ClawConfig;
import com.openclaw.desktop.llm.LlmProviderRegistry;
import com.openclaw.desktop.tool.ToolRegistry;
import com.openclaw.desktop.ui.chat.ChatView;
import com.openclaw.desktop.ui.settings.SettingsView;
import com.openclaw.desktop.ui.theme.ThemeManager;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JavaFX 主应用 — ClawDesktop 桌面入口。
 * 启动后显示聊天界面 + 设置面板。
 */
public class ClawDesktopUI extends Application {

    private static final Logger log = LoggerFactory.getLogger(ClawDesktopUI.class);

    private static LlmProviderRegistry sharedProviderRegistry;
    private static ToolRegistry sharedToolRegistry;
    private static ClawConfig sharedConfig;
    private static Agent sharedAgent;

    private ThemeManager themeManager;
    private ChatView chatView;
    private SettingsView settingsView;

    public static void setDependencies(LlmProviderRegistry providers, ToolRegistry tools, ClawConfig config, Agent agent) {
        sharedProviderRegistry = providers;
        sharedToolRegistry = tools;
        sharedConfig = config;
        sharedAgent = agent;
    }

    @Override
    public void start(Stage primaryStage) {
        log.info("Starting ClawDesktop UI...");

        themeManager = new ThemeManager();

        // 构建 ChatView
        chatView = new ChatView();
        var chatRoot = chatView.build();
        if (sharedProviderRegistry != null && sharedToolRegistry != null) {
            chatView.setup(sharedProviderRegistry, sharedToolRegistry);
        }

        // 构建 SettingsView
        settingsView = new SettingsView(sharedConfig != null ? sharedConfig : ClawConfig.defaults(), themeManager);

        // TabPane
        var tabPane = new TabPane();
        var chatTab = new Tab("聊天", chatRoot);
        chatTab.setClosable(false);
        var settingsTab = new Tab("设置", settingsView.getNode());
        settingsTab.setClosable(false);
        tabPane.getTabs().addAll(chatTab, settingsTab);

        // Menu bar
        var menuBar = new MenuBar();
        var fileMenu = new Menu("文件");
        var exitItem = new MenuItem("退出");
        exitItem.setOnAction(e -> Platform.exit());
        fileMenu.getItems().add(exitItem);

        var viewMenu = new Menu("视图");
        var themeMenu = new Menu("主题");
        var lightItem = new MenuItem("浅色");
        lightItem.setOnAction(e -> {
            themeManager.setTheme(ThemeManager.Theme.LIGHT);
            themeManager.applyToScene(primaryStage.getScene());
        });
        var darkItem = new MenuItem("深色");
        darkItem.setOnAction(e -> {
            themeManager.setTheme(ThemeManager.Theme.DARK);
            themeManager.applyToScene(primaryStage.getScene());
        });
        themeMenu.getItems().addAll(darkItem, lightItem);
        viewMenu.getItems().add(themeMenu);

        var helpMenu = new Menu("帮助");
        var aboutItem = new MenuItem("关于");
        aboutItem.setOnAction(e -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("关于 ClawDesktop");
            alert.setHeaderText("ClawDesktop v0.1.0");
            alert.setContentText("Java 21 + JavaFX 21\n基于 OpenClaw 架构的 Java 桌面 AI 助手");
            alert.showAndWait();
        });
        helpMenu.getItems().add(aboutItem);

        menuBar.getMenus().addAll(fileMenu, viewMenu, helpMenu);

        // Layout
        var root = new VBox(menuBar, tabPane);
        VBox.setVgrow(tabPane, Priority.ALWAYS);

        var scene = new Scene(root, 900, 650);
        themeManager.applyToScene(scene);

        primaryStage.setTitle("ClawDesktop - AI 助手");
        primaryStage.setScene(scene);
        primaryStage.setOnCloseRequest(e -> {
            log.info("UI closing...");
            Platform.exit();
            System.exit(0);
        });

        primaryStage.show();
        log.info("ClawDesktop UI started");
    }
}
