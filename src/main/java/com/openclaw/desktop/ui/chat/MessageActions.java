package com.openclaw.desktop.ui.chat;

import javafx.application.Platform;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Consumer;

/**
 * 消息操作菜单 — 为每条消息提供重试/编辑/删除/复制操作。
 *
 * <p>当鼠标悬停在消息气泡上时显示操作按钮，移出时隐藏。
 * 操作通过回调通知 ChatView 处理。
 */
public class MessageActions {

    private static final Logger log = LoggerFactory.getLogger(MessageActions.class);

    public enum Action { RETRY, EDIT, DELETE, COPY }

    private final String messageId;
    private final String role;
    private final VBox messageBubble;
    private final HBox toolbar;
    private final Consumer<Action> actionHandler;

    public MessageActions(String messageId, String role, VBox messageBubble, Consumer<Action> actionHandler) {
        this.messageId = messageId;
        this.role = role;
        this.messageBubble = messageBubble;
        this.actionHandler = actionHandler;
        this.toolbar = buildToolbar();
        // 初始隐藏
        toolbar.setVisible(false);
        toolbar.setManaged(false);
    }

    /** 工具栏节点（加入消息气泡顶部）。 */
    public HBox toolbar() {
        return toolbar;
    }

    private HBox buildToolbar() {
        var box = new HBox(4);
        box.setStyle("-fx-background-color: rgba(0,0,0,0.3); -fx-background-radius: 4; -fx-padding: 2 4;");

        // 仅 assistant 消息可重试
        if ("assistant".equals(role)) {
            box.getChildren().add(makeButton("↻", "重试", Action.RETRY));
        }
        // 仅 user 消息可编辑
        if ("user".equals(role)) {
            box.getChildren().add(makeButton("✎", "编辑", Action.EDIT));
        }
        box.getChildren().add(makeButton("📋", "复制", Action.COPY));
        box.getChildren().add(makeButton("🗑", "删除", Action.DELETE));

        return box;
    }

    private Label makeButton(String icon, String tooltip, Action action) {
        var label = new Label(icon);
        label.setStyle("-fx-text-fill: #a6adc8; -fx-font-size: 12; -fx-cursor: hand; -fx-padding: 2 4;");
        label.setOnMouseEntered(e -> label.setStyle("-fx-text-fill: #cdd6f4; -fx-font-size: 12; -fx-cursor: hand; -fx-padding: 2 4;"));
        label.setOnMouseExited(e -> label.setStyle("-fx-text-fill: #a6adc8; -fx-font-size: 12; -fx-cursor: hand; -fx-padding: 2 4;"));
        label.setOnMouseClicked(e -> {
            log.debug("Message action: {} for {}", action, messageId);
            if (actionHandler != null) {
                actionHandler.accept(action);
            }
        });
        javafx.scene.control.Tooltip.install(label, new javafx.scene.control.Tooltip(tooltip));
        return label;
    }

    /** 显示工具栏（鼠标进入消息时调用）。 */
    public void show() {
        Platform.runLater(() -> {
            toolbar.setVisible(true);
            toolbar.setManaged(true);
        });
    }

    /** 隐藏工具栏（鼠标移出时调用）。 */
    public void hide() {
        Platform.runLater(() -> {
            toolbar.setVisible(false);
            toolbar.setManaged(false);
        });
    }

    /** 绑定到消息气泡的悬停事件。 */
    public void attachHover() {
        messageBubble.setOnMouseEntered(e -> show());
        messageBubble.setOnMouseExited(e -> hide());
    }
}
