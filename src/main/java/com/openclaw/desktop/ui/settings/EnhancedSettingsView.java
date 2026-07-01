package com.openclaw.desktop.ui.settings;

import com.openclaw.desktop.config.ClawConfig;
import com.openclaw.desktop.ui.theme.ThemeManager;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigRenderOptions;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * 增强版设置面板 — 管理 8 个 LLM Provider、模型选择、通道、配置导入/导出、实时验证。
 * 保存时真正写入 ~/.clawdesktop/application.conf，并回调通知主应用热重载。
 */
public class EnhancedSettingsView {

    private static final Logger log = LoggerFactory.getLogger(EnhancedSettingsView.class);
    private static final Path CONFIG_DIR  = Paths.get(System.getProperty("user.home"), ".clawdesktop");
    private static final Path CONFIG_FILE = CONFIG_DIR.resolve("application.conf");

    private static final List<String> PROVIDER_IDS = List.of(
        "openai", "anthropic", "deepseek", "qwen", "google", "groq", "mistral", "ollama");

    private static final Map<String, String> PROVIDER_LABELS = Map.of(
        "openai",    "OpenAI (GPT-4o / GPT-4o-mini)",
        "anthropic", "Anthropic Claude (Claude 3.5 / Claude 3)",
        "deepseek",  "DeepSeek (DeepSeek Chat / Coder)",
        "qwen",      "通义千问 Qwen (qwen-max / qwen-turbo)",
        "google",    "Google Gemini (Gemini Pro / Ultra)",
        "groq",      "Groq (快速推理 — llama3 / mixtral)",
        "mistral",   "Mistral (Mistral Large / Medium / Small)",
        "ollama",    "Ollama (本地运行 — 无需 API Key)"
    );

    private static final Map<String, List<String>> PROVIDER_MODELS = Map.of(
        "openai",    List.of("gpt-4o", "gpt-4o-mini", "gpt-4-turbo", "gpt-3.5-turbo", "o1-mini", "o1-preview"),
        "anthropic", List.of("claude-3-5-sonnet-20241022", "claude-3-opus-20240229", "claude-3-haiku-20240307", "claude-3-sonnet-20240229"),
        "deepseek",  List.of("deepseek-chat", "deepseek-coder", "deepseek-reasoner"),
        "qwen",      List.of("qwen-max", "qwen-plus", "qwen-turbo", "qwen-long", "qwen2.5-72b-instruct", "qwen2.5-7b-instruct"),
        "google",    List.of("gemini-1.5-pro", "gemini-1.5-flash", "gemini-2.0-flash-exp"),
        "groq",      List.of("llama3-8b-8192", "llama3-70b-8192", "mixtral-8x7b-32768", "gemma2-9b-it"),
        "mistral",   List.of("mistral-large-latest", "mistral-medium-latest", "mistral-small-latest", "codestral-latest"),
        "ollama",    List.of("qwen2.5:7b", "qwen2.5:14b", "llama3.1:8b", "llama3.1:70b", "mistral:7b", "codestral:7b", "deepseek-coder-v2:16b")
    );

    private final ClawConfig config;
    private final ThemeManager themeManager;
    private final Map<String, ProviderForm> providerForms = new LinkedHashMap<>();
    private final List<ChannelForm> channelForms = new ArrayList<>();
    private Runnable onSaveCallback;

    private VBox root;
    private TabPane tabPane;
    private Label validationLabel;
    private ComboBox<String> defaultProviderCombo;

    public EnhancedSettingsView(ClawConfig config, ThemeManager themeManager) {
        this.config = config;
        this.themeManager = themeManager;
        build();
    }

    public Node getNode() { return root; }

    public void setOnSave(Runnable callback) { this.onSaveCallback = callback; }

    private void build() {
        root = new VBox(12);
        root.setPadding(new Insets(16));
        root.setStyle("-fx-background-color: " + ThemeManager.backgroundColor(themeManager.current()));

        tabPane = new TabPane();
        tabPane.getTabs().addAll(
            buildProvidersTab(),
            buildChannelsTab(),
            buildGeneralTab(),
            buildImportExportTab()
        );

        validationLabel = new Label("✓ 配置有效");
        validationLabel.setStyle("-fx-text-fill: #a6e3a1; -fx-font-size: 11;");

        var saveBtn = new Button("💾 保存配置并生效");
        saveBtn.setStyle("-fx-background-color: #89b4fa; -fx-text-fill: #1e1e2e; -fx-font-weight: bold; -fx-padding: 8 24; -fx-font-size: 14;");
        saveBtn.setOnAction(e -> saveConfig());

        var bottomBar = new HBox(12, validationLabel, spacer(), saveBtn);
        bottomBar.setAlignment(Pos.CENTER_LEFT);
        root.getChildren().addAll(tabPane, bottomBar);
    }

    // ========== Providers Tab ==========

    private Tab buildProvidersTab() {
        var tab = new Tab("🤖 LLM 提供商 & API Key");
        tab.setClosable(false);

        var content = new VBox(10);
        content.setPadding(new Insets(12));

        // 提示文字
        var hintLabel = new Label("选择并配置你想使用的 AI 模型提供商。设置 API Key 后点击「保存」即可生效。");
        hintLabel.setWrapText(true);
        hintLabel.setStyle("-fx-text-fill: #a6adc8; -fx-font-size: 12; -fx-padding: 4 0;");
        content.getChildren().add(hintLabel);

        for (var id : PROVIDER_IDS) {
            var form = buildProviderForm(id);
            providerForms.put(id, form);
            content.getChildren().add(form.card);
        }

        // 默认 Provider + 模型 选择
        var defaultLabel = new Label("默认提供商：");
        defaultLabel.setStyle(labelStyle());
        defaultProviderCombo = buildDefaultProviderCombo();

        var defaultModelLabel = new Label("默认模型：");
        defaultModelLabel.setStyle(labelStyle());
        var defaultModelCombo = new ComboBox<String>();
        defaultModelCombo.setPromptText("选择模型...");
        updateDefaultModelCombo(defaultModelCombo, defaultProviderCombo.getValue());
        defaultProviderCombo.setOnAction(e -> updateDefaultModelCombo(defaultModelCombo, defaultProviderCombo.getValue()));

        var defaultRow = new HBox(12, defaultLabel, defaultProviderCombo, defaultModelLabel, defaultModelCombo);
        defaultRow.setAlignment(Pos.CENTER_LEFT);
        defaultRow.setPadding(new Insets(10, 0, 0, 0));
        content.getChildren().add(defaultRow);

        var scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setPrefHeight(500);
        tab.setContent(scroll);
        return tab;
    }

    private void updateDefaultModelCombo(ComboBox<String> modelCombo, String providerId) {
        var models = PROVIDER_MODELS.getOrDefault(providerId, List.of());
        modelCombo.setItems(FXCollections.observableArrayList(models));
        if (!models.isEmpty()) {
            modelCombo.setValue(models.get(0));
        }
        // 如果当前配置中有模型，使用它
        var currentModel = config.agent().modelId();
        for (var m : models) {
            if (m.equals(currentModel)) {
                modelCombo.setValue(m);
                break;
            }
        }
    }

    private ComboBox<String> buildDefaultProviderCombo() {
        var combo = new ComboBox<>(FXCollections.observableArrayList(PROVIDER_IDS));
        var current = config.llm().defaultProvider();
        if (current != null && PROVIDER_IDS.contains(current)) {
            combo.setValue(current);
        } else {
            combo.setValue("openai");
        }
        combo.setStyle("-fx-background-color: #313244; -fx-text-fill: " + ThemeManager.textColor(themeManager.current()) + "; -fx-min-width: 150;");
        return combo;
    }

    private ProviderForm buildProviderForm(String id) {
        var card = new VBox(8);
        card.setPadding(new Insets(12));
        card.setStyle(cardStyle());

        var cfg = config.llm().providers().get(id);
        boolean enabled = cfg != null && (cfg.apiKey() != null || id.equals("ollama"));

        // 标题行
        var titleLabel = new Label(PROVIDER_LABELS.getOrDefault(id, id));
        titleLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14; -fx-text-fill: " + ThemeManager.textColor(themeManager.current()));

        var enableCheck = new CheckBox("启用");
        enableCheck.setSelected(enabled);
        enableCheck.setStyle("-fx-text-fill: " + ThemeManager.textColor(themeManager.current()));

        // 状态标签（显示 Key 是否已设置）
        var statusLabel = new Label();
        if (id.equals("ollama")) {
            statusLabel.setText("✅ 本地运行，无需 Key");
            statusLabel.setStyle("-fx-text-fill: #a6e3a1; -fx-font-size: 11;");
        } else if (cfg != null && cfg.apiKey() != null && !cfg.apiKey().isEmpty()) {
            statusLabel.setText("✅ Key 已设置 (" + cfg.apiKey().substring(0, Math.min(8, cfg.apiKey().length())) + "...)");
            statusLabel.setStyle("-fx-text-fill: #a6e3a1; -fx-font-size: 11;");
        } else {
            statusLabel.setText("⚠ 未设置 API Key");
            statusLabel.setStyle("-fx-text-fill: #f38ba8; -fx-font-size: 11;");
        }

        var testBtn = new Button("🔌 测试连接");
        testBtn.setStyle("-fx-background-color: #313244; -fx-text-fill: " + ThemeManager.textColor(themeManager.current()) + ";");
        testBtn.setOnAction(e -> testConnection(id));

        var header = new HBox(12, enableCheck, titleLabel, statusLabel, spacer(), testBtn);
        header.setAlignment(Pos.CENTER_LEFT);
        card.getChildren().add(header);

        // API Key
        var apiKeyField = new PasswordField();
        apiKeyField.setPromptText(id.equals("ollama") ? "无需 API Key（本地运行）" : "粘贴你的 API Key...");
        if (cfg != null && cfg.apiKey() != null) apiKeyField.setText(cfg.apiKey());
        apiKeyField.textProperty().addListener((o, ov, nv) -> {
            validate();
            // 更新状态标签
            if (id.equals("ollama")) return;
            if (nv == null || nv.isEmpty()) {
                statusLabel.setText("⚠ 未设置 API Key");
                statusLabel.setStyle("-fx-text-fill: #f38ba8; -fx-font-size: 11;");
            } else {
                statusLabel.setText("✅ Key 已填写 (" + nv.substring(0, Math.min(8, nv.length())) + "...)");
                statusLabel.setStyle("-fx-text-fill: #a6e3a1; -fx-font-size: 11;");
            }
        });
        var apiKeyRow = labeledRow("🔑 API Key：", apiKeyField);

        // Base URL
        var baseUrlField = new TextField();
        baseUrlField.setPromptText(defaultBaseUrlFor(id));
        if (cfg != null && cfg.baseUrl() != null) baseUrlField.setText(cfg.baseUrl());
        var baseUrlRow = labeledRow("🌐 Base URL：", baseUrlField);

        // 模型选择（ComboBox + TextField 混合）
        var modelCombo = new ComboBox<>(FXCollections.observableArrayList(PROVIDER_MODELS.getOrDefault(id, List.of())));
        modelCombo.setPromptText("选择或输入模型...");
        modelCombo.setEditable(true);
        modelCombo.setStyle("-fx-background-color: #313244; -fx-text-fill: " + ThemeManager.textColor(themeManager.current()) + "; -fx-min-width: 200;");
        // 默认选第一个
        var models = PROVIDER_MODELS.getOrDefault(id, List.of());
        if (!models.isEmpty()) modelCombo.setValue(models.get(0));
        // 如果有配置的模型，使用它
        var currentModel = config.agent().modelId();
        for (var m : models) {
            if (m.equals(currentModel)) {
                modelCombo.setValue(m);
                break;
            }
        }
        var modelRow = labeledRow("🧠 模型：", modelCombo);

        // 启用时才可编辑
        var fields = List.of(apiKeyField, baseUrlField, modelCombo);
        enableCheck.selectedProperty().addListener((o, ov, nv) -> {
            for (var f : fields) f.setDisable(!nv);
            testBtn.setDisable(!nv);
            if (nv && id.equals("ollama")) apiKeyField.setDisable(true);
        });
        if (!enabled) {
            for (var f : fields) f.setDisable(true);
            testBtn.setDisable(true);
        }
        if (id.equals("ollama")) apiKeyField.setDisable(true);

        card.getChildren().addAll(apiKeyRow, baseUrlRow, modelRow);
        return new ProviderForm(id, card, enableCheck, apiKeyField, baseUrlField, modelCombo, testBtn);
    }

    // ========== 测试连接 ==========

    private void testConnection(String providerId) {
        final var form = providerForms.get(providerId);
        if (form == null) return;

        final var apiKey = form.apiKeyField.getText();
        final var baseUrl0 = form.baseUrlField.getText();
        final var baseUrl = (baseUrl0 == null || baseUrl0.isBlank()) ? defaultBaseUrlFor(providerId) : baseUrl0;

        form.testBtn.setDisable(true);
        form.testBtn.setText("⏳ 测试中...");

        var task = new javafx.concurrent.Task<Boolean>() {
            @Override protected Boolean call() throws Exception {
                var url = switch (providerId) {
                    case "openai"    -> baseUrl + "/models";
                    case "anthropic" -> baseUrl + "/v1/messages";
                    case "deepseek"  -> baseUrl + "/models";
                    case "qwen"      -> baseUrl + "/models";
                    case "google"    -> baseUrl + "/v1beta/models";
                    case "groq"      -> baseUrl + "/models";
                    case "mistral"   -> baseUrl + "/v1/models";
                    case "ollama"    -> "http://localhost:11434/api/tags";
                    default           -> baseUrl;
                };

                var client = java.net.http.HttpClient.newBuilder()
                    .connectTimeout(java.time.Duration.ofSeconds(8)).build();
                var reqBuilder = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(url))
                    .timeout(java.time.Duration.ofSeconds(10));
                if (!"ollama".equals(providerId) && apiKey != null && !apiKey.isBlank()) {
                    reqBuilder.header("Authorization", "Bearer " + apiKey);
                }
                reqBuilder.GET();

                var resp = client.send(reqBuilder.build(),
                    java.net.http.HttpResponse.BodyHandlers.ofString());
                return resp.statusCode() < 500;
            }
            @Override protected void succeeded() {
                javafx.application.Platform.runLater(() -> {
                    form.testBtn.setDisable(false);
                    form.testBtn.setText("🔌 测试连接");
                    var alert = new Alert(getValue() ? Alert.AlertType.INFORMATION : Alert.AlertType.WARNING);
                    alert.setTitle("连接测试");
                    alert.setHeaderText(getValue() ? "✅ 连接成功" : "⚠ 连接失败");
                    alert.setContentText(getValue()
                        ? "Provider 网络可达。点击「保存配置并生效」即可开始使用。"
                        : "无法连接到 " + baseUrl + "，请检查 Base URL 和网络。");
                    alert.showAndWait();
                });
            }
            @Override protected void failed() {
                javafx.application.Platform.runLater(() -> {
                    form.testBtn.setDisable(false);
                    form.testBtn.setText("🔌 测试连接");
                    var alert = new Alert(Alert.AlertType.ERROR);
                    alert.setTitle("连接测试");
                    alert.setHeaderText("❌ 连接失败");
                    alert.setContentText("错误信息：" + getException().getMessage());
                    alert.showAndWait();
                });
            }
        };
        new Thread(task).start();
    }

    // ========== Channels Tab ==========

    private Tab buildChannelsTab() {
        var tab = new Tab("📡 消息通道");
        tab.setClosable(false);
        var content = new VBox(12);
        content.setPadding(new Insets(12));

        var channelIds = List.of("telegram", "discord", "slack", "webhook");
        for (var id : channelIds) {
            var form = buildChannelForm(id);
            channelForms.add(form);
            content.getChildren().add(form.card);
        }
        tab.setContent(content);
        return tab;
    }

    private ChannelForm buildChannelForm(String id) {
        var card = new VBox(8);
        card.setPadding(new Insets(10));
        card.setStyle(cardStyle());

        var enableCheck = new CheckBox(channelLabel(id));
        var existing = config.channels().stream()
            .filter(c -> c.id().equals(id)).findFirst();
        enableCheck.setSelected(existing.isPresent() && existing.get().enabled());
        enableCheck.setStyle("-fx-text-fill: " + ThemeManager.textColor(themeManager.current()));

        var tokenField = new PasswordField();
        tokenField.setPromptText(switch (id) {
            case "webhook" -> "Webhook URL";
            default        -> "Bot Token";
        });

        var row = new HBox(10, new Label("Token："), tokenField);
        HBox.setHgrow(tokenField, Priority.ALWAYS);
        card.getChildren().addAll(enableCheck, row);

        return new ChannelForm(id, card, enableCheck, tokenField);
    }

    private String channelLabel(String id) {
        return switch (id) {
            case "telegram" -> "Telegram Bot";
            case "discord"  -> "Discord Bot";
            case "slack"    -> "Slack App";
            case "webhook"  -> "Webhook";
            default          -> id;
        };
    }

    // ========== General Tab ==========

    private Tab buildGeneralTab() {
        var tab = new Tab("⚙ 通用设置");
        tab.setClosable(false);
        var content = new VBox(12);
        content.setPadding(new Insets(12));

        // Agent
        var agentCard = new VBox(8);
        agentCard.setPadding(new Insets(10));
        agentCard.setStyle(cardStyle());
        agentCard.getChildren().add(headerLabel("Agent"));

        var tempField = new TextField(String.valueOf(config.agent().temperature()));
        var maxTokensField = new TextField(String.valueOf(config.agent().maxTokens()));
        var sysPromptArea = new TextArea(config.agent().systemPrompt());
        sysPromptArea.setPrefRowCount(3);
        sysPromptArea.setWrapText(true);

        agentCard.getChildren().addAll(
            labeledRow("温度 (0-2)：", tempField),
            labeledRow("最大 Token：", maxTokensField),
            new Label("系统提示词：") {{ setStyle(labelStyle()); }},
            sysPromptArea
        );

        // 主题
        var themeCard = new VBox(8);
        themeCard.setPadding(new Insets(10));
        themeCard.setStyle(cardStyle());
        themeCard.getChildren().add(headerLabel("主题"));

        var themeCombo = new ComboBox<>(FXCollections.observableArrayList("深色 🌙", "浅色 ☀️"));
        themeCombo.setValue(themeManager.current() == ThemeManager.Theme.DARK ? "深色 🌙" : "浅色 ☀️");
        themeCombo.setStyle("-fx-background-color: #313244; -fx-text-fill: " + ThemeManager.textColor(themeManager.current()));
        themeCombo.setOnAction(e -> {
            themeManager.setTheme(themeCombo.getValue().startsWith("深色")
                ? ThemeManager.Theme.DARK : ThemeManager.Theme.LIGHT);
            if (root.getScene() != null) themeManager.applyToScene(root.getScene());
        });
        themeCard.getChildren().add(themeCombo);

        content.getChildren().addAll(agentCard, themeCard);
        tab.setContent(content);
        return tab;
    }

    // ========== Import / Export Tab ==========

    private Tab buildImportExportTab() {
        var tab = new Tab("📁 导入 / 导出");
        tab.setClosable(false);
        var content = new VBox(16);
        content.setPadding(new Insets(20));
        content.setAlignment(Pos.TOP_CENTER);

        var exportBtn = new Button("📤 导出配置到文件");
        exportBtn.setStyle(btnStyle());
        exportBtn.setOnAction(e -> exportConfig());

        var importBtn = new Button("📥 从文件导入配置");
        importBtn.setStyle(btnStyle());
        importBtn.setOnAction(e -> importConfig());

        var hint = new Label("配置以 HOCON 格式导出，包含 Provider / 通道设置。\n导入会覆盖当前配置，保存后自动生效。");
        hint.setWrapText(true);
        hint.setStyle("-fx-text-fill: #a6adc8; -fx-font-size: 11;");

        content.getChildren().addAll(exportBtn, importBtn, hint);
        tab.setContent(content);
        return tab;
    }

    // ========== 保存配置 ==========

    private void saveConfig() {
        var errors = validateInternal();
        if (!errors.isEmpty()) {
            validationLabel.setText("❌ " + String.join("；", errors));
            validationLabel.setStyle("-fx-text-fill: #f38ba8; -fx-font-size: 11;");
            return;
        }

        try {
            if (!Files.exists(CONFIG_DIR)) {
                Files.createDirectories(CONFIG_DIR);
            }

            var confStr = buildHoconString();
            Files.writeString(CONFIG_FILE, confStr);
            log.info("Config saved to: {}", CONFIG_FILE);

            validationLabel.setText("✅ 配置已保存 → 即将热重载");
            validationLabel.setStyle("-fx-text-fill: #a6e3a1; -fx-font-size: 11;");

            // 先回调，让主应用重建 Provider + Agent
            if (onSaveCallback != null) {
                onSaveCallback.run();
            }

            var providerCount = providerForms.values().stream()
                .filter(f -> f.enableCheck.isSelected()).count();

            var alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("✅ 保存成功");
            alert.setHeaderText("配置已保存并生效");
            alert.setContentText(
                "已启用 " + providerCount + " 个 Provider。\n" +
                "默认模型：" + getSelectedModel() + "\n\n" +
                "你现在可以直接在聊天界面使用 AI 对话了！"
            );
            alert.showAndWait();

        } catch (Exception ex) {
            log.error("Save config failed", ex);
            validationLabel.setText("❌ 保存失败：" + ex.getMessage());
            validationLabel.setStyle("-fx-text-fill: #f38ba8; -fx-font-size: 11;");
        }
    }

    private String getSelectedModel() {
        var dp = defaultProviderCombo.getValue();
        var form = providerForms.get(dp);
        if (form != null && form.modelCombo.getValue() != null) {
            return form.modelCombo.getValue();
        }
        return defaultModelFor(dp);
    }

    private String buildHoconString() {
        var sb = new StringBuilder();
        sb.append("# ClawDesktop 配置文件（由设置界面自动生成）\n\n");

        // gateway
        sb.append("gateway {\n");
        sb.append("  port = 7180\n");
        sb.append("  ws-port = 7181\n");
        sb.append("  bind-address = \"127.0.0.1\"\n");
        sb.append("  cors-enabled = true\n");
        sb.append("}\n\n");

        // agent — 使用选择的默认 Provider 和模型
        String defaultModel = getSelectedModel();
        sb.append("agent {\n");
        sb.append("  id = \"default\"\n");
        sb.append("  name = \"ClawDesktop\"\n");
        sb.append("  model-id = \"").append(defaultModel).append("\"\n");
        sb.append("  system-prompt = \"You are ClawDesktop, a helpful personal AI assistant.\"\n");
        sb.append("  reasoning-level = \"off\"\n");
        sb.append("  max-tokens = 4096\n");
        sb.append("  temperature = 0.7\n");
        sb.append("}\n\n");

        // llm — 默认 provider 为用户选择的
        String defaultProvider = defaultProviderCombo.getValue();
        sb.append("llm {\n");
        sb.append("  default-provider = \"").append(defaultProvider).append("\"\n\n");
        sb.append("  providers {\n");
        for (var entry : providerForms.entrySet()) {
            var id = entry.getKey();
            var form = entry.getValue();
            if (!form.enableCheck.isSelected()) continue;

            sb.append("    ").append(id).append(" {\n");
            var ak = form.apiKeyField.getText();
            if (ak != null && !ak.isBlank()) {
                sb.append("      api-key = \"").append(ak.replace("\\", "\\\\").replace("\"", "\\\"")).append("\"\n");
            }
            var bu = form.baseUrlField.getText();
            if (bu != null && !bu.isBlank()) {
                sb.append("      base-url = \"").append(bu).append("\"\n");
            }
            sb.append("    }\n");
        }
        sb.append("  }\n");
        sb.append("}\n\n");

        // memory
        sb.append("memory {\n");
        sb.append("  db-path = \"").append(config.memory().dbPath()).append("\"\n");
        sb.append("  embedding-enabled = false\n");
        sb.append("}\n");

        return sb.toString();
    }

    private void exportConfig() {
        var chooser = new FileChooser();
        chooser.setTitle("导出配置");
        chooser.setInitialFileName("claw-config.conf");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("HOCON Config", "*.conf"));
        var file = chooser.showSaveDialog(root.getScene().getWindow());
        if (file == null) return;
        try {
            Files.writeString(file.toPath(), buildHoconString());
            new Alert(Alert.AlertType.INFORMATION, "配置已导出到：\n" + file.getAbsolutePath()).showAndWait();
        } catch (Exception ex) {
            log.error("Export failed", ex);
            new Alert(Alert.AlertType.ERROR, "导出失败：" + ex.getMessage()).showAndWait();
        }
    }

    private void importConfig() {
        var chooser = new FileChooser();
        chooser.setTitle("导入配置");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Config Files", "*.conf", "*.json"));
        var file = chooser.showOpenDialog(root.getScene().getWindow());
        if (file == null) return;
        try {
            var content = Files.readString(file.toPath());
            Files.writeString(CONFIG_FILE, content);
            new Alert(Alert.AlertType.INFORMATION, "配置已导入，保存后自动生效。").showAndWait();
        } catch (Exception ex) {
            log.error("Import failed", ex);
            new Alert(Alert.AlertType.ERROR, "导入失败：" + ex.getMessage()).showAndWait();
        }
    }

    // ========== 验证 ==========

    private List<String> validateInternal() {
        var errors = new ArrayList<String>();
        for (var entry : providerForms.entrySet()) {
            var form = entry.getValue();
            if (!form.enableCheck.isSelected()) continue;
            if (!entry.getKey().equals("ollama")) {
                var ak = form.apiKeyField.getText();
                if (ak == null || ak.isBlank()) {
                    errors.add(PROVIDER_LABELS.get(entry.getKey()) + "：API Key 为空");
                }
            }
        }
        return errors;
    }

    private void validate() {
        var errors = validateInternal();
        if (errors.isEmpty()) {
            validationLabel.setText("✓ 配置有效");
            validationLabel.setStyle("-fx-text-fill: #a6e3a1; -fx-font-size: 11;");
        } else {
            validationLabel.setText("⚠ " + String.join("；", errors));
            validationLabel.setStyle("-fx-text-fill: #f38ba8; -fx-font-size: 11;");
        }
    }

    // ========== 默认值 ==========

    private String defaultModelFor(String providerId) {
        var models = PROVIDER_MODELS.getOrDefault(providerId, List.of());
        return models.isEmpty() ? "" : models.get(0);
    }

    private String defaultBaseUrlFor(String providerId) {
        return switch (providerId) {
            case "openai"    -> "https://api.openai.com/v1";
            case "anthropic" -> "https://api.anthropic.com";
            case "deepseek"  -> "https://api.deepseek.com/v1";
            case "qwen"      -> "https://dashscope.aliyuncs.com/compatible-mode/v1";
            case "google"    -> "https://generativelanguage.googleapis.com/v1beta";
            case "groq"      -> "https://api.groq.com/openai/v1";
            case "mistral"   -> "https://api.mistral.ai/v1";
            case "ollama"    -> "http://localhost:11434";
            default           -> "";
        };
    }

    // ========== 小工具 ==========

    private String labelStyle() {
        return "-fx-text-fill: " + ThemeManager.textColor(themeManager.current()) + "; -fx-font-size: 12;";
    }

    private String cardStyle() {
        return "-fx-background-color: " +
            (themeManager.current() == ThemeManager.Theme.DARK ? "#313244" : "#f0f0f0") +
            "; -fx-background-radius: 8;";
    }

    private String btnStyle() {
        return "-fx-background-color: #313244; -fx-text-fill: " +
            ThemeManager.textColor(themeManager.current()) + "; -fx-padding: 10 20; -fx-font-size: 13;";
    }

    private Label headerLabel(String text) {
        var lbl = new Label(text);
        lbl.setStyle("-fx-font-weight: bold; -fx-font-size: 13; -fx-text-fill: " +
            ThemeManager.textColor(themeManager.current()));
        return lbl;
    }

    private HBox labeledRow(String labelText, Control field) {
        var lbl = new Label(labelText);
        lbl.setStyle(labelStyle());
        lbl.setMinWidth(100);
        lbl.setAlignment(Pos.CENTER_LEFT);
        if (field instanceof PasswordField || field instanceof TextField || field instanceof ComboBox) {
            HBox.setHgrow(field, Priority.ALWAYS);
        }
        var row = new HBox(10, lbl, field);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private Region spacer() {
        var r = new Region();
        HBox.setHgrow(r, Priority.ALWAYS);
        return r;
    }

    // ========== 表单记录 ==========

    private record ProviderForm(
        String id,
        VBox card,
        CheckBox enableCheck,
        PasswordField apiKeyField,
        TextField baseUrlField,
        ComboBox<String> modelCombo,
        Button testBtn
    ) {}

    private record ChannelForm(
        String id,
        VBox card,
        CheckBox enableCheck,
        PasswordField tokenField
    ) {}
}
