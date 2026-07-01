package com.openclaw.desktop.ui.md;

import com.openclaw.desktop.ui.theme.ThemeManager;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * 代码块渲染器 — 将代码渲染为带语法高亮、行号、复制按钮的 JavaFX 节点。
 *
 * <p>布局：
 * <pre>
 * ┌─────────────────────────────────┐
 * │ [lang]              [📋 Copy]   │  ← 工具栏
 * ├─────────────────────────────────┤
 * │  1 │ public class Foo {          │
 * │  2 │     public static void...   │  ← 代码区（行号 + 高亮）
 * │  3 │ }                           │
 * └─────────────────────────────────┘
 * </pre>
 */
public class CodeBlockRenderer {

    private static final Logger log = LoggerFactory.getLogger(CodeBlockRenderer.class);

    /** Token 颜色（暗色主题）。 */
    private static final java.util.Map<CodeHighlighter.TokenType, String> DARK_COLORS = java.util.Map.of(
        CodeHighlighter.TokenType.PLAIN, "#cdd6f4",
        CodeHighlighter.TokenType.KEYWORD, "#cba6f7",
        CodeHighlighter.TokenType.STRING, "#a6e3a1",
        CodeHighlighter.TokenType.COMMENT, "#6c7086",
        CodeHighlighter.TokenType.NUMBER, "#fab387",
        CodeHighlighter.TokenType.ANNOTATION, "#f9e2af",
        CodeHighlighter.TokenType.FUNCTION, "#89b4fa",
        CodeHighlighter.TokenType.OPERATOR, "#f38ba8",
        CodeHighlighter.TokenType.TYPE, "#f9e2af"
    );

    /** Token 颜色（亮色主题）。 */
    private static final java.util.Map<CodeHighlighter.TokenType, String> LIGHT_COLORS = java.util.Map.of(
        CodeHighlighter.TokenType.PLAIN, "#1e1e1e",
        CodeHighlighter.TokenType.KEYWORD, "#8e44ad",
        CodeHighlighter.TokenType.STRING, "#27ae60",
        CodeHighlighter.TokenType.COMMENT, "#95a5a6",
        CodeHighlighter.TokenType.NUMBER, "#e67e22",
        CodeHighlighter.TokenType.ANNOTATION, "#f39c12",
        CodeHighlighter.TokenType.FUNCTION, "#2980b9",
        CodeHighlighter.TokenType.OPERATOR, "#c0392b",
        CodeHighlighter.TokenType.TYPE, "#d35400"
    );

    /**
     * 渲染代码块。
     * @param code 代码内容
     * @param lang 语言标识（如 "java"、"python"）
     * @param theme 当前主题
     * @return 渲染后的 VBox 节点
     */
    public VBox render(String code, String lang, ThemeManager.Theme theme) {
        var language = CodeHighlighter.resolveLanguage(lang);
        var segments = CodeHighlighter.highlight(code, language);
        var colors = theme == ThemeManager.Theme.DARK ? DARK_COLORS : LIGHT_COLORS;
        var bgColor = theme == ThemeManager.Theme.DARK ? "#11111b" : "#f6f6f6";
        var borderColor = theme == ThemeManager.Theme.DARK ? "#313244" : "#ddd";
        var lineNumColor = theme == ThemeManager.Theme.DARK ? "#585b70" : "#bbb";

        var root = new VBox();
        root.setStyle("-fx-background-color: " + bgColor + "; -fx-border-color: " + borderColor
            + "; -fx-border-radius: 8; -fx-background-radius: 8; -fx-border-width: 1;");

        // 工具栏
        var toolbar = new HBox(8);
        toolbar.setPadding(new Insets(6, 10, 6, 10));
        toolbar.setStyle("-fx-background-color: " + borderColor + "; -fx-background-radius: 8 8 0 0;");

        var langLabel = new Label(language == CodeHighlighter.Language.PLAIN
            ? (lang == null || lang.isBlank() ? "code" : lang) : language.name().toLowerCase());
        langLabel.setStyle("-fx-text-fill: " + lineNumColor + "; -fx-font-size: 11; -fx-font-family: 'Consolas';");

        var spacer = new javafx.scene.layout.Region();
        HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);

        var copyBtn = new Button("📋 复制");
        copyBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: " + lineNumColor
            + "; -fx-font-size: 11; -fx-cursor: hand;");
        copyBtn.setOnAction(e -> {
            var content = new ClipboardContent();
            content.putString(code);
            Clipboard.getSystemClipboard().setContent(content);
            copyBtn.setText("✅ 已复制");
            new java.util.Timer().schedule(new java.util.TimerTask() {
                @Override
                public void run() {
                    Platform.runLater(() -> copyBtn.setText("📋 复制"));
                }
            }, 1500);
        });

        toolbar.getChildren().addAll(langLabel, spacer, copyBtn);

        // 代码区（行号 + 高亮文本）
        var lines = code.split("\n", -1);
        var codeBox = new HBox(8);
        codeBox.setPadding(new Insets(8, 10, 8, 10));

        // 行号
        var lineNumStr = new StringBuilder();
        for (int i = 1; i <= lines.length; i++) {
            if (i > 1) lineNumStr.append('\n');
            lineNumStr.append(String.format("%3d", i));
        }
        var lineNumText = new Text(lineNumStr.toString());
        lineNumText.setFont(Font.font("Consolas", 12));
        lineNumText.setStyle("-fx-fill: " + lineNumColor + ";");
        var lineNumFlow = new TextFlow(lineNumText);
        lineNumFlow.setLineSpacing(2);

        // 代码内容
        var codeFlow = buildCodeFlow(segments, colors);
        codeFlow.setLineSpacing(2);
        codeFlow.setMaxWidth(Double.MAX_VALUE);

        codeBox.getChildren().addAll(lineNumFlow, codeFlow);
        HBox.setHgrow(codeFlow, javafx.scene.layout.Priority.ALWAYS);

        root.getChildren().addAll(toolbar, codeBox);
        return root;
    }

    private TextFlow buildCodeFlow(List<CodeHighlighter.Segment> segments,
                                   java.util.Map<CodeHighlighter.TokenType, String> colors) {
        var flow = new TextFlow();
        for (var seg : segments) {
            var text = new Text(seg.text());
            text.setFont(Font.font("Consolas", 12));
            var color = colors.getOrDefault(seg.type(), colors.get(CodeHighlighter.TokenType.PLAIN));
            text.setStyle("-fx-fill: " + color + ";");
            flow.getChildren().add(text);
        }
        if (flow.getChildren().isEmpty()) {
            flow.getChildren().add(new Text(""));
        }
        return flow;
    }
}
