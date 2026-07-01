package com.openclaw.desktop.desktop;

import javafx.application.Platform;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * 系统托盘集成 — 跨平台托盘图标、右键菜单、最小化到托盘。
 *
 * <p>对应 OpenClaw 的 desktop 集成。特性：
 * <ul>
 *   <li>右键菜单：显示窗口 / 隐藏到托盘 / 设置 / 关于 / 退出</li>
 *   <li>双击托盘图标切换窗口可见性</li>
 *   <li>关闭主窗口时最小化到托盘而非退出（可配置）</li>
 *   <li>托盘图标闪烁（用于提醒）</li>
 *   <li>动态托盘提示文本</li>
 *   <li>跨平台：Windows / Linux / macOS（AWT SystemTray）</li>
 * </ul>
 */
public class SystemTrayManager {

    private static final Logger log = LoggerFactory.getLogger(SystemTrayManager.class);

    private Stage primaryStage;
    private TrayIcon trayIcon;
    private boolean traySupported;
    private final AtomicBoolean flashing = new AtomicBoolean(false);
    private Thread flashThread;
    private java.awt.Image normalIcon;
    private java.awt.Image emptyIcon;

    /** 关闭窗口时的行为：minimize（最小化到托盘）或 exit（直接退出）。 */
    private CloseBehavior closeBehavior = CloseBehavior.MINIMIZE_TO_TRAY;

    /** 菜单项回调（由 DesktopApplication 注入）。 */
    private Consumer<String> menuActionHandler;

    public enum CloseBehavior {
        MINIMIZE_TO_TRAY,
        EXIT
    }

    public void init(Stage stage) {
        this.primaryStage = stage;

        if (!SystemTray.isSupported()) {
            log.warn("System tray not supported on this platform");
            return;
        }

        traySupported = true;
        var tray = SystemTray.getSystemTray();
        var popup = buildPopupMenu();

        normalIcon = loadIcon();
        emptyIcon = createEmptyIcon();

        trayIcon = new TrayIcon(normalIcon, "ClawDesktop", popup);
        trayIcon.setImageAutoSize(true);
        trayIcon.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    // 双击切换可见性
                    Platform.runLater(() -> toggleWindow());
                } else if (e.getClickCount() == 1 && e.getButton() == MouseEvent.BUTTON1) {
                    // 单击显示窗口
                    Platform.runLater(() -> showWindow());
                }
            }
        });

        try {
            tray.add(trayIcon);
            log.info("System tray icon installed");
        } catch (AWTException e) {
            log.error("Failed to add tray icon: {}", e.getMessage());
            traySupported = false;
            return;
        }

        // 关闭窗口时最小化到托盘
        primaryStage.setOnCloseRequest(e -> {
            if (traySupported && closeBehavior == CloseBehavior.MINIMIZE_TO_TRAY) {
                e.consume();
                primaryStage.hide();
                showTrayMessage("ClawDesktop", "正在后台运行，双击托盘图标恢复");
            }
        });

        // 最小化时正常最小化到任务栏（不隐藏到托盘）
        // 关闭按钮才隐藏到托盘
    }

    private PopupMenu buildPopupMenu() {
        var popup = new PopupMenu();

        var showItem = new MenuItem("显示窗口");
        showItem.addActionListener(e -> Platform.runLater(this::showWindow));

        var hideItem = new MenuItem("隐藏到托盘");
        hideItem.addActionListener(e -> Platform.runLater(() -> primaryStage.hide()));

        var settingsItem = new MenuItem("设置...");
        settingsItem.addActionListener(e -> invokeMenuAction("settings"));

        popup.add(showItem);
        popup.add(hideItem);
        popup.addSeparator();
        popup.add(settingsItem);

        var aboutItem = new MenuItem("关于 ClawDesktop");
        aboutItem.addActionListener(e -> invokeMenuAction("about"));

        popup.add(aboutItem);
        popup.addSeparator();

        var exitItem = new MenuItem("退出");
        exitItem.addActionListener(e -> {
            stopFlashing();
            Platform.runLater(() -> {
                primaryStage.close();
                // 移除托盘图标
                if (trayIcon != null) {
                    SystemTray.getSystemTray().remove(trayIcon);
                }
                System.exit(0);
            });
        });

        return popup;
    }

    private void invokeMenuAction(String action) {
        if (menuActionHandler != null) {
            menuActionHandler.accept(action);
        } else {
            log.debug("Menu action '{}' has no handler", action);
        }
    }

    /** 加载图标资源，找不到则生成默认图标。 */
    private java.awt.Image loadIcon() {
        var res = getClass().getResource("/icon.png");
        if (res != null) {
            var img = Toolkit.getDefaultToolkit().createImage(res);
            // 等待图片加载完成
            var tracker = new MediaTracker(new Label());
            tracker.addImage(img, 0);
            try { tracker.waitForID(0); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            if (tracker.isErrorID(0)) {
                log.warn("Tray icon load failed, using default");
                return createDefaultIcon();
            }
            return img;
        }
        return createDefaultIcon();
    }

    /** 显示主窗口并置前。 */
    public void showWindow() {
        if (primaryStage == null) return;
        // Windows 下 hide() 后需要先 setIconified(true) 再 show() 再 setIconified(false)
        // 否则窗口可能不正常恢复
        Platform.runLater(() -> {
            primaryStage.setIconified(true);
            primaryStage.show();
            primaryStage.setIconified(false);
            primaryStage.toFront();
            primaryStage.requestFocus();
            stopFlashing();
        });
    }

    /** 隐藏主窗口到托盘。 */
    public void hideWindow() {
        if (primaryStage != null) primaryStage.hide();
    }

    /** 切换窗口可见性。 */
    public void toggleWindow() {
        if (primaryStage == null) return;
        if (primaryStage.isShowing()) {
            hideWindow();
        } else {
            showWindow();
        }
    }

    public void showTrayMessage(String title, String message) {
        if (traySupported && trayIcon != null) {
            trayIcon.displayMessage(title, message, TrayIcon.MessageType.INFO);
        }
    }

    public void showTrayWarning(String title, String message) {
        if (traySupported && trayIcon != null) {
            trayIcon.displayMessage(title, message, TrayIcon.MessageType.WARNING);
        }
    }

    public void showTrayError(String title, String message) {
        if (traySupported && trayIcon != null) {
            trayIcon.displayMessage(title, message, TrayIcon.MessageType.ERROR);
        }
    }

    /** 设置托盘提示文本。 */
    public void setTooltip(String tooltip) {
        if (trayIcon != null) {
            trayIcon.setToolTip(tooltip);
        }
    }

    /**
     * 托盘图标闪烁（用于提醒）。
     * @param durationMs 闪烁持续时间（毫秒），0 表示持续闪烁直到 stopFlashing
     */
    public void startFlashing(long durationMs) {
        if (!traySupported || trayIcon == null) return;
        if (!flashing.compareAndSet(false, true)) return;

        flashThread = Thread.ofVirtual().name("tray-flash").start(() -> {
            try {
                long start = System.currentTimeMillis();
                boolean toggle = false;
                while (flashing.get()) {
                    toggle = !toggle;
                    trayIcon.setImage(toggle ? emptyIcon : normalIcon);
                    Thread.sleep(500);
                    if (durationMs > 0 && System.currentTimeMillis() - start > durationMs) {
                        break;
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                trayIcon.setImage(normalIcon);
                flashing.set(false);
            }
        });
    }

    /** 停止闪烁。 */
    public void stopFlashing() {
        flashing.set(false);
        if (flashThread != null) {
            flashThread.interrupt();
            flashThread = null;
        }
        if (trayIcon != null && normalIcon != null) {
            trayIcon.setImage(normalIcon);
        }
    }

    public boolean isTraySupported() {
        return traySupported;
    }

    public void setCloseBehavior(CloseBehavior behavior) {
        this.closeBehavior = behavior;
    }

    public CloseBehavior getCloseBehavior() {
        return closeBehavior;
    }

    public void setMenuActionHandler(Consumer<String> handler) {
        this.menuActionHandler = handler;
    }

    /** 销毁托盘图标。 */
    public void dispose() {
        stopFlashing();
        if (trayIcon != null && SystemTray.isSupported()) {
            try {
                SystemTray.getSystemTray().remove(trayIcon);
            } catch (Exception e) {
                log.debug("Error removing tray icon: {}", e.getMessage());
            }
        }
    }

    // ---- 图标生成 ----

    private java.awt.Image createDefaultIcon() {
        var bi = new java.awt.image.BufferedImage(16, 16, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        var g = bi.createGraphics();
        g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(137, 180, 250));
        g.fillRoundRect(1, 1, 14, 14, 4, 4);
        g.setColor(Color.WHITE);
        g.setFont(new Font("Arial", Font.BOLD, 11));
        var fm = g.getFontMetrics();
        g.drawString("C", (16 - fm.stringWidth("C")) / 2, 12);
        g.dispose();
        return bi;
    }

    private java.awt.Image createEmptyIcon() {
        var bi = new java.awt.image.BufferedImage(16, 16, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        return bi;
    }
}
