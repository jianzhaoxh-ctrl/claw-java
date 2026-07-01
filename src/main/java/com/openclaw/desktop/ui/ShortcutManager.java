package com.openclaw.desktop.ui;

import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 全局快捷键管理器 — 注册和分发快捷键。
 * 对应 OpenClaw 的键盘快捷键系统。
 */
public class ShortcutManager {

    private static final Logger log = LoggerFactory.getLogger(ShortcutManager.class);
    private final Map<KeyCodeCombination, Runnable> bindings = new ConcurrentHashMap<>();
    private final Map<String, KeyCodeCombination> namedBindings = new ConcurrentHashMap<>();

    public ShortcutManager() {
        register("new-session", new KeyCodeCombination(KeyCode.N, KeyCombination.SHORTCUT_DOWN), () -> {});
        register("save", new KeyCodeCombination(KeyCode.S, KeyCombination.SHORTCUT_DOWN), () -> {});
        register("settings", new KeyCodeCombination(KeyCode.S, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN), () -> {});
        register("copy-last-reply", new KeyCodeCombination(KeyCode.C, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN), () -> {});
        register("clear-screen", new KeyCodeCombination(KeyCode.L, KeyCombination.SHORTCUT_DOWN), () -> {});
        register("command-palette", new KeyCodeCombination(KeyCode.SLASH, KeyCombination.SHORTCUT_DOWN), () -> {});
        register("refresh", new KeyCodeCombination(KeyCode.F5), () -> {});
        register("reset-session", new KeyCodeCombination(KeyCode.R, KeyCombination.SHORTCUT_DOWN), () -> {});
    }

    /** 注册快捷键 */
    public void register(String name, KeyCodeCombination combo, Runnable action) {
        bindings.put(combo, action);
        namedBindings.put(name, combo);
        log.debug("Registered shortcut: {} = {}", combo, name);
    }

    /** 覆盖快捷键动作 */
    public void override(String name, Runnable action) {
        var combo = namedBindings.get(name);
        if (combo != null) {
            bindings.put(combo, action);
        }
    }

    /** 处理按键事件 */
    public boolean handle(KeyEvent event) {
        for (var entry : bindings.entrySet()) {
            if (entry.getKey().match(event)) {
                event.consume();
                entry.getValue().run();
                return true;
            }
        }
        return false;
    }

    /** 获取所有注册的快捷键 */
    public Map<KeyCodeCombination, Runnable> allBindings() {
        return Map.copyOf(bindings);
    }

    /** 快捷键帮助文本 */
    public String helpText() {
        var sb = new StringBuilder();
        sb.append("ClawDesktop Keyboard Shortcuts:\n\n");
        sb.append("Ctrl+N        New Session\n");
        sb.append("Ctrl+S        Save / Export\n");
        sb.append("Ctrl+Shift+S  Settings\n");
        sb.append("Ctrl+/        Command Palette\n");
        sb.append("Ctrl+Shift+C  Copy Last Reply\n");
        sb.append("Ctrl+L        Clear Screen\n");
        sb.append("Ctrl+R        Reset Session\n");
        sb.append("F5            Refresh / Reconnect\n");
        sb.append("Enter         Send Message\n");
        sb.append("Shift+Enter   New Line\n");
        return sb.toString();
    }
}
