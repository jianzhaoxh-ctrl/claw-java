package com.openclaw.desktop.ui.chat;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 工具调用进度面板 — 显示当前轮次中所有工具调用的执行状态。
 *
 * <p>每个工具调用显示为一行：图标 + 工具名 + 状态文本 + 进度条/耗时。
 * 状态：pending → running → completed / failed。
 *
 * <p>由 ChatView 在收到 {@code AgentEvent.ToolCallStarted/Completed} 时调用。
 */
public class ToolCallProgressPanel {

    private static final Logger log = LoggerFactory.getLogger(ToolCallProgressPanel.class);

    private final VBox container;
    private final Map<Integer, ToolCallEntry> entries = new ConcurrentHashMap<>();
    private final Map<String, Integer> nameToIndex = new ConcurrentHashMap<>();
    private volatile boolean visible = false;

    public ToolCallProgressPanel() {
        this.container = new VBox(4);
        this.container.setPadding(new Insets(6, 10, 6, 10));
        this.container.setStyle("-fx-background-color: rgba(49,50,68,0.3); -fx-background-radius: 8;");
        this.container.setVisible(false);
        this.container.setManaged(false);
    }

    /** 容器节点，加入消息气泡顶部。 */
    public VBox node() {
        return container;
    }

    /** 工具调用开始。 */
    public void onToolCallStarted(int index, String toolName) {
        Platform.runLater(() -> {
            ensureVisible();
            var entry = new ToolCallEntry(index, toolName);
            entries.put(index, entry);
            nameToIndex.put(toolName + "#" + index, index);
            container.getChildren().add(entry.row);
            log.debug("Tool call started: #{} {}", index, toolName);
        });
    }

    /** 工具调用完成。 */
    public void onToolCallCompleted(int index, String toolName, String result, boolean success) {
        Platform.runLater(() -> {
            var entry = entries.get(index);
            if (entry == null) {
                // 未收到 Started 事件，补一条
                onToolCallStarted(index, toolName);
                entry = entries.get(index);
                if (entry == null) return;
            }
            entry.complete(success);
            log.debug("Tool call completed: #{} {} (success={})", index, toolName, success);
        });
    }

    /** 清空所有工具调用记录（一轮对话结束后调用）。 */
    public void clear() {
        Platform.runLater(() -> {
            entries.clear();
            nameToIndex.clear();
            container.getChildren().clear();
            container.setVisible(false);
            container.setManaged(false);
            visible = false;
        });
    }

    /** 当前是否有活跃的工具调用。 */
    public boolean hasActive() {
        return entries.values().stream().anyMatch(e -> e.status == Status.RUNNING);
    }

    /** 已完成的工具调用数。 */
    public int completedCount() {
        return (int) entries.values().stream()
            .filter(e -> e.status == Status.COMPLETED || e.status == Status.FAILED)
            .count();
    }

    /** 总工具调用数。 */
    public int totalCount() {
        return entries.size();
    }

    private void ensureVisible() {
        if (!visible) {
            container.setVisible(true);
            container.setManaged(true);
            visible = true;
        }
    }

    // ---- 内部 ----

    private enum Status { PENDING, RUNNING, COMPLETED, FAILED }

    private static class ToolCallEntry {
        final int index;
        final String toolName;
        final HBox row;
        final Label iconLabel;
        final Label nameLabel;
        final Label statusLabel;
        final ProgressBar progressBar;
        final long startTime;
        Status status = Status.RUNNING;

        ToolCallEntry(int index, String toolName) {
            this.index = index;
            this.toolName = toolName;
            this.startTime = System.currentTimeMillis();
            this.iconLabel = new Label("🔧");
            this.nameLabel = new Label(toolName);
            this.nameLabel.setStyle("-fx-text-fill: #cdd6f4; -fx-font-size: 12; -fx-font-weight: bold;");
            this.statusLabel = new Label("执行中...");
            this.statusLabel.setStyle("-fx-text-fill: #a6adc8; -fx-font-size: 11;");
            this.progressBar = new ProgressBar(-1); // 不确定进度
            this.progressBar.setPrefWidth(80);
            this.progressBar.setPrefHeight(8);

            this.row = new HBox(8, iconLabel, nameLabel, statusLabel, progressBar);
            this.row.setAlignment(Pos.CENTER_LEFT);
            this.row.setPadding(new Insets(2, 0, 2, 0));

            // 启动耗时更新
            startElapsedUpdater();
        }

        void complete(boolean success) {
            status = success ? Status.COMPLETED : Status.FAILED;
            var elapsed = (System.currentTimeMillis() - startTime) / 1000.0;
            Platform.runLater(() -> {
                iconLabel.setText(success ? "✅" : "❌");
                statusLabel.setText(success
                    ? String.format("完成 (%.1fs)", elapsed)
                    : String.format("失败 (%.1fs)", elapsed));
                progressBar.setProgress(1.0);
                progressBar.setStyle(success
                    ? "-fx-accent: #a6e3a1;"
                    : "-fx-accent: #f38ba8;");
            });
        }

        private void startElapsedUpdater() {
            var updater = new javafx.animation.Timeline(
                new javafx.animation.KeyFrame(javafx.util.Duration.millis(200), e -> {
                    if (status == Status.RUNNING) {
                        var elapsed = (System.currentTimeMillis() - startTime) / 1000.0;
                        statusLabel.setText(String.format("执行中 (%.1fs)", elapsed));
                    }
                }));
            updater.setCycleCount(javafx.animation.Animation.INDEFINITE);
            updater.play();
            // 完成后停止（在 complete 中无法直接引用 updater，依赖 GC）
        }
    }
}
