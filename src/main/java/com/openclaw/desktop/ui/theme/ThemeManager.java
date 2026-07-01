package com.openclaw.desktop.ui.theme;

import javafx.scene.Scene;
import javafx.scene.Parent;

import java.util.Objects;

/**
 * 主题管理器 — Light / Dark 主题切换，提供样式工具方法。
 */
public class ThemeManager {

    public enum Theme { DARK, LIGHT }

    private Theme currentTheme = Theme.DARK;

    public Theme current() {
        return currentTheme;
    }

    public void setTheme(Theme theme) {
        this.currentTheme = theme;
    }

    /** 切换主题并应用到 Scene */
    public void toggle(Scene scene) {
        currentTheme = (currentTheme == Theme.DARK) ? Theme.LIGHT : Theme.DARK;
        applyToScene(scene);
    }

    /** 应用当前主题到 Scene */
    public void applyToScene(Scene scene) {
        if (scene == null) return;
        scene.getStylesheets().clear();
        scene.getStylesheets().add(getStyleSheet());
    }

    public String getStyleSheet() {
        var url = switch (currentTheme) {
            case DARK -> "/ui/dark.css";
            case LIGHT -> "/ui/light.css";
        };
        var resource = getClass().getResource(url);
        return resource != null ? resource.toExternalForm() : "";
    }

    // ---- 样式字符串工具 ----

    public static String sidebarStyle(Theme theme) {
        return theme == Theme.DARK
            ? "-fx-background-color: #181825; -fx-border-color: #313244; -fx-border-width: 0 1 0 0;"
            : "-fx-background-color: #f5f5f5; -fx-border-color: #ddd; -fx-border-width: 0 1 0 0;";
    }

    public static String inputStyle(Theme theme) {
        return theme == Theme.DARK
            ? "-fx-control-inner-background: #313244; -fx-text-fill: #cdd6f4; -fx-prompt-text-fill: #585b70;"
            : "-fx-control-inner-background: #ffffff; -fx-text-fill: #1e1e1e; -fx-prompt-text-fill: #999;";
    }

    public static String userBubbleStyle(Theme theme) {
        return theme == Theme.DARK
            ? "-fx-background-color: #313244; -fx-background-radius: 12; -fx-text-fill: #cdd6f4;"
            : "-fx-background-color: #e1e1e1; -fx-background-radius: 12; -fx-text-fill: #1e1e1e;";
    }

    public static String assistantBubbleStyle(Theme theme) {
        return theme == Theme.DARK
            ? "-fx-background-color: #1e1e2e; -fx-background-radius: 12; -fx-text-fill: #cdd6f4; -fx-border-color: #45475a; -fx-border-radius: 12; -fx-border-width: 1;"
            : "-fx-background-color: #ffffff; -fx-background-radius: 12; -fx-text-fill: #1e1e1e; -fx-border-color: #ddd; -fx-border-radius: 12; -fx-border-width: 1;";
    }

    public static String backgroundColor(Theme theme) {
        return theme == Theme.DARK ? "#1e1e2e;" : "#ffffff;";
    }

    public static String textColor(Theme theme) {
        return theme == Theme.DARK ? "#cdd6f4;" : "#1e1e1e;";
    }
}
