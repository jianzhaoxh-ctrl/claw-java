package com.openclaw.desktop.ui.chat;

import com.openclaw.desktop.agent.Agent;
import com.openclaw.desktop.agent.AgentConfig;
import com.openclaw.desktop.llm.LlmProviderRegistry;
import com.openclaw.desktop.tool.ToolRegistry;
import com.openclaw.desktop.session.Session;
import com.openclaw.desktop.session.SessionKey;
import com.openclaw.desktop.session.SessionManager;
import com.openclaw.desktop.ui.md.MarkdownRenderer;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Files;
import java.util.Base64;
import java.util.UUID;

/**
 * 聊天视图 — AtlantaFX 主题版，支持复制和附件。
 */
public class ChatView {

    private static final Logger log = LoggerFactory.getLogger(ChatView.class);

    private final MarkdownRenderer mdRenderer = new MarkdownRenderer();
    private final SessionManager sessionManager = new SessionManager(new com.openclaw.desktop.session.SessionStore(), true);

    private LlmProviderRegistry providerRegistry;
    private ToolRegistry toolRegistry;
    private Agent agent;
    private Session currentSession;
    private Runnable onSettingsOpen;
    private volatile boolean isRequesting = false;

    // 待发送的附件
    private java.util.List<AttachedFile> pendingAttachments = new java.util.ArrayList<>();

    // UI 组件
    private VBox root;
    private VBox messagesContainer;
    private ScrollPane scrollPane;
    private TextArea inputArea;
    private Button sendButton;
    private Button attachButton;
    private Label statusLabel;
    private ComboBox<String> modelSelector;
    private HBox attachmentBar;
    private ListView<com.openclaw.desktop.session.SessionManager.SessionInfo> sessionListView;
    private final ObservableList<com.openclaw.desktop.session.SessionManager.SessionInfo> sessions = FXCollections.observableArrayList();
    private java.util.Map<String, Session> activeSessions = new java.util.concurrent.ConcurrentHashMap<>();

    private static final String USER_BUBBLE =
        "-fx-background-color: -color-accent-muted; -fx-background-radius: 12 12 4 12; " +
        "-fx-padding: 10 14 10 14; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.15), 4, 0, 0, 1);";
    private static final String ASSISTANT_BUBBLE =
        "-fx-background-color: -color-bg-subtle; -fx-background-radius: 12 12 12 4; " +
        "-fx-padding: 10 14 10 14; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.10), 4, 0, 0, 1);";

    /** 附件记录 */
    private record AttachedFile(String name, String mimeType, byte[] data) {
        boolean isImage() {
            return mimeType != null && mimeType.startsWith("image/");
        }
        String toBase64() {
            return Base64.getEncoder().encodeToString(data);
        }
    }

    public VBox getRoot() { return root; }

    public VBox build() {
        root = new VBox();

        // ---- 顶栏 ----
        var topBar = new HBox(10);
        topBar.setPadding(new Insets(8, 14, 8, 14));
        topBar.setStyle("-fx-background-color: -color-bg-default; -fx-border-color: -color-border-muted; -fx-border-width: 0 0 1 0;");
        topBar.setAlignment(Pos.CENTER_LEFT);

        var titleLabel = new Label("⚡ ClawDesktop");
        titleLabel.setStyle("-fx-font-size: 15; -fx-font-weight: bold;");

        var sep1 = new Separator(javafx.geometry.Orientation.VERTICAL);
        sep1.setPrefHeight(24);

        modelSelector = new ComboBox<>();
        modelSelector.setPrefWidth(200);

        var sep2 = new Separator(javafx.geometry.Orientation.VERTICAL);
        sep2.setPrefHeight(24);

        statusLabel = new Label("初始化中...");
        statusLabel.setStyle("-fx-font-size: 11; -fx-text-fill: -color-fg-muted;");
        HBox.setHgrow(statusLabel, Priority.ALWAYS);

        var settingsButton = new Button("⚙");
        settingsButton.getStyleClass().addAll("button", "icon-button");
        settingsButton.setPrefSize(32, 32);
        settingsButton.setOnAction(e -> { if (onSettingsOpen != null) onSettingsOpen.run(); });

        topBar.getChildren().addAll(titleLabel, sep1, modelSelector, sep2, statusLabel, settingsButton);

        // ---- 侧边栏 ----
        var sidebar = new VBox(6);
        sidebar.setPadding(new Insets(8));
        sidebar.setStyle("-fx-background-color: -color-bg-subtle; -fx-border-color: -color-border-muted; -fx-border-width: 0 1 0 0;");
        sidebar.setPrefWidth(180);

        var newSessionBtn = new Button("✚  新建对话");
        newSessionBtn.setMaxWidth(Double.MAX_VALUE);
        newSessionBtn.getStyleClass().addAll("button", "accent");
        newSessionBtn.setOnAction(e -> createNewSession());

        var deleteItem = new MenuItem("删除对话");
        deleteItem.setOnAction(e -> {
            var selected = sessionListView.getSelectionModel().getSelectedItem();
            if (selected != null) {
                sessionManager.delete(selected.key());
                sessions.remove(selected);
                activeSessions.remove(selected.key().toString());
            }
        });

        sessionListView = new ListView<>(sessions);
        sessionListView.setStyle("-fx-background-color: transparent;");
        sessionListView.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(com.openclaw.desktop.session.SessionManager.SessionInfo item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null); setGraphic(null);
                } else {
                    setText(item.title());
                    setStyle("-fx-padding: 6 8 6 8; -fx-border-radius: 6; -fx-background-radius: 6;");
                }
            }
        });
        sessionListView.setOnMouseClicked(e -> {
            var selected = sessionListView.getSelectionModel().getSelectedItem();
            if (selected != null) switchToSession(selected);
        });
        sessionListView.setContextMenu(new ContextMenu(deleteItem));
        VBox.setVgrow(sessionListView, Priority.ALWAYS);
        sidebar.getChildren().addAll(newSessionBtn, sessionListView);

        // ---- 消息区域 ----
        messagesContainer = new VBox(6);
        messagesContainer.setPadding(new Insets(10));

        scrollPane = new ScrollPane(messagesContainer);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background: -color-bg-default; -fx-background-color: -color-bg-default;");
        scrollPane.setVvalue(1.0);
        messagesContainer.heightProperty().addListener((obs, o, n) -> scrollPane.setVvalue(1.0));

        addWelcomeMessage();

        // ---- 附件预览栏 ----
        attachmentBar = new HBox(6);
        attachmentBar.setPadding(new Insets(4, 10, 0, 10));
        attachmentBar.setStyle("-fx-background-color: -color-bg-default;");
        attachmentBar.setVisible(false);
        attachmentBar.setManaged(false);

        // ---- 输入区域 ----
        var inputBox = new HBox(8);
        inputBox.setPadding(new Insets(6, 10, 10, 10));
        inputBox.setStyle("-fx-background-color: -color-bg-default;");

        attachButton = new Button("📎");
        attachButton.getStyleClass().addAll("button", "icon-button");
        attachButton.setPrefSize(44, 44);
        attachButton.setTooltip(new Tooltip("添加附件（图片/文件）"));
        attachButton.setOnAction(e -> openFileChooser());

        inputArea = new TextArea();
        inputArea.setPromptText("输入消息... (Enter 发送, Shift+Enter 换行, 📎 添加附件)");
        inputArea.setWrapText(true);
        inputArea.setPrefRowCount(2);
        inputArea.setMaxHeight(72);
        HBox.setHgrow(inputArea, Priority.ALWAYS);

        inputArea.addEventHandler(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.ENTER && !e.isShiftDown()) {
                e.consume();
                sendMessage();
            }
        });

        // 拖放支持
        inputArea.setOnDragOver(e -> {
            if (e.getGestureSource() != inputArea && e.getDragboard().hasFiles()) {
                e.acceptTransferModes(TransferMode.COPY);
            }
            e.consume();
        });
        inputArea.setOnDragDropped(e -> {
            var db = e.getDragboard();
            if (db.hasFiles()) {
                for (var file : db.getFiles()) {
                    addAttachment(file);
                }
            }
            e.setDropCompleted(true);
            e.consume();
        });

        sendButton = new Button("➤");
        sendButton.getStyleClass().addAll("button", "accent");
        sendButton.setPrefSize(44, 44);
        sendButton.setStyle("-fx-font-size: 14; -fx-font-weight: bold;");
        sendButton.setOnAction(e -> sendMessage());

        inputBox.getChildren().addAll(attachButton, inputArea, sendButton);

        // ---- 主布局 ----
        var contentArea = new VBox(attachmentBar, scrollPane, inputBox);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        var mainLayout = new HBox(sidebar, contentArea);
        HBox.setHgrow(contentArea, Priority.ALWAYS);

        root.getChildren().addAll(topBar, mainLayout);
        VBox.setVgrow(mainLayout, Priority.ALWAYS);

        return root;
    }

    public void setup(LlmProviderRegistry providerRegistry, ToolRegistry toolRegistry) {
        this.providerRegistry = providerRegistry;
        this.toolRegistry = toolRegistry;

        var models = new java.util.ArrayList<String>();
        for (var p : providerRegistry.all()) {
            models.add(p.id() + " / " + p.name());
        }
        modelSelector.setItems(FXCollections.observableArrayList(models));

        var defaultProvider = providerRegistry.getDefault();
        if (defaultProvider != null) {
            var defaultItem = defaultProvider.id() + " / " + defaultProvider.name();
            modelSelector.getSelectionModel().select(defaultItem);
        } else if (!models.isEmpty()) {
            modelSelector.getSelectionModel().selectFirst();
        }

        modelSelector.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                var providerId = newVal.split(" /")[0];
                switchProvider(providerId);
            }
        });

        createNewSession();
    }

    private void switchProvider(String providerId) {
        var provider = providerRegistry.all().stream()
            .filter(p -> p.id().equals(providerId))
            .findFirst().orElse(null);
        if (provider == null || currentSession == null) return;

        var modelId = inferModelId(providerId);
        var cfg = new AgentConfig(
            "default", "ClawDesktop", modelId,
            "You are ClawDesktop, a helpful personal AI assistant.",
            com.openclaw.desktop.agent.ReasoningLevel.OFF,
            4096, 0.7, java.util.List.of(), java.time.Instant.now()
        );
        agent = new Agent(cfg, provider, toolRegistry, currentSession);
        statusLabel.setText("✅ " + provider.name() + " · " + modelId);
        log.info("Switched to provider={}, model={}", providerId, modelId);
    }

    private String inferModelId(String providerId) {
        return switch (providerId) {
            case "openai"    -> "gpt-4o";
            case "anthropic" -> "claude-3-5-sonnet-20241022";
            case "deepseek"  -> "deepseek-chat";
            case "qwen"      -> "qwen-max";
            case "google"    -> "gemini-1.5-pro";
            case "groq"      -> "llama-3.3-70b-versatile";
            case "mistral"   -> "mistral-large-latest";
            case "ollama"    -> "qwen2.5:7b";
            default           -> "gpt-4o";
        };
    }

    public void setOnSettingsOpen(Runnable callback) { this.onSettingsOpen = callback; }
    public void updateStatusLabel(String text) { if (statusLabel != null) statusLabel.setText(text); }

    // ==================== 附件处理 ====================

    private void openFileChooser() {
        var chooser = new FileChooser();
        chooser.setTitle("选择附件");
        chooser.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("图片", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.webp", "*.bmp"),
            new FileChooser.ExtensionFilter("文本文件", "*.txt", "*.md", "*.json", "*.csv", "*.xml", "*.yaml", "*.yml"),
            new FileChooser.ExtensionFilter("代码", "*.java", "*.py", "*.js", "*.ts", "*.go", "*.rs", "*.cpp", "*.c", "*.h"),
            new FileChooser.ExtensionFilter("文档", "*.pdf", "*.docx", "*.xlsx", "*.pptx"),
            new FileChooser.ExtensionFilter("所有文件", "*.*")
        );
        var files = chooser.showOpenMultipleDialog(root.getScene().getWindow());
        if (files != null) {
            for (var file : files) {
                addAttachment(file);
            }
        }
    }

    private void addAttachment(File file) {
        try {
            var path = file.toPath();
            var mimeType = Files.probeContentType(path);
            if (mimeType == null) {
                mimeType = "application/octet-stream";
            }
            var data = Files.readAllBytes(path);

            // 限制 10MB
            if (data.length > 10 * 1024 * 1024) {
                statusLabel.setText("⚠ 附件过大（>10MB）: " + file.getName());
                return;
            }

            pendingAttachments.add(new AttachedFile(file.getName(), mimeType, data));
            refreshAttachmentBar();
            log.info("Attached: {} ({} bytes, {})", file.getName(), data.length, mimeType);
        } catch (Exception e) {
            log.error("Failed to attach: {}", file.getName(), e);
            statusLabel.setText("⚠ 无法读取: " + file.getName());
        }
    }

    private void refreshAttachmentBar() {
        attachmentBar.getChildren().clear();
        if (pendingAttachments.isEmpty()) {
            attachmentBar.setVisible(false);
            attachmentBar.setManaged(false);
            return;
        }
        attachmentBar.setVisible(true);
        attachmentBar.setManaged(true);
        for (int i = 0; i < pendingAttachments.size(); i++) {
            var att = pendingAttachments.get(i);
            var chip = new HBox(4);
            chip.setStyle("-fx-background-color: -color-accent-subtle; -fx-background-radius: 8; -fx-padding: 3 8 3 6;");
            chip.setAlignment(Pos.CENTER);

            var icon = new Label(att.isImage() ? "🖼" : "📄");
            var name = new Label(att.name());
            name.setStyle("-fx-font-size: 11;");
            var removeBtn = new Button("✕");
            removeBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: -color-fg-muted; -fx-font-size: 10; -fx-padding: 0 0 0 4;");
            removeBtn.setOnAction(e -> {
                pendingAttachments.remove(att);
                refreshAttachmentBar();
            });

            chip.getChildren().addAll(icon, name, removeBtn);
            attachmentBar.getChildren().add(chip);
        }
    }

    // ==================== 会话管理 ====================

    private void createNewSession() {
        var key = SessionKey.main("session-" + UUID.randomUUID().toString().substring(0, 8));
        currentSession = sessionManager.getOrCreate(key).block();
        activeSessions.put(key.toString(), currentSession);
        var provider = providerRegistry.getDefault();
        if (provider == null) {
            statusLabel.setText("⚠ 未配置 Provider — 请在设置中配置 API Key");
            return;
        }
        var modelId = inferModelId(provider.id());
        var cfg = new AgentConfig(
            "default", "ClawDesktop", modelId,
            "You are ClawDesktop, a helpful personal AI assistant.",
            com.openclaw.desktop.agent.ReasoningLevel.OFF,
            4096, 0.7, java.util.List.of(), java.time.Instant.now()
        );
        agent = new Agent(cfg, provider, toolRegistry, currentSession);
        sessions.add(0, new com.openclaw.desktop.session.SessionManager.SessionInfo(
            key, "新对话", java.time.Instant.now(), java.time.Instant.now(), 0
        ));
        sessionListView.getSelectionModel().selectFirst();
        messagesContainer.getChildren().clear();
        addWelcomeMessage();
        statusLabel.setText("✅ " + provider.name() + " · " + modelId);
    }

    private void switchToSession(com.openclaw.desktop.session.SessionManager.SessionInfo info) {
        var session = activeSessions.get(info.key().toString());
        if (session == null) {
            session = sessionManager.getOrCreate(info.key()).block();
            if (session != null) activeSessions.put(info.key().toString(), session);
        }
        if (session == null) return;
        currentSession = session;
        var provider = providerRegistry.getDefault();
        if (provider != null) {
            var modelId = inferModelId(provider.id());
            var cfg = new AgentConfig(
                "default", "ClawDesktop", modelId,
                "You are ClawDesktop, a helpful personal AI assistant.",
                com.openclaw.desktop.agent.ReasoningLevel.OFF,
                4096, 0.7, java.util.List.of(), java.time.Instant.now()
            );
            agent = new Agent(cfg, provider, toolRegistry, currentSession);
            statusLabel.setText("✅ " + provider.name() + " · " + modelId);
        }
        messagesContainer.getChildren().clear();
        var entries = session.transcript().entries();
        if (entries.isEmpty()) {
            addWelcomeMessage();
        } else {
            for (var entry : entries) {
                if (entry.content() != null && !entry.content().isEmpty()) {
                    addMessage(entry.role(), entry.content());
                }
            }
        }
        log.info("Switched to session: {} ({} messages)", info.key(), entries.size());
    }

    private void addWelcomeMessage() {
        var label = new Label("欢迎使用 ClawDesktop 🚀\n\n我是你的个人 AI 助手，支持文件读写、网络搜索等。\n可拖放文件到输入框，或点 📎 添加附件。");
        label.setStyle("-fx-font-size: 14; -fx-text-fill: -color-fg-muted;");
        label.setWrapText(true);
        label.setMaxWidth(450);
        label.setAlignment(Pos.CENTER);
        var box = new HBox(label);
        box.setAlignment(Pos.CENTER);
        HBox.setHgrow(box, Priority.ALWAYS);
        messagesContainer.getChildren().add(box);
    }

    // ==================== 消息发送 ====================

    private void sendMessage() {
        if (isRequesting) return;

        var message = inputArea.getText().trim();
        if (message.isEmpty() && pendingAttachments.isEmpty()) return;
        if (agent == null) return;

        isRequesting = true;
        inputArea.setDisable(true);
        sendButton.setDisable(true);
        sendButton.setText("⏳");

        // 构建显示文本（含附件信息）
        var displayMsg = new StringBuilder(message);
        if (!pendingAttachments.isEmpty()) {
            for (var att : pendingAttachments) {
                displayMsg.append("\n\n[📎 ").append(att.name());
                if (att.isImage()) {
                    displayMsg.append(" (图片)");
                }
                displayMsg.append("]");
            }
        }
        addMessage("user", displayMsg.toString());
        inputArea.clear();

        // 构建发送给 LLM 的文本（含文件内容摘要）
        var llmMsg = new StringBuilder(message);
        for (var att : pendingAttachments) {
            if (att.isImage()) {
                // 图片 — 用 base64 标记（实际 vision 调用在 Provider 层）
                llmMsg.append("\n\n[用户发送了一张图片: ").append(att.name()).append("]");
            } else {
                // 文本类文件 — 直接内嵌内容
                var content = new String(att.data(), java.nio.charset.StandardCharsets.UTF_8);
                if (content.length() > 5000) {
                    content = content.substring(0, 5000) + "\n...(truncated, total " + att.data().length + " bytes)";
                }
                llmMsg.append("\n\n--- 文件: ").append(att.name()).append(" ---\n");
                llmMsg.append(content);
                llmMsg.append("\n--- 文件结束 ---");
            }
        }

        // 清空附件
        pendingAttachments.clear();
        refreshAttachmentBar();

        statusLabel.setText("🤖 正在思考...");

        var assistantBox = addMessage("assistant", "");
        var startTime = System.currentTimeMillis();
        var accumulated = new StringBuilder();

        agent.chatStream(llmMsg.toString())
            .doOnSubscribe(_ignored -> Platform.runLater(() -> {
                var sel = sessionListView.getSelectionModel().getSelectedItem();
                if (sel != null && "新对话".equals(sel.title())) {
                    var title = message.length() > 30 ? message.substring(0, 30) + "..." : message;
                    var newInfo = new com.openclaw.desktop.session.SessionManager.SessionInfo(
                        sel.key(), title, sel.createdAt(), java.time.Instant.now(), sel.messageCount() + 1
                    );
                    int idx = sessions.indexOf(sel);
                    if (idx >= 0) sessions.set(idx, newInfo);
                }
            }))
            .subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic())
            .subscribe(
                chunk -> Platform.runLater(() -> {
                    accumulated.append(chunk);
                    updateMessage(assistantBox, accumulated.toString());
                }),
                error -> Platform.runLater(() -> {
                    log.error("Chat error", error);
                    var msg = error.getMessage() != null ? error.getMessage() : "unknown";
                    if (accumulated.length() == 0) {
                        updateMessage(assistantBox, "⚠ 请求失败：" + msg);
                    } else {
                        updateMessage(assistantBox, accumulated + "\n\n⚠ " + msg);
                    }
                    statusLabel.setText("❌ " + (msg.length() > 50 ? msg.substring(0, 50) + "..." : msg));
                    resetSendState();
                }),
                () -> Platform.runLater(() -> {
                    var elapsed = (System.currentTimeMillis() - startTime) / 1000.0;
                    var p = providerRegistry.getDefault();
                    statusLabel.setText("✅ " + (p != null ? p.name() : "") + " · " + String.format("%.1fs", elapsed));
                    resetSendState();
                })
            );
    }

    private void resetSendState() {
        isRequesting = false;
        inputArea.setDisable(false);
        inputArea.requestFocus();
        sendButton.setDisable(false);
        sendButton.setText("➤");
    }

    // ==================== 消息渲染（含复制按钮） ====================

    private VBox addMessage(String role, String content) {
        var bubble = new VBox(4);
        bubble.setStyle(role.equals("user") ? USER_BUBBLE : ASSISTANT_BUBBLE);
        bubble.setMaxWidth(580);

        if (!content.isEmpty()) {
            bubble.getChildren().add(mdRenderer.render(content, "-color-fg-default"));
        }

        // 添加复制按钮栏
        var toolBar = new HBox(4);
        toolBar.setAlignment(Pos.CENTER_RIGHT);

        var copyBtn = new Button("📋 复制");
        copyBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: -color-fg-muted; -fx-font-size: 10; -fx-padding: 2 6 2 6; -fx-cursor: hand;");
        copyBtn.setOnAction(e -> {
            var clipboard = javafx.scene.input.Clipboard.getSystemClipboard();
            var contentHolder = new javafx.scene.input.ClipboardContent();
            // 从 bubble 的第一个子节点提取纯文本
            var text = extractTextFromBubble(bubble);
            contentHolder.putString(text);
            clipboard.setContent(contentHolder);
            copyBtn.setText("✅ 已复制");
            var timeline = new javafx.animation.Timeline(new javafx.animation.KeyFrame(
                javafx.util.Duration.seconds(1.5),
                ev -> copyBtn.setText("📋 复制")
            ));
            timeline.play();
        });

        toolBar.getChildren().add(copyBtn);
        bubble.getChildren().add(toolBar);

        var row = new HBox();
        row.setPadding(new Insets(3, 8, 3, 8));
        if (role.equals("user")) {
            var spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            row.getChildren().addAll(spacer, bubble);
        } else {
            row.getChildren().add(bubble);
            var spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            row.getChildren().add(spacer);
        }

        messagesContainer.getChildren().add(row);
        return bubble;
    }

    private void updateMessage(VBox bubble, String content) {
        // 保留最后的 toolBar（复制按钮），只更新内容
        var toolBar = bubble.getChildren().size() > 1
            ? bubble.getChildren().get(bubble.getChildren().size() - 1) : null;
        bubble.getChildren().clear();
        bubble.getChildren().add(mdRenderer.render(content, "-color-fg-default"));
        if (toolBar != null) {
            bubble.getChildren().add(toolBar);
        }
    }

    private String extractTextFromBubble(VBox bubble) {
        var sb = new StringBuilder();
        // 只提取内容区域（第一个子节点），跳过最后的 toolBar
        for (var node : bubble.getChildren()) {
            // 跳过 HBox（复制按钮栏）
            if (node instanceof HBox) continue;
            extractText(node, sb);
        }
        return sb.toString().trim();
    }

    private void extractText(javafx.scene.Node node, StringBuilder sb) {
        if (node instanceof javafx.scene.text.Text t) {
            sb.append(t.getText());
        } else if (node instanceof Label l) {
            sb.append(l.getText()).append("\n");
        } else if (node instanceof javafx.scene.Parent p) {
            for (var child : p.getChildrenUnmodifiable()) {
                extractText(child, sb);
            }
        }
    }
}
