package com.openclaw.desktop.desktop;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableMap;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Optional;
import java.util.prefs.Preferences;

/**
 * 窗口管理器 — 管理多个 JavaFX 窗口（Stage），提供置顶/透明度/位置持久化等能力。
 *
 * <p>功能：
 * <ul>
 *   <li>多窗口注册与查找（按 name 索引）</li>
 *   <li>窗口置顶切换（Stage.alwaysOnTop）</li>
 *   <li>窗口透明度调节（0.1 ~ 1.0）</li>
 *   <li>窗口居中到屏幕</li>
 *   <li>记住窗口位置与大小（基于 Java Preferences API，跨重启持久化）</li>
 *   <li>窗口最小/默认尺寸约束</li>
 * </ul>
 */
public class WindowManager {

    private static final Logger log = LoggerFactory.getLogger(WindowManager.class);
    private static final String PREF_NODE = "com/openclaw/desktop/windows";

    private final ObservableMap<String, Stage> windows = FXCollections.observableHashMap();
    private final Preferences prefs = Preferences.userRoot().node(PREF_NODE);
    private Stage primaryStage;

    public WindowManager() {}

    /** 设置主窗口。 */
    public void setPrimaryStage(Stage stage) {
        this.primaryStage = stage;
        register("main", stage);
        restoreWindowGeometry("main", stage);
        // 保存窗口几何信息（位置/大小变化时）
        attachGeometrySaver("main", stage);
    }

    /**
     * 注册一个命名窗口。
     */
    public void register(String name, Stage stage) {
        windows.put(name, stage);
        log.debug("Window registered: {} -> {}", name, stage.getTitle());
    }

    /**
     * 注销窗口。
     */
    public void unregister(String name) {
        windows.remove(name);
    }

    /**
     * 按名称查找窗口。
     */
    public Optional<Stage> get(String name) {
        return Optional.ofNullable(windows.get(name));
    }

    /** 主窗口。 */
    public Optional<Stage> primary() {
        return Optional.ofNullable(primaryStage);
    }

    /** 所有已注册窗口。 */
    public Map<String, Stage> all() {
        return Map.copyOf(windows);
    }

    /**
     * 切换指定窗口的置顶状态。
     * @return 切换后的置顶状态
     */
    public boolean toggleAlwaysOnTop(String name) {
        var stage = windows.get(name);
        if (stage == null) return false;
        var newState = !stage.isAlwaysOnTop();
        stage.setAlwaysOnTop(newState);
        prefs.putBoolean(name + ".alwaysOnTop", newState);
        log.debug("Window '{}' alwaysOnTop: {}", name, newState);
        return newState;
    }

    /**
     * 设置窗口透明度（0.1 ~ 1.0）。
     */
    public void setOpacity(String name, double opacity) {
        var stage = windows.get(name);
        if (stage == null) return;
        var clamped = Math.max(0.1, Math.min(1.0, opacity));
        stage.setOpacity(clamped);
        prefs.putDouble(name + ".opacity", clamped);
    }

    /**
     * 获取窗口透明度。
     */
    public double getOpacity(String name) {
        var stage = windows.get(name);
        return stage != null ? stage.getOpacity() : 1.0;
    }

    /**
     * 将窗口居中到屏幕。
     */
    public void centerOnScreen(String name) {
        var stage = windows.get(name);
        if (stage == null) return;
        Platform.runLater(() -> {
            stage.centerOnScreen();
        });
    }

    /**
     * 显示指定窗口。
     */
    public void show(String name) {
        var stage = windows.get(name);
        if (stage != null) {
            Platform.runLater(() -> {
                stage.show();
                stage.toFront();
            });
        }
    }

    /**
     * 隐藏指定窗口。
     */
    public void hide(String name) {
        var stage = windows.get(name);
        if (stage != null) {
            Platform.runLater(stage::hide);
        }
    }

    /**
     * 关闭所有窗口（应用退出时调用）。
     */
    public void closeAll() {
        for (var stage : windows.values()) {
            Platform.runLater(stage::close);
        }
        windows.clear();
    }

    // ---- 窗口几何持久化 ----

    /**
     * 从 Preferences 恢复窗口位置和大小。
     */
    private void restoreWindowGeometry(String name, Stage stage) {
        var x = prefs.getDouble(name + ".x", -1);
        var y = prefs.getDouble(name + ".y", -1);
        var w = prefs.getDouble(name + ".width", 1000);
        var h = prefs.getDouble(name + ".height", 700);
        var maximized = prefs.getBoolean(name + ".maximized", false);
        var alwaysOnTop = prefs.getBoolean(name + ".alwaysOnTop", false);
        var opacity = prefs.getDouble(name + ".opacity", 1.0);

        if (x >= 0 && y >= 0) {
            stage.setX(x);
            stage.setY(y);
        }
        stage.setWidth(w);
        stage.setHeight(h);
        stage.setMaximized(maximized);
        stage.setAlwaysOnTop(alwaysOnTop);
        stage.setOpacity(opacity);
    }

    /**
     * 监听窗口位置/大小变化，自动持久化。
     */
    private void attachGeometrySaver(String name, Stage stage) {
        stage.xProperty().addListener((obs, o, n) -> prefs.putDouble(name + ".x", n.doubleValue()));
        stage.yProperty().addListener((obs, o, n) -> prefs.putDouble(name + ".y", n.doubleValue()));
        stage.widthProperty().addListener((obs, o, n) -> prefs.putDouble(name + ".width", n.doubleValue()));
        stage.heightProperty().addListener((obs, o, n) -> prefs.putDouble(name + ".height", n.doubleValue()));
        stage.maximizedProperty().addListener((obs, o, n) -> prefs.putBoolean(name + ".maximized", n));
    }

    /**
     * 获取窗口几何信息的可读描述。
     */
    public String geometryInfo(String name) {
        var stage = windows.get(name);
        if (stage == null) return "Window not found: " + name;
        return String.format("%s: pos(%.0f, %.0f) size(%.0f x %.0f) opacity=%.2f onTop=%s maximized=%s",
            name, stage.getX(), stage.getY(), stage.getWidth(), stage.getHeight(),
            stage.getOpacity(), stage.isAlwaysOnTop(), stage.isMaximized());
    }
}
