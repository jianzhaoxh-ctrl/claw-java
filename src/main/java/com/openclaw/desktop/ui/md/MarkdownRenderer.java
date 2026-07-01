package com.openclaw.desktop.ui.md;

import com.openclaw.desktop.ui.theme.ThemeManager;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.layout.*;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;
import javafx.scene.text.TextFlow;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Markdown 渲染器 — 将 Markdown 文本渲染为 JavaFX 节点。
 *
 * <p>支持：标题、粗体、斜体、代码块（语法高亮 + 行号 + 复制按钮）、行内代码、
 * 有序/无序列表、链接、引用块、表格、水平分隔线。
 *
 * <p>代码块渲染委托给 {@link CodeBlockRenderer}，表格渲染由 {@link #renderTable} 处理。
 */
public class MarkdownRenderer {

    private static final Pattern HEADER = Pattern.compile("^(#{1,6})\\s+(.+)$");
    private static final Pattern BOLD = Pattern.compile("\\*\\*(.+?)\\*\\*");
    private static final Pattern ITALIC = Pattern.compile("\\*(.+?)\\*");
    private static final Pattern CODE_INLINE = Pattern.compile("`(.+?)`");
    private static final Pattern LINK = Pattern.compile("\\[(.+?)]\\((.+?)\\)");
    private static final Pattern LIST_ITEM = Pattern.compile("^[\\-*]\\s+(.+)$");
    private static final Pattern NUM_LIST = Pattern.compile("^\\d+\\.\\s+(.+)$");
    private static final Pattern CODE_BLOCK_START = Pattern.compile("^```(.*)$");
    private static final Pattern TABLE_ROW = Pattern.compile("^\\|(.+)\\|$");
    private static final Pattern TABLE_SEPARATOR = Pattern.compile("^\\|?[\\s:]*-{2,}[\\s:|-]*\\|?$");
    private static final Pattern BLOCKQUOTE = Pattern.compile("^>\\s*(.*)$");
    private static final Pattern HR = Pattern.compile("^(---|\\*\\*\\*|___)\\s*$");

    private ThemeManager.Theme theme = ThemeManager.Theme.DARK;
    private final CodeBlockRenderer codeBlockRenderer = new CodeBlockRenderer();

    /** 设置主题（影响代码块与表格配色）。 */
    public void setTheme(ThemeManager.Theme theme) {
        this.theme = theme;
    }

    public ThemeManager.Theme theme() {
        return theme;
    }

    /**
     * 渲染 Markdown 文本为 JavaFX VBox。
     */
    public VBox render(String markdown, String textColor) {
        var box = new VBox(4);
        var lines = markdown.split("\n");

        boolean inCodeBlock = false;
        var codeContent = new StringBuilder();
        String codeLang = "";
        var tableRows = new ArrayList<String[]>();
        boolean inTable = false;

        for (int lineIdx = 0; lineIdx < lines.length; lineIdx++) {
            var line = lines[lineIdx];

            // 代码块处理
            if (inCodeBlock) {
                if (line.trim().equals("```")) {
                    var codeBlock = codeBlockRenderer.render(codeContent.toString(), codeLang, theme);
                    box.getChildren().add(codeBlock);
                    inCodeBlock = false;
                    codeContent.setLength(0);
                } else {
                    codeContent.append(line).append('\n');
                }
                continue;
            }
            var codeStart = CODE_BLOCK_START.matcher(line);
            if (codeStart.matches()) {
                inCodeBlock = true;
                codeLang = codeStart.group(1);
                continue;
            }

            // 表格处理
            var tableRowMatch = TABLE_ROW.matcher(line);
            if (tableRowMatch.matches()) {
                var cells = tableRowMatch.group(1).split("\\|");
                for (int i = 0; i < cells.length; i++) cells[i] = cells[i].trim();
                tableRows.add(cells);
                inTable = true;
                continue;
            } else if (inTable) {
                // 表格结束
                if (!tableRows.isEmpty()) {
                    box.getChildren().add(renderTable(tableRows, textColor));
                    tableRows.clear();
                }
                inTable = false;
            }

            // 水平分隔线
            if (HR.matcher(line).matches()) {
                var sep = new Separator();
                sep.setStyle("-fx-background-color: " + (theme == ThemeManager.Theme.DARK ? "#313244" : "#ddd") + ";");
                box.getChildren().add(sep);
                continue;
            }

            // 引用块
            var bq = BLOCKQUOTE.matcher(line);
            if (bq.matches()) {
                var quoteText = bq.group(1);
                var flow = createRichText(quoteText, textColor);
                var quoteBox = new VBox(flow);
                quoteBox.setStyle("-fx-border-color: #89b4fa; -fx-border-width: 0 0 0 3 0 0; "
                    + "-fx-background-color: rgba(137,180,250,0.05); -fx-padding: 6 10;");
                quoteBox.setPadding(new javafx.geometry.Insets(6, 10, 6, 10));
                box.getChildren().add(quoteBox);
                continue;
            }

            // 标题
            var header = HEADER.matcher(line);
            if (header.matches()) {
                var level = header.group(1).length();
                var text = header.group(2);
                var label = new Label(text);
                label.setStyle(switch (level) {
                    case 1 -> "-fx-font-size: 20; -fx-font-weight: bold; -fx-text-fill: " + textColor + ";";
                    case 2 -> "-fx-font-size: 17; -fx-font-weight: bold; -fx-text-fill: " + textColor + ";";
                    case 3 -> "-fx-font-size: 15; -fx-font-weight: bold; -fx-text-fill: " + textColor + ";";
                    default -> "-fx-font-size: 13; -fx-font-weight: bold; -fx-text-fill: " + textColor + ";";
                });
                label.setWrapText(true);
                box.getChildren().add(label);
                continue;
            }

            // 列表项
            var listItem = LIST_ITEM.matcher(line);
            if (listItem.matches()) {
                var flow = createRichText("• " + listItem.group(1), textColor);
                flow.setPadding(new javafx.geometry.Insets(0, 0, 0, 12));
                box.getChildren().add(flow);
                continue;
            }

            var numList = NUM_LIST.matcher(line);
            if (numList.matches()) {
                var flow = createRichText(line, textColor);
                flow.setPadding(new javafx.geometry.Insets(0, 0, 0, 12));
                box.getChildren().add(flow);
                continue;
            }

            // 空行
            if (line.isBlank()) {
                box.getChildren().add(new Label(""));
                continue;
            }

            // 普通段落
            var flow = createRichText(line, textColor);
            box.getChildren().add(flow);
        }

        // 未闭合的代码块
        if (inCodeBlock && codeContent.length() > 0) {
            var codeBlock = codeBlockRenderer.render(codeContent.toString(), codeLang, theme);
            box.getChildren().add(codeBlock);
        }
        // 未结束的表格
        if (!tableRows.isEmpty()) {
            box.getChildren().add(renderTable(tableRows, textColor));
        }

        return box;
    }

    /**
     * 渲染 Markdown 表格。
     * @param rows 行数据，第一行为表头，第二行为分隔行（忽略），其余为数据行
     */
    private Node renderTable(List<String[]> rows, String textColor) {
        if (rows.isEmpty()) return new Label("");
        // 跳过分隔行
        var dataRows = new ArrayList<String[]>();
        for (int i = 0; i < rows.size(); i++) {
            if (i == 1 && TABLE_SEPARATOR.matcher("|" + String.join("|", rows.get(i)) + "|").matches()) {
                continue;
            }
            dataRows.add(rows.get(i));
        }
        if (dataRows.isEmpty()) return new Label("");

        var maxCols = dataRows.stream().mapToInt(r -> r.length).max().orElse(1);
        var table = new javafx.scene.layout.GridPane();
        table.setHgap(8);
        table.setVgap(4);
        table.setStyle("-fx-background-color: " + (theme == ThemeManager.Theme.DARK ? "#1e1e2e" : "#fff")
            + "; -fx-padding: 8; -fx-background-radius: 6; -fx-border-color: "
            + (theme == ThemeManager.Theme.DARK ? "#313244" : "#ddd") + "; -fx-border-radius: 6;");

        for (int r = 0; r < dataRows.size(); r++) {
            var row = dataRows.get(r);
            var isHeader = r == 0;
            for (int c = 0; c < maxCols; c++) {
                var cellText = c < row.length ? row[c] : "";
                var flow = createRichText(cellText, textColor);
                var cellBox = new VBox(flow);
                cellBox.setPadding(new javafx.geometry.Insets(4, 8, 4, 8));
                if (isHeader) {
                    cellBox.setStyle("-fx-background-color: " + (theme == ThemeManager.Theme.DARK ? "#313244" : "#f0f0f0")
                        + "; -fx-background-radius: 4;");
                }
                table.add(cellBox, c, r);
            }
        }
        return table;
    }

    /**
     * 创建带行内格式的文本流（粗体/斜体/代码/链接）。
     */
    private TextFlow createRichText(String text, String textColor) {
        var flow = new TextFlow();
        var combined = Pattern.compile("(\\*\\*.+?\\*\\*)|(`.+?`)|(\\*.+?\\*)|(\\[.+?]\\(.+?\\))");
        var matcher = combined.matcher(text);
        var lastEnd = 0;

        while (matcher.find()) {
            if (matcher.start() > lastEnd) {
                var plain = new Text(text.substring(lastEnd, matcher.start()));
                plain.setStyle("-fx-fill: " + textColor + "; -fx-font-size: 13;");
                flow.getChildren().add(plain);
            }

            var group = matcher.group();
            if (group.startsWith("**")) {
                var t = new Text(group.substring(2, group.length() - 2));
                t.setStyle("-fx-fill: " + textColor + "; -fx-font-weight: bold; -fx-font-size: 13;");
                flow.getChildren().add(t);
            } else if (group.startsWith("`")) {
                var t = new Text(group.substring(1, group.length() - 1));
                t.setStyle("-fx-fill: #a6e3a1; -fx-font-family: 'Consolas'; -fx-font-size: 12; -fx-background-color: #1a1a2e;");
                flow.getChildren().add(t);
            } else if (group.startsWith("[")) {
                var linkMatcher = LINK.matcher(group);
                if (linkMatcher.matches()) {
                    var t = new Text(linkMatcher.group(1));
                    t.setStyle("-fx-fill: #89b4fa; -fx-underline: true; -fx-font-size: 13;");
                    flow.getChildren().add(t);
                }
            } else if (group.startsWith("*")) {
                var t = new Text(group.substring(1, group.length() - 1));
                t.setStyle("-fx-fill: " + textColor + "; -fx-font-style: italic; -fx-font-size: 13;");
                flow.getChildren().add(t);
            }
            lastEnd = matcher.end();
        }

        if (lastEnd < text.length()) {
            var plain = new Text(text.substring(lastEnd));
            plain.setStyle("-fx-fill: " + textColor + "; -fx-font-size: 13;");
            flow.getChildren().add(plain);
        }

        if (flow.getChildren().isEmpty()) {
            var plain = new Text(text);
            plain.setStyle("-fx-fill: " + textColor + "; -fx-font-size: 13;");
            flow.getChildren().add(plain);
        }

        return flow;
    }
}
