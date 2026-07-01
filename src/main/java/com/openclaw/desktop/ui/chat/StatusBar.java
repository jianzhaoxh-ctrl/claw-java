package com.openclaw.desktop.ui.chat;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 状态栏 — 显示连接状态、当前模型、Token 用量、会话信息。
 *
 * <p>布局：{@code [● 连接状态] [模型: gpt-4o] [→ Token: 1234] [会话: 3 条消息] [速率: 45 tok/s]}
 */
public class StatusBar {

    private static final Logger log = LoggerFactory.getLogger(StatusBar.class);

    private final HBox root;
    private final Label connectionLabel;
    private final Label modelLabel;
    private final Label tokenLabel;
    private final Label messageCountLabel;
    private final Label rateLabel;

    public StatusBar() {
        root = new HBox(12);
        root.setPadding(new Insets(4, 12, 4, 12));
        root.setStyle("-fx-background-color: #11111b; -fx-border-color: #313244; -fx-border-width: 1 0 0 0;");
        root.setAlignment(Pos.CENTER_LEFT);

        connectionLabel = new Label("● 未连接");
        connectionLabel.setStyle("-fx-text-fill: #f38ba8; -fx-font-size: 11;");

        modelLabel = new Label("模型: --");
        modelLabel.setStyle("-fx-text-fill: #a6adc8; -fx-font-size: 11;");

        tokenLabel = new Label("Tokens: 0");
        tokenLabel.setStyle("-fx-text-fill: #a6adc8; -fx-font-size: 11;");

        messageCountLabel = new Label("消息: 0");
        messageCountLabel.setStyle("-fx-text-fill: #a6adc8; -fx-font-size: 11;");

        rateLabel = new Label("");
        rateLabel.setStyle("-fx-text-fill: #a6adc8; -fx-font-size: 11;");

        var spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        root.getChildren().addAll(connectionLabel, new Separator(), modelLabel, new Separator(),
            tokenLabel, new Separator(), messageCountLabel, spacer, rateLabel);
    }

    public HBox node() {
        return root;
    }

    /** 更新连接状态。 */
    public void setConnected(boolean connected, String providerName) {
        Platform.runLater(() -> {
            if (connected) {
                connectionLabel.setText("● 已连接 · " + (providerName != null ? providerName : ""));
                connectionLabel.setStyle("-fx-text-fill: #a6e3a1; -fx-font-size: 11;");
            } else {
                connectionLabel.setText("● 未连接");
                connectionLabel.setStyle("-fx-text-fill: #f38ba8; -fx-font-size: 11;");
            }
        });
    }

    /** 更新当前模型。 */
    public void setModel(String modelId) {
        Platform.runLater(() -> modelLabel.setText("模型: " + (modelId != null ? modelId : "--")));
    }

    /** 更新 Token 统计。 */
    public void setTokenStats(long total, long prompt, long completion) {
        Platform.runLater(() -> tokenLabel.setText(String.format("Tokens: %d (↑%d ↓%d)", total, prompt, completion)));
    }

    /** 更新消息计数。 */
    public void setMessageCount(int count) {
        Platform.runLater(() -> messageCountLabel.setText("消息: " + count));
    }

    /** 更新速率。 */
    public void setRate(double tokensPerSecond) {
        Platform.runLater(() -> {
            if (tokensPerSecond > 0) {
                rateLabel.setText(String.format("%.1f tok/s", tokensPerSecond));
            } else {
                rateLabel.setText("");
            }
        });
    }

    /** 从 UsageInfo 更新全部统计。 */
    public void updateFrom(com.openclaw.desktop.llm.UsageInfo usage) {
        if (usage != null) {
            setTokenStats(usage.totalTokens(), usage.promptTokens(), usage.completionTokens());
        }
    }

    /** 简易分隔符。 */
    private static class Separator extends Region {
        Separator() {
            setStyle("-fx-background-color: #313244;");
            setPrefWidth(1);
            setPrefHeight(14);
        }
    }
}
