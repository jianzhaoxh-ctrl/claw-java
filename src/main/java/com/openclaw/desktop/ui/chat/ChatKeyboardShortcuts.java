package com.openclaw.desktop.ui.chat;

import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 键盘快捷键管理器 — 集中管理聊天界面的快捷键绑定。
 *
 * <p>默认快捷键：
 * <ul>
 *   <li>{@code Enter} — 发送消息</li>
 *   <li>{@code Shift+Enter} — 换行</li>
 *   <li>{@code Ctrl+Enter} — 换行（兼容）</li>
 *   <li>{@code Ctrl+N} — 新建会话</li>
 *   <li>{@code Ctrl+K} — 搜索会话</li>
 *   <li>{@code Ctrl+,} — 打开设置</li>
 *   <li>{@code Ctrl+B} — 切换侧边栏</li>
 *   <li>{@code Ctrl+L} — 清空当前会话</li>
 *   <li>{@code Esc} — 取消当前操作</li>
 *   <li>{@code Ctrl+Shift+S} — 导出会话</li>
 * </ul>
 *
 * <p>可自定义：{@link #bind(String, Runnable)} 注册自定义快捷键。
 */
public class ChatKeyboardShortcuts {

    private static final Logger log = LoggerFactory.getLogger(ChatKeyboardShortcuts.class);

    private final Map<String, Runnable> handlers = new HashMap<>();

    /** 注册快捷键处理器。 */
    public void bind(String action, Runnable handler) {
        handlers.put(action, handler);
    }

    /** 解绑。 */
    public void unbind(String action) {
        handlers.remove(action);
    }

    /**
     * 处理键盘事件，返回是否已消费。
     */
    public boolean handle(KeyEvent event) {
        var code = event.getCode();
        var ctrl = event.isControlDown();
        var shift = event.isShiftDown();
        var alt = event.isAltDown();

        // Enter 发送（无修饰键）
        if (code == KeyCode.ENTER && !ctrl && !shift && !alt) {
            return invoke("send", event);
        }
        // Shift+Enter 或 Ctrl+Enter 换行（不消费，让 TextArea 处理）
        if (code == KeyCode.ENTER && (shift || ctrl)) {
            return false;
        }
        // Ctrl+N 新建会话
        if (ctrl && code == KeyCode.N && !shift) {
            return invoke("newSession", event);
        }
        // Ctrl+K 搜索
        if (ctrl && code == KeyCode.K) {
            return invoke("search", event);
        }
        // Ctrl+, 设置
        if (ctrl && code == KeyCode.COMMA) {
            return invoke("settings", event);
        }
        // Ctrl+B 切换侧边栏
        if (ctrl && code == KeyCode.B) {
            return invoke("toggleSidebar", event);
        }
        // Ctrl+L 清空
        if (ctrl && code == KeyCode.L) {
            return invoke("clearSession", event);
        }
        // Ctrl+Shift+S 导出
        if (ctrl && shift && code == KeyCode.S) {
            return invoke("export", event);
        }
        // Esc 取消
        if (code == KeyCode.ESCAPE) {
            return invoke("cancel", event);
        }
        return false;
    }

    private boolean invoke(String action, KeyEvent event) {
        var handler = handlers.get(action);
        if (handler != null) {
            event.consume();
            try {
                handler.run();
            } catch (Exception e) {
                log.error("Shortcut handler '{}' failed: {}", action, e.getMessage(), e);
            }
            return true;
        }
        return false;
    }

    /** 所有已注册的 action 名称。 */
    public java.util.Set<String> registeredActions() {
        return java.util.Collections.unmodifiableSet(handlers.keySet());
    }
}
