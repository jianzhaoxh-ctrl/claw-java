package com.openclaw.desktop.desktop;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * 桌面通知管理器 — 跨平台系统级通知。
 *
 * <p>按平台选择最佳通知方式：
 * <ul>
 *   <li><b>Windows</b>：优先使用 PowerShell 调用 BurntToast 或 Windows Runtime ToastNotification；
 *       不可用时回退到 AWT TrayIcon 通知</li>
 *   <li><b>Linux</b>：使用 {@code notify-send}（libnotify），常见于统信 UOS / 麒麟 OS；
 *       不可用时回退到 AWT TrayIcon</li>
 *   <li><b>macOS</b>：使用 {@code osascript} 显示通知；
 *       不可用时回退到 AWT TrayIcon</li>
 * </ul>
 *
 * <p>支持通知优先级、点击回调（通过通知 ID）、延迟与重复调度。
 */
public class NotificationManager {

    private static final Logger log = LoggerFactory.getLogger(NotificationManager.class);

    private final SystemTrayManager trayManager;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1, r -> {
        var t = new Thread(r, "notification-scheduler");
        t.setDaemon(true);
        return t;
    });

    private final boolean windows;
    private final boolean linux;
    private final boolean mac;
    private final boolean notifySendAvailable;
    private final boolean powershellAvailable;
    private Consumer<String> clickHandler;
    private long nextId = 1;

    public NotificationManager(SystemTrayManager trayManager) {
        this.trayManager = trayManager;
        var os = System.getProperty("os.name", "").toLowerCase();
        this.windows = os.contains("win");
        this.linux = os.contains("nix") || os.contains("nux") || os.contains("aix");
        this.mac = os.contains("mac");
        this.notifySendAvailable = linux && isCommandAvailable("notify-send");
        this.powershellAvailable = windows && isCommandAvailable("powershell");
        log.info("NotificationManager initialized: os={} notify-send={} powershell={}",
            os, notifySendAvailable, powershellAvailable);
    }

    /**
     * 显示即时通知。
     * @return 通知 ID（可用于后续点击回调匹配），失败返回 -1
     */
    public long notify(String title, String message) {
        return notify(title, message, Priority.NORMAL);
    }

    /**
     * 显示指定优先级的通知。
     */
    public long notify(String title, String message, Priority priority) {
        var id = nextId++;
        boolean sent = false;

        if (windows) {
            sent = sendWindowsToast(title, message, priority);
        } else if (mac) {
            sent = sendMacNotification(title, message);
        } else if (linux) {
            sent = sendLinuxNotify(title, message, priority);
        }

        if (!sent) {
            // 回退到 AWT 托盘通知
            if (trayManager != null && trayManager.isTraySupported()) {
                switch (priority) {
                    case URGENT, HIGH -> trayManager.showTrayWarning(title, message);
                    case ERROR -> trayManager.showTrayError(title, message);
                    default -> trayManager.showTrayMessage(title, message);
                }
                sent = true;
            }
        }

        if (!sent) {
            log.warn("No notification mechanism available, logging only: [{}] {}", title, message);
        }
        return sent ? id : -1;
    }

    /** 延迟通知。 */
    public long schedule(String title, String message, long delayMs) {
        var id = nextId++;
        scheduler.schedule(() -> notify(title, message), delayMs, TimeUnit.MILLISECONDS);
        return id;
    }

    /** 重复通知（首次延迟 periodMs，之后每 periodMs 一次）。 */
    public long repeat(String title, String message, long periodMs) {
        var id = nextId++;
        scheduler.scheduleAtFixedRate(() -> notify(title, message), periodMs, periodMs, TimeUnit.MILLISECONDS);
        return id;
    }

    /** 设置点击回调（参数为通知 ID）。 */
    public void setClickHandler(Consumer<String> handler) {
        this.clickHandler = handler;
    }

    /** 通知优先级。 */
    public enum Priority {
        LOW, NORMAL, HIGH, URGENT, ERROR
    }

    // ---- 平台特定实现 ----

    /**
     * Windows Toast 通知 — 通过 PowerShell 调用 Windows Runtime ToastNotificationManager。
     * 需要安装 BurntToast 模块或使用内置 Windows Runtime。
     */
    private boolean sendWindowsToast(String title, String message, Priority priority) {
        if (!powershellAvailable) return false;
        var urgency = switch (priority) {
            case URGENT, ERROR -> "Urgent";
            case HIGH -> "Urgent";
            default -> "Default";
        };
        // 转义 PowerShell 字符串中的特殊字符
        var safeTitle = escapePowerShell(title);
        var safeMsg = escapePowerShell(message);
        var script = """
            $ErrorActionPreference = 'SilentlyContinue'
            try {
                [Windows.UI.Notifications.ToastNotificationManager, Windows.UI.Notifications, ContentType = WindowsRuntime] | Out-Null
                $template = [Windows.UI.Notifications.ToastNotificationManager]::GetTemplateContent([Windows.UI.Notifications.ToastTemplateType]::ToastText02)
                $textNodes = $template.GetElementsByTagName('text')
                $textNodes.Item(0).AppendChild($template.CreateTextNode('%s')) | Out-Null
                $textNodes.Item(1).AppendChild($template.CreateTextNode('%s')) | Out-Null
                $toast = [Windows.UI.Notifications.ToastNotification]::new($template)
                $notifier = [Windows.UI.Notifications.ToastNotificationManager]::CreateToastNotifier('ClawDesktop')
                $notifier.Show($toast)
            } catch {
                Write-Output 'FALLBACK'
            }
            """.formatted(safeTitle, safeMsg);

        try {
            var pb = new ProcessBuilder("powershell", "-NoProfile", "-NonInteractive", "-Command", script);
            pb.redirectErrorStream(true);
            var proc = pb.start();
            var out = new String(proc.getInputStream().readAllBytes()).trim();
            var exitCode = proc.waitFor();
            if (exitCode == 0 && !out.contains("FALLBACK")) {
                log.debug("Windows Toast sent: {}", title);
                return true;
            }
            log.debug("Windows Toast fallback triggered");
            return false;
        } catch (IOException | InterruptedException e) {
            log.debug("Windows Toast failed: {}", e.getMessage());
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * Linux notify-send 通知。
     */
    private boolean sendLinuxNotify(String title, String message, Priority priority) {
        if (!notifySendAvailable) return false;
        var urgency = switch (priority) {
            case URGENT, ERROR -> "critical";
            case HIGH -> "normal";
            default -> "normal";
        };
        try {
            var pb = new ProcessBuilder("notify-send",
                "--urgency=" + urgency,
                "--app-name=ClawDesktop",
                "--icon=dialog-information",
                title, message);
            pb.redirectErrorStream(true);
            var proc = pb.start();
            var exitCode = proc.waitFor();
            if (exitCode == 0) {
                log.debug("Linux notify-send: {}", title);
                return true;
            }
            return false;
        } catch (IOException | InterruptedException e) {
            log.debug("notify-send failed: {}", e.getMessage());
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * macOS osascript 通知。
     */
    private boolean sendMacNotification(String title, String message) {
        try {
            var script = "display notification \"" + escapeAppleScript(message)
                + "\" with title \"" + escapeAppleScript(title) + "\"";
            var pb = new ProcessBuilder("osascript", "-e", script);
            pb.redirectErrorStream(true);
            var proc = pb.start();
            var exitCode = proc.waitFor();
            return exitCode == 0;
        } catch (IOException | InterruptedException e) {
            log.debug("osascript failed: {}", e.getMessage());
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            return false;
        }
    }

    // ---- 辅助 ----

    private boolean isCommandAvailable(String cmd) {
        try {
            var pb = new ProcessBuilder(cmd, "--version");
            pb.redirectErrorStream(true);
            var proc = pb.start();
            return proc.waitFor() == 0 || proc.exitValue() == 0;
        } catch (Exception e) {
            // Windows powershell --version 可能返回非 0 但仍可用，再试 /?
            try {
                var pb = new ProcessBuilder(cmd, "/?");
                pb.redirectErrorStream(true);
                var proc = pb.start();
                proc.waitFor();
                return true;
            } catch (Exception e2) {
                return false;
            }
        }
    }

    private String escapePowerShell(String s) {
        return s.replace("'", "''").replace("\"", "`\"");
    }

    private String escapeAppleScript(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    public void shutdown() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(3, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            scheduler.shutdownNow();
        }
        log.info("NotificationManager shutdown");
    }
}
