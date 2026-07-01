package com.openclaw.desktop.desktop;

import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 剪贴板管理器 — 增强 JavaFX 剪贴板能力，提供历史记录与便捷操作。
 *
 * <p>功能：
 * <ul>
 *   <li>复制文本到系统剪贴板</li>
 *   <li>从系统剪贴板粘贴文本</li>
 *   <li>剪贴板历史记录（内存，最多 N 条，应用重启后失效）</li>
 *   <li>清空历史</li>
 *   <li>富文本（HTML）复制支持</li>
 * </ul>
 */
public class ClipboardManager {

    private static final Logger log = LoggerFactory.getLogger(ClipboardManager.class);
    private static final int DEFAULT_HISTORY_LIMIT = 50;

    private final int historyLimit;
    private final List<String> history = Collections.synchronizedList(new ArrayList<>());

    public ClipboardManager() {
        this(DEFAULT_HISTORY_LIMIT);
    }

    public ClipboardManager(int historyLimit) {
        this.historyLimit = historyLimit;
    }

    /**
     * 复制纯文本到系统剪贴板。
     */
    public void copy(String text) {
        var content = new ClipboardContent();
        content.putString(text);
        javafx.application.Platform.runLater(() -> {
            Clipboard.getSystemClipboard().setContent(content);
            addToHistory(text);
            log.debug("Copied {} chars to clipboard", text.length());
        });
    }

    /**
     * 复制富文本（同时设置 plain text + html）到系统剪贴板。
     */
    public void copyRich(String plainText, String html) {
        var content = new ClipboardContent();
        content.putString(plainText);
        content.putHtml(html);
        javafx.application.Platform.runLater(() -> {
            Clipboard.getSystemClipboard().setContent(content);
            addToHistory(plainText);
            log.debug("Copied rich text ({} chars, html {} chars)", plainText.length(), html.length());
        });
    }

    /**
     * 从系统剪贴板读取纯文本。
     * @return 剪贴板文本，若为空或非文本返回空字符串
     */
    public String paste() {
        var clipboard = Clipboard.getSystemClipboard();
        var text = clipboard.getString();
        return text != null ? text : "";
    }

    /**
     * 从系统剪贴板读取 HTML（若可用）。
     */
    public String pasteHtml() {
        var clipboard = Clipboard.getSystemClipboard();
        var html = clipboard.getHtml();
        return html != null ? html : "";
    }

    /**
     * 检查剪贴板是否有文本内容。
     */
    public boolean hasText() {
        return Clipboard.getSystemClipboard().hasString();
    }

    /**
     * 清空系统剪贴板。
     */
    public void clear() {
        javafx.application.Platform.runLater(() -> {
            Clipboard.getSystemClipboard().clear();
            log.debug("Clipboard cleared");
        });
    }

    // ---- 历史记录 ----

    private void addToHistory(String text) {
        if (text == null || text.isBlank()) return;
        // 去重：若最新一条与当前相同则跳过
        if (!history.isEmpty() && history.get(history.size() - 1).equals(text)) return;
        history.add(text);
        while (history.size() > historyLimit) {
            history.remove(0);
        }
    }

    /**
     * 获取剪贴板历史（最新在前）。
     */
    public List<String> history() {
        var snapshot = new ArrayList<>(history);
        Collections.reverse(snapshot);
        return Collections.unmodifiableList(snapshot);
    }

    /**
     * 按索引从历史中复制（重新放入剪贴板）。
     */
    public boolean copyFromHistory(int index) {
        if (index < 0 || index >= history.size()) return false;
        var text = history.get(index);
        copy(text);
        return true;
    }

    /**
     * 清空剪贴板历史。
     */
    public void clearHistory() {
        history.clear();
        log.debug("Clipboard history cleared");
    }

    /**
     * 历史记录条数。
     */
    public int historySize() {
        return history.size();
    }

    /**
     * 历史记录上限。
     */
    public int historyLimit() {
        return historyLimit;
    }
}
