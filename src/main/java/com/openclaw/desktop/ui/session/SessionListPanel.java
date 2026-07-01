package com.openclaw.desktop.ui.session;

import com.openclaw.desktop.session.Session;
import com.openclaw.desktop.session.SessionKey;
import com.openclaw.desktop.session.SessionManager;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
/**
 * 会话列表面板 — 侧边栏中的会话管理 UI。
 *
 * <p>功能：
 * <ul>
 *   <li>显示所有会话（标题 + 预览 + 时间）</li>
 *   <li>点击切换会话</li>
 *   <li>搜索框过滤会话</li>
 *   <li>右键菜单：重命名 / 删除 / 导出 Markdown / 导出 JSON</li>
 *   <li>新建会话按钮</li>
 * </ul>
 */
public class SessionListPanel {

    private static final Logger log = LoggerFactory.getLogger(SessionListPanel.class);

    private final SessionManager sessionManager;
    private final SessionExporter exporter = new SessionExporter();
    private final ObservableList<SessionItem> items = FXCollections.observableArrayList();
    private final Map<String, SessionItem> byKey = new ConcurrentHashMap<>();
    private Consumer<Session> onSessionSelected;

    private VBox root;
    private ListView<SessionItem> listView;
    private TextField searchField;
    private String currentFilter = "";

    public SessionListPanel(SessionManager sessionManager) {
        this.sessionManager = sessionManager;
        build();
    }

    public VBox node() {
        return root;
    }

    public void setOnSessionSelected(Consumer<Session> handler) {
        this.onSessionSelected = handler;
    }

    private void build() {
        root = new VBox(6);
        root.setPadding(new Insets(8));

        // 新建按钮
        var newBtn = new Button("+ 新建会话");
        newBtn.setMaxWidth(Double.MAX_VALUE);
        newBtn.setStyle("-fx-background-color: #89b4fa; -fx-text-fill: #1e1e2e; -fx-font-weight: bold;");
        newBtn.setOnAction(e -> createNewSession());

        // 搜索框
        searchField = new TextField();
        searchField.setPromptText("搜索会话...");
        searchField.setStyle("-fx-background-color: #313244; -fx-text-fill: #cdd6f4; -fx-prompt-text-fill: #585b70;");
        searchField.textProperty().addListener((obs, o, n) -> {
            currentFilter = n == null ? "" : n.toLowerCase();
            applyFilter();
        });

        // 列表
        listView = new ListView<>(items);
        listView.setCellFactory(lv -> new SessionCell());
        VBox.setVgrow(listView, javafx.scene.layout.Priority.ALWAYS);
        listView.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 1) {
                var selected = listView.getSelectionModel().getSelectedItem();
                if (selected != null) selectSession(selected);
            }
        });

        root.getChildren().addAll(newBtn, searchField, listView);
    }

    /** 创建新会话。 */
    public void createNewSession() {
        var id = "session-" + System.currentTimeMillis();
        var key = SessionKey.main(id);
        var session = sessionManager.getOrCreate(key).block();
        var item = new SessionItem(key.toString(), "新会话 " + (items.size() + 1),
            Instant.now(), "开始新的对话", session);
        items.add(0, item);
        byKey.put(key.toString(), item);
        listView.getSelectionModel().select(0);
        selectSession(item);
        log.info("Created new session: {}", key);
    }

    /** 选择会话。 */
    private void selectSession(SessionItem item) {
        if (onSessionSelected != null) {
            onSessionSelected.accept(item.session);
        }
    }

    /** 应用搜索过滤。 */
    private void applyFilter() {
        if (currentFilter.isBlank()) {
            // 恢复全部
            listView.setItems(items);
            return;
        }
        var filtered = items.filtered(item ->
            item.title.toLowerCase().contains(currentFilter)
            || item.preview.toLowerCase().contains(currentFilter));
        listView.setItems(filtered);
    }

    /** 重命名会话。 */
    public void renameSession(String key, String newTitle) {
        var item = byKey.get(key);
        if (item != null) {
            var idx = items.indexOf(item);
            if (idx >= 0) {
                var renamed = new SessionItem(item.key, newTitle, item.createdAt, item.preview, item.session);
                items.set(idx, renamed);
                byKey.put(key, renamed);
                log.info("Session renamed: {} -> {}", key, newTitle);
            }
        }
    }

    /** 删除会话。 */
    public void deleteSession(String key) {
        var item = byKey.remove(key);
        if (item != null) {
            items.remove(item);
            sessionManager.delete(item.session.key()).block();
            log.info("Session deleted: {}", key);
        }
    }

    /** 更新会话预览（收到新消息时调用）。 */
    public void updatePreview(String key, String preview) {
        Platform.runLater(() -> {
            var item = byKey.get(key);
            if (item != null) {
                var idx = items.indexOf(item);
                if (idx >= 0) {
                    var updated = new SessionItem(item.key, item.title, item.createdAt, preview, item.session);
                    items.set(idx, updated);
                    byKey.put(key, updated);
                }
            }
        });
    }

    /** 导出会话。 */
    public void exportSession(String key, SessionExporter.Format format) {
        var item = byKey.get(key);
        if (item == null) return;
        var fileChooser = new FileChooser();
        fileChooser.setTitle("导出会话");
        fileChooser.setInitialFileName(item.title + (format == SessionExporter.Format.MARKDOWN ? ".md" : ".json"));
        fileChooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter(
                format == SessionExporter.Format.MARKDOWN ? "Markdown 文件" : "JSON 文件",
                format == SessionExporter.Format.MARKDOWN ? "*.md" : "*.json"));
        var file = fileChooser.showSaveDialog(root.getScene().getWindow());
        if (file != null) {
            try {
                exporter.export(item.session, item.title, format, file.toPath());
                var alert = new Alert(Alert.AlertType.INFORMATION, "已导出到: " + file.getAbsolutePath());
                alert.showAndWait();
            } catch (Exception e) {
                log.error("Export failed", e);
                new Alert(Alert.AlertType.ERROR, "导出失败: " + e.getMessage()).showAndWait();
            }
        }
    }

    /** 当前会话项列表。 */
    public List<SessionItem> items() {
        return List.copyOf(items);
    }

    /** 会话项记录。 */
    public record SessionItem(String key, String title, Instant createdAt, String preview, Session session) {}

    /** 自定义单元格。 */
    private class SessionCell extends ListCell<SessionItem> {
        @Override
        protected void updateItem(SessionItem item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setGraphic(null);
                setText(null);
                setContextMenu(null);
                return;
            }
            var box = new VBox(2);
            var titleLabel = new Label(item.title);
            titleLabel.setStyle("-fx-text-fill: #cdd6f4; -fx-font-weight: bold; -fx-font-size: 12;");
            var previewLabel = new Label(item.preview);
            previewLabel.setStyle("-fx-text-fill: #a6adc8; -fx-font-size: 10;");
            previewLabel.setWrapText(true);
            var timeLabel = new Label(formatTime(item.createdAt));
            timeLabel.setStyle("-fx-text-fill: #585b70; -fx-font-size: 9;");
            box.getChildren().addAll(titleLabel, previewLabel, timeLabel);
            setGraphic(box);
            setStyle("-fx-background-color: transparent; -fx-padding: 4 6;");
            setContextMenu(buildContextMenu(item));
        }

        private ContextMenu buildContextMenu(SessionItem item) {
            var menu = new ContextMenu();
            var renameItem = new MenuItem("重命名");
            renameItem.setOnAction(e -> {
                var input = new TextInputDialog(item.title);
                input.setTitle("重命名会话");
                input.setHeaderText(null);
                input.setContentText("新名称:");
                input.showAndWait().ifPresent(newName -> renameSession(item.key, newName));
            });
            var exportMdItem = new MenuItem("导出为 Markdown");
            exportMdItem.setOnAction(e -> exportSession(item.key, SessionExporter.Format.MARKDOWN));
            var exportJsonItem = new MenuItem("导出为 JSON");
            exportJsonItem.setOnAction(e -> exportSession(item.key, SessionExporter.Format.JSON));
            var deleteItem = new MenuItem("删除");
            deleteItem.setStyle("-fx-text-fill: #f38ba8;");
            deleteItem.setOnAction(e -> {
                var confirm = new Alert(Alert.AlertType.CONFIRMATION, "确认删除会话「" + item.title + "」？",
                    ButtonType.YES, ButtonType.NO);
                confirm.showAndWait().ifPresent(btn -> {
                    if (btn == ButtonType.YES) deleteSession(item.key);
                });
            });
            menu.getItems().addAll(renameItem, exportMdItem, exportJsonItem, new SeparatorMenuItem(), deleteItem);
            return menu;
        }

        private String formatTime(Instant time) {
            var now = Instant.now();
            var diff = now.getEpochSecond() - time.getEpochSecond();
            if (diff < 60) return "刚刚";
            if (diff < 3600) return (diff / 60) + " 分钟前";
            if (diff < 86400) return (diff / 3600) + " 小时前";
            if (diff < 86400 * 7) return (diff / 86400) + " 天前";
            return time.toString().substring(0, 10);
        }
    }
}
