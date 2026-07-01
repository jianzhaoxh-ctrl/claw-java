package com.openclaw.desktop.ui.chat;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.text.DecimalFormat;
import java.util.Locale;

/**
 * Token 使用统计显示 — 对应 OpenClaw 的 token 使用量 UI。
 *
 * <p>显示：
 * <ul>
 *   <li>Prompt tokens</li>
 *   <li>Completion tokens</li>
 *   <li>Total tokens</li>
 *   <li>预估成本</li>
 *   <li>模型名称</li>
 * </ul>
 */
public class TokenStats {

    private static final DecimalFormat COST_FORMAT = new DecimalFormat("#.####");
    private static final DecimalFormat TOKEN_FORMAT = new DecimalFormat("#,###");

    private final VBox container;
    private final Label promptLabel;
    private final Label completionLabel;
    private final Label totalLabel;
    private final Label costLabel;
    private final Label modelLabel;

    public TokenStats() {
        container = new VBox(2);
        container.setPadding(new Insets(4, 8, 4, 8));
        container.setStyle("-fx-background-color: #2d2d3f; -fx-background-radius: 4; -fx-border-radius: 4;");

        promptLabel = createStatLabel("Prompt: 0");
        completionLabel = createStatLabel("Completion: 0");
        totalLabel = createStatLabel("Total: 0");
        costLabel = createStatLabel("Cost: $0.0000");
        modelLabel = createStatLabel("Model: -");

        container.getChildren().addAll(modelLabel, promptLabel, completionLabel, totalLabel, costLabel);
    }

    /** 更新统计数据 */
    public void update(com.openclaw.desktop.llm.UsageInfo usage, String modelId) {
        if (usage != null) {
            promptLabel.setText("Prompt: " + TOKEN_FORMAT.format(usage.promptTokens()));
            completionLabel.setText("Completion: " + TOKEN_FORMAT.format(usage.completionTokens()));
            totalLabel.setText("Total: " + TOKEN_FORMAT.format(usage.totalTokens()));

            var inputCost = usage.inputCost() != null ? usage.inputCost() : 0.0;
            var outputCost = usage.outputCost() != null ? usage.outputCost() : 0.0;
            costLabel.setText("Cost: $" + COST_FORMAT.format(inputCost + outputCost));
        }
        if (modelId != null) {
            modelLabel.setText("Model: " + modelId);
        }
    }

    /** 清零 */
    public void reset() {
        promptLabel.setText("Prompt: 0");
        completionLabel.setText("Completion: 0");
        totalLabel.setText("Total: 0");
        costLabel.setText("Cost: $0.0000");
        modelLabel.setText("Model: -");
    }

    /** 获取 UI 节点 */
    public VBox node() { return container; }

    private Label createStatLabel(String text) {
        var label = new Label(text);
        label.setStyle("-fx-text-fill: #a0a0b0; -fx-font-size: 11; -fx-font-family: 'Consolas';");
        return label;
    }

    // ========== 预估成本计算 ==========

    /** 根据模型和 token 使用量计算预估成本 */
    public static double estimateCost(String modelId, int promptTokens, int completionTokens) {
        var pricing = ModelPricing.get(modelId);
        return (promptTokens * pricing.inputCostPer1k / 1000.0)
             + (completionTokens * pricing.outputCostPer1k / 1000.0);
    }

    /** 模型定价信息 */
    public record ModelPricing(double inputCostPer1k, double outputCostPer1k) {
        private static final ModelPricing DEFAULT = new ModelPricing(0.01, 0.03);

        public static ModelPricing get(String modelId) {
            if (modelId == null) return DEFAULT;
            return switch (modelId.toLowerCase()) {
                case "gpt-4o" -> new ModelPricing(2.5, 10.0);
                case "gpt-4o-mini" -> new ModelPricing(0.15, 0.6);
                case "gpt-4-turbo" -> new ModelPricing(10.0, 30.0);
                case "gpt-3.5-turbo" -> new ModelPricing(0.5, 1.5);
                case "claude-3.5-sonnet" -> new ModelPricing(3.0, 15.0);
                case "claude-3-opus" -> new ModelPricing(15.0, 75.0);
                case "claude-3-haiku" -> new ModelPricing(0.25, 1.25);
                case "deepseek-chat" -> new ModelPricing(0.14, 0.28);
                case "deepseek-coder" -> new ModelPricing(0.14, 0.28);
                case String s when s.startsWith("qwen") -> new ModelPricing(0.3, 0.6);
                default -> DEFAULT;
            };
        }
    }
}
