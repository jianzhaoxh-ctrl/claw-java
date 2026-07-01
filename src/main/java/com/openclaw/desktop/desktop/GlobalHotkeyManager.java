package com.openclaw.desktop.desktop;

import javafx.application.Platform;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * 全局快捷键管理器 — 注册系统级热键，唤起主窗口。
 *
 * <p>跨平台实现：
 * <ul>
 *   <li><b>Windows</b>：通过 PowerShell + .NET Win32 API（RegisterHotKey）注册热键，
 *       不依赖 JNA，使用 PowerShell 后台进程监听 WM_HOTKEY 消息</li>
 *   <li><b>Linux</b>：使用 {@code xdotool} 或 {@code xbindkeys} 模拟（信创环境通用）</li>
 *   <li><b>macOS</b>：使用 {@code osascript} 模拟（受限）</li>
 * </ul>
 *
 * <p>由于纯 Java 无法直接注册系统级热键，本实现采用「外部命令轮询」策略：
 * 启动一个后台进程监听热键事件，触发时回调主窗口。该方案无需 JNA 依赖，
 * 兼容 Windows / 统信 UOS / 麒麟 OS。
 *
 * <p>若外部命令不可用，回退到 JavaFX 应用内快捷键（仅窗口聚焦时生效）。
 */
public class GlobalHotkeyManager {

    private static final Logger log = LoggerFactory.getLogger(GlobalHotkeyManager.class);

    private final Stage primaryStage;
    private final Consumer<String> hotkeyHandler;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private Process hotkeyProcess;
    private Thread monitorThread;
    private final Map<String, String> registeredKeys = new HashMap<>();

    /** 修饰键常量。 */
    public static final int MOD_ALT = 0x0001;
    public static final int MOD_CONTROL = 0x0002;
    public static final int MOD_SHIFT = 0x0004;
    public static final int MOD_WIN = 0x0008;
    public static final int MOD_NOREPEAT = 0x4000;

    public GlobalHotkeyManager(Stage primaryStage, Consumer<String> hotkeyHandler) {
        this.primaryStage = primaryStage;
        this.hotkeyHandler = hotkeyHandler;
    }

    /**
     * 注册并启动全局热键监听。
     * 默认热键：Ctrl+Alt+Space（唤起窗口）。
     * @return 是否成功启动
     */
    public boolean start() {
        return start("Ctrl+Alt+Space");
    }

    /**
     * 注册并启动全局热键监听。
     * @param hotkeyExpr 热键表达式，如 "Ctrl+Alt+Space"、"Ctrl+Shift+D"
     * @return 是否成功启动
     */
    public boolean start(String hotkeyExpr) {
        if (running.get()) {
            log.warn("GlobalHotkeyManager already running");
            return true;
        }

        var os = System.getProperty("os.name", "").toLowerCase();
        boolean started = false;
        if (os.contains("win")) {
            started = startWindowsHotkey(hotkeyExpr);
        } else if (os.contains("mac")) {
            started = startMacHotkey(hotkeyExpr);
        } else {
            started = startLinuxHotkey(hotkeyExpr);
        }

        if (!started) {
            log.warn("Global hotkey unavailable, falling back to JavaFX in-app shortcut");
            registerJavaFxShortcut(hotkeyExpr);
        }
        return started;
    }

    /**
     * Windows 热键 — 通过 PowerShell 脚本注册 RegisterHotKey 并监听。
     */
    private boolean startWindowsHotkey(String hotkeyExpr) {
        var winExpr = toWindowsExpr(hotkeyExpr);
        var script = """
            Add-Type -TypeDefinition @'
            using System;
            using System.Runtime.InteropServices;
            public class HotkeyHelper {
                [DllImport("user32.dll")]
                public static extern bool RegisterHotKey(IntPtr hWnd, int id, uint fsModifiers, uint vk);
                [DllImport("user32.dll")]
                public static extern bool UnregisterHotKey(IntPtr hWnd, int id);
                [DllImport("user32.dll")]
                public static extern bool PeekMessage(out System.Runtime.InteropServices.COMTypes.MSG msg, IntPtr hWnd, uint filterMin, uint filterMax, uint remove);
            }
'@
            $mods = %s
            $vk = %s
            $registered = [HotkeyHelper]::RegisterHotKey([IntPtr]::Zero, 1, $mods, $vk)
            if (-not $registered) {
                Write-Output "REGISTER_FAILED"
                exit 1
            }
            Write-Output "REGISTERED"
            $msg = New-Object System.Runtime.InteropServices.COMTypes.MSG
            while ($true) {
                if ([HotkeyHelper]::PeekMessage([ref]$msg, [IntPtr]::Zero, 0x0312, 0x0312, 1)) {
                    Write-Output "HOTKEY_TRIGGERED"
                    [System.Console]::Out.Flush()
                }
                Start-Sleep -Milliseconds 100
            }
            """.formatted(winExpr.modifiers, winExpr.vk);

        try {
            var pb = new ProcessBuilder("powershell", "-NoProfile", "-NonInteractive", "-Command", script);
            pb.redirectErrorStream(true);
            hotkeyProcess = pb.start();
            running.set(true);

            monitorThread = Thread.ofVirtual().name("hotkey-monitor").start(() -> {
                try (var reader = new java.io.BufferedReader(new java.io.InputStreamReader(hotkeyProcess.getInputStream()))) {
                    String line;
                    while (running.get() && (line = reader.readLine()) != null) {
                        if (line.contains("REGISTER_FAILED")) {
                            log.warn("Windows hotkey register failed (may be occupied): {}", hotkeyExpr);
                            running.set(false);
                            return;
                        }
                        if (line.contains("REGISTERED")) {
                            log.info("Windows global hotkey registered: {}", hotkeyExpr);
                        }
                        if (line.contains("HOTKEY_TRIGGERED")) {
                            log.debug("Hotkey triggered: {}", hotkeyExpr);
                            Platform.runLater(() -> {
                                if (hotkeyHandler != null) {
                                    hotkeyHandler.accept(hotkeyExpr);
                                } else {
                                    // 默认行为：显示/聚焦窗口
                                    primaryStage.show();
                                    primaryStage.setIconified(false);
                                    primaryStage.toFront();
                                    primaryStage.requestFocus();
                                }
                            });
                        }
                    }
                } catch (IOException e) {
                    if (running.get()) {
                        log.warn("Hotkey monitor IO error: {}", e.getMessage());
                    }
                } finally {
                    running.set(false);
                }
            });
            registeredKeys.put(hotkeyExpr, "windows");
            return true;
        } catch (IOException e) {
            log.warn("Failed to start Windows hotkey: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Linux 热键 — 通过 xbindkeys（若可用）。
     */
    private boolean startLinuxHotkey(String hotkeyExpr) {
        if (!isCommandAvailable("xbindkeys")) {
            log.info("xbindkeys not available, hotkey disabled");
            return false;
        }
        var cmd = toXbindkeysConfig(hotkeyExpr);
        try {
            var pb = new ProcessBuilder("xbindkeys", "-n", "-e", cmd);
            pb.redirectErrorStream(true);
            hotkeyProcess = pb.start();
            running.set(true);
            monitorThread = Thread.ofVirtual().name("hotkey-monitor").start(() -> {
                try {
                    hotkeyProcess.waitFor();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    running.set(false);
                }
            });
            registeredKeys.put(hotkeyExpr, "linux");
            log.info("Linux global hotkey registered via xbindkeys: {}", hotkeyExpr);
            return true;
        } catch (IOException e) {
            log.warn("Failed to start xbindkeys: {}", e.getMessage());
            return false;
        }
    }

    private boolean startMacHotkey(String hotkeyExpr) {
        // macOS 全局热键需要 Accessibility 权限，osascript 受限
        log.info("macOS global hotkey requires Accessibility permission, skipping");
        return false;
    }

    /**
     * 回退方案：注册 JavaFX 应用内快捷键（仅窗口聚焦时生效）。
     */
    private void registerJavaFxShortcut(String hotkeyExpr) {
        if (primaryStage == null || primaryStage.getScene() == null) {
            log.warn("Cannot register JavaFX shortcut: scene not ready");
            return;
        }
        var keyCode = parseKeyCode(hotkeyExpr);
        if (keyCode == null) return;
        var accelerator = new javafx.scene.input.KeyCodeCombination(
            keyCode,
            parseModifier(hotkeyExpr, "Ctrl"),
            parseModifier(hotkeyExpr, "Shift"),
            parseModifier(hotkeyExpr, "Alt")
        );
        primaryStage.getScene().getAccelerators().put(accelerator, () -> {
            log.debug("In-app hotkey triggered: {}", hotkeyExpr);
            if (hotkeyHandler != null) hotkeyHandler.accept(hotkeyExpr);
        });
        log.info("Registered JavaFX in-app shortcut: {}", hotkeyExpr);
    }

    /**
     * 停止热键监听。
     */
    public void stop() {
        running.set(false);
        if (hotkeyProcess != null && hotkeyProcess.isAlive()) {
            hotkeyProcess.destroy();
            try {
                if (!hotkeyProcess.waitFor(2, java.util.concurrent.TimeUnit.SECONDS)) {
                    hotkeyProcess.destroyForcibly();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                hotkeyProcess.destroyForcibly();
            }
        }
        if (monitorThread != null) {
            monitorThread.interrupt();
            monitorThread = null;
        }
        registeredKeys.clear();
        log.info("GlobalHotkeyManager stopped");
    }

    public boolean isRunning() {
        return running.get();
    }

    public Map<String, String> registeredKeys() {
        return Map.copyOf(registeredKeys);
    }

    // ---- 表达式解析 ----

    private record WinExpr(int modifiers, int vk) {}

    private WinExpr toWindowsExpr(String expr) {
        int mods = 0;
        var parts = expr.split("\\+");
        String key = "SPACE";
        for (var p : parts) {
            var t = p.trim().toLowerCase();
            switch (t) {
                case "ctrl", "control" -> mods |= MOD_CONTROL;
                case "alt" -> mods |= MOD_ALT;
                case "shift" -> mods |= MOD_SHIFT;
                case "win" -> mods |= MOD_WIN;
                default -> key = t.toUpperCase();
            }
        }
        mods |= MOD_NOREPEAT;
        var vk = vkCode(key);
        return new WinExpr(mods, vk);
    }

    private int vkCode(String key) {
        return switch (key) {
            case "SPACE" -> 0x20;
            case "D" -> 0x44;
            case "C" -> 0x43;
            case "V" -> 0x56;
            case "F1" -> 0x70;
            case "F2" -> 0x71;
            case "F3" -> 0x72;
            case "F4" -> 0x73;
            case "F5" -> 0x74;
            case "F6" -> 0x75;
            case "F7" -> 0x76;
            case "F8" -> 0x77;
            case "F9" -> 0x78;
            case "F10" -> 0x79;
            case "F11" -> 0x7A;
            case "F12" -> 0x7B;
            default -> 0x20;
        };
    }

    private String toXbindkeysConfig(String expr) {
        // xbindkeys 配置格式："xdotool key space\n  Control+Alt+space"
        var parts = expr.split("\\+");
        var key = parts[parts.length - 1].trim().toLowerCase();
        var mods = new StringBuilder();
        for (int i = 0; i < parts.length - 1; i++) {
            if (i > 0) mods.append("+");
            mods.append(parts[i].trim());
        }
        mods.append("+").append(key);
        return "xdotool key --clearmodifiers " + key + "\n  " + mods;
    }

    private javafx.scene.input.KeyCode parseKeyCode(String expr) {
        var parts = expr.split("\\+");
        var key = parts[parts.length - 1].trim().toUpperCase();
        try {
            return javafx.scene.input.KeyCode.valueOf(key);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private javafx.scene.input.KeyCombination.Modifier parseModifier(String expr, String mod) {
        var lower = expr.toLowerCase();
        if (lower.contains(mod.toLowerCase())) {
            return switch (mod) {
                case "Ctrl" -> javafx.scene.input.KeyCombination.CONTROL_DOWN;
                case "Shift" -> javafx.scene.input.KeyCombination.SHIFT_DOWN;
                case "Alt" -> javafx.scene.input.KeyCombination.ALT_DOWN;
                case "Win" -> javafx.scene.input.KeyCombination.META_DOWN;
                default -> javafx.scene.input.KeyCombination.SHORTCUT_DOWN;
            };
        }
        return javafx.scene.input.KeyCombination.ALT_ANY;
    }

    private boolean isCommandAvailable(String cmd) {
        try {
            var pb = new ProcessBuilder(cmd, "--help");
            pb.redirectErrorStream(true);
            var proc = pb.start();
            proc.waitFor();
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
