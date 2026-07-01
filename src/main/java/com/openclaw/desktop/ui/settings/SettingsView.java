package com.openclaw.desktop.ui.settings;

import com.openclaw.desktop.config.ClawConfig;
import com.openclaw.desktop.ui.theme.ThemeManager;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 设置面板 — 配置 API Key、模型选择、通道开关等。
 */
public class SettingsView {

    private static final Logger log = LoggerFactory.getLogger(SettingsView.class);

    private final ClawConfig config;
    private final ThemeManager themeManager;
    private VBox root;

    public SettingsView(ClawConfig config, ThemeManager themeManager) {
        this.config = config;
        this.themeManager = themeManager;
        build();
    }

    public javafx.scene.Node getNode() {
        return root;
    }

    private void build() {
        root = new VBox(16);
        root.setPadding(new Insets(20));
        root.setStyle("-fx-background-color: " + ThemeManager.backgroundColor(themeManager.current()));

        // ---- LLM 配置 ----
        var llmSection = createSection("LLM 配置");

        var providerLabel = new Label("默认 Provider:");
        var providerField = new TextField(config.llm().defaultProvider());
        providerField.setStyle("-fx-text-fill: " + ThemeManager.textColor(themeManager.current()));

        var apiKeyLabel = new Label("API Key:");
        var apiKeyField = new PasswordField();
        var openaiCfg = config.llm().providers().get("openai");
        if (openaiCfg != null) {
            apiKeyField.setText(openaiCfg.apiKey() != null ? openaiCfg.apiKey() : "");
        }

        var baseUrlLabel = new Label("Base URL (可选):");
        var baseUrlField = new TextField();
        if (openaiCfg != null && openaiCfg.baseUrl() != null) {
            baseUrlField.setText(openaiCfg.baseUrl());
        }

        var modelLabel = new Label("默认模型:");
        var modelField = new TextField(config.agent().modelId());

        llmSection.getChildren().addAll(providerLabel, providerField,
            apiKeyLabel, apiKeyField,
            baseUrlLabel, baseUrlField,
            modelLabel, modelField);

        // ---- Gateway 配置 ----
        var gatewaySection = createSection("Gateway 配置");
        var portLabel = new Label("端口:");
        var portField = new TextField(String.valueOf(config.gateway().port()));
        var bindLabel = new Label("绑定地址:");
        var bindField = new TextField(config.gateway().bindAddress());

        gatewaySection.getChildren().addAll(portLabel, portField, bindLabel, bindField);

        // ---- 记忆配置 ----
        var memorySection = createSection("记忆配置");
        var dbPathLabel = new Label("数据库路径:");
        var dbPathField = new TextField(config.memory().dbPath());
        var embeddingCheck = new CheckBox("启用 Embedding");
        embeddingCheck.setSelected(config.memory().embeddingEnabled());

        memorySection.getChildren().addAll(dbPathLabel, dbPathField, embeddingCheck);

        // ---- 主题选择 ----
        var themeSection = createSection("主题");
        var themeCombo = new ComboBox<>(FXCollections.observableArrayList("深色 (Dark)", "浅色 (Light)"));
        themeCombo.getSelectionModel().selectFirst();
        themeCombo.setOnAction(e -> {
            var selected = themeCombo.getSelectionModel().getSelectedIndex();
            themeManager.setTheme(selected == 0 ? ThemeManager.Theme.DARK : ThemeManager.Theme.LIGHT);
            root.setStyle("-fx-background-color: " + ThemeManager.backgroundColor(themeManager.current()));
        });
        themeSection.getChildren().add(themeCombo);

        // ---- 保存按钮 ----
        var saveButton = new Button("💾 保存配置");
        saveButton.setStyle("-fx-background-color: #89b4fa; -fx-text-fill: #1e1e2e; -fx-font-weight: bold; -fx-padding: 8 20;");
        saveButton.setOnAction(e -> {
            // TODO: 实际保存逻辑
            log.info("Save settings requested");
            var alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("设置");
            alert.setHeaderText(null);
            alert.setContentText("配置已保存（需要重启生效）");
            alert.showAndWait();
        });

        root.getChildren().addAll(llmSection, gatewaySection, memorySection, themeSection, saveButton);
    }

    private VBox createSection(String title) {
        var section = new VBox(8);
        section.setPadding(new Insets(12));
        section.setStyle("-fx-background-color: " + (themeManager.current() == ThemeManager.Theme.DARK ? "#313244;" : "#f0f0f0;")
            + " -fx-background-radius: 8;");

        var titleLabel = new Label(title);
        titleLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14; -fx-text-fill: "
            + ThemeManager.textColor(themeManager.current()));
        section.getChildren().add(titleLabel);

        return section;
    }
}
