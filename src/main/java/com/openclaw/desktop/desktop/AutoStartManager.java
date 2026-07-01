package com.openclaw.desktop.desktop;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermission;
import java.util.HashSet;
import java.util.Set;

/**
 * 开机自启管理器 — 跨平台配置开机自动启动。
 *
 * <p>平台策略：
 * <ul>
 *   <li><b>Windows</b>：写入注册表 {@code HKCU\Software\Microsoft\Windows\CurrentVersion\Run}
 *       （通过 {@code reg add} 命令，无需管理员权限）</li>
 *   <li><b>Linux（统信 UOS / 麒麟 OS）</b>：在 {@code ~/.config/autostart/} 下创建
 *       {@code .desktop} 文件（XDG Autostart 标准）</li>
 *   <li><b>macOS</b>：在 {@code ~/Library/LaunchAgents/} 下创建 plist 文件</li>
 * </ul>
 */
public class AutoStartManager {

    private static final Logger log = LoggerFactory.getLogger(AutoStartManager.class);
    private static final String APP_NAME = "ClawDesktop";

    private final String appPath;
    private final String appName;
    private final String iconPath;
    private final boolean windows;
    private final boolean linux;
    private final boolean mac;

    public AutoStartManager(String appPath) {
        this(appPath, APP_NAME, null);
    }

    public AutoStartManager(String appPath, String appName, String iconPath) {
        this.appPath = appPath;
        this.appName = appName;
        this.iconPath = iconPath;
        var os = System.getProperty("os.name", "").toLowerCase();
        this.windows = os.contains("win");
        this.linux = os.contains("nix") || os.contains("nux") || os.contains("aix");
        this.mac = os.contains("mac");
    }

    /**
     * 启用开机自启。
     * @return 是否成功
     */
    public boolean enable() {
        if (windows) return enableWindows();
        if (mac) return enableMac();
        if (linux) return enableLinux();
        log.warn("Unknown OS, cannot enable autostart");
        return false;
    }

    /**
     * 禁用开机自启。
     * @return 是否成功
     */
    public boolean disable() {
        if (windows) return disableWindows();
        if (mac) return disableMac();
        if (linux) return disableLinux();
        return false;
    }

    /**
     * 查询当前是否已启用开机自启。
     */
    public boolean isEnabled() {
        if (windows) return isWindowsEnabled();
        if (mac) return isMacEnabled();
        if (linux) return isLinuxEnabled();
        return false;
    }

    // ---- Windows ----

    private boolean enableWindows() {
        var cmd = new String[]{"reg", "add",
            "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run",
            "/v", appName, "/t", "REG_SZ", "/d", "\"" + appPath + "\"", "/f"};
        try {
            var proc = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            return proc.waitFor() == 0;
        } catch (IOException | InterruptedException e) {
            log.warn("Failed to enable Windows autostart: {}", e.getMessage());
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            return false;
        }
    }

    private boolean disableWindows() {
        var cmd = new String[]{"reg", "delete",
            "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run",
            "/v", appName, "/f"};
        try {
            var proc = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            proc.waitFor();
            return true; // 即使键不存在也返回成功
        } catch (IOException | InterruptedException e) {
            log.warn("Failed to disable Windows autostart: {}", e.getMessage());
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            return false;
        }
    }

    private boolean isWindowsEnabled() {
        var cmd = new String[]{"reg", "query",
            "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run",
            "/v", appName};
        try {
            var proc = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            var out = new String(proc.getInputStream().readAllBytes());
            proc.waitFor();
            return out.contains(appName);
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            return false;
        }
    }

    // ---- Linux (XDG Autostart) ----

    private Path linuxDesktopFile() {
        return Paths.get(System.getProperty("user.home"), ".config", "autostart",
            appName.toLowerCase() + ".desktop");
    }

    private boolean enableLinux() {
        var desktopFile = linuxDesktopFile();
        try {
            Files.createDirectories(desktopFile.getParent());
        } catch (IOException e) {
            log.warn("Failed to create autostart dir: {}", e.getMessage());
            return false;
        }

        var iconLine = iconPath != null ? "Icon=" + iconPath : "Icon=dialog-information";
        var content = """
            [Desktop Entry]
            Type=Application
            Name=%s
            Comment=Personal AI Assistant
            Exec=%s
            %s
            Terminal=false
            Categories=Utility;Office;
            X-GNOME-Autostart-enabled=true
            X-UOS-Autostart-enabled=true
            """.formatted(appName, appPath, iconLine);

        try {
            Files.writeString(desktopFile, content);
            // 设置可执行权限
            var perms = new HashSet<PosixFilePermission>();
            perms.add(PosixFilePermission.OWNER_READ);
            perms.add(PosixFilePermission.OWNER_WRITE);
            perms.add(PosixFilePermission.OWNER_EXECUTE);
            perms.add(PosixFilePermission.GROUP_READ);
            perms.add(PosixFilePermission.OTHERS_READ);
            try {
                Files.setPosixFilePermissions(desktopFile, perms);
            } catch (UnsupportedOperationException e) {
                // 非 POSIX 文件系统（如 NTFS），忽略权限设置
            }
            log.info("Linux autostart enabled: {}", desktopFile);
            return true;
        } catch (IOException e) {
            log.warn("Failed to write desktop file: {}", e.getMessage());
            return false;
        }
    }

    private boolean disableLinux() {
        try {
            return Files.deleteIfExists(linuxDesktopFile());
        } catch (IOException e) {
            log.warn("Failed to delete desktop file: {}", e.getMessage());
            return false;
        }
    }

    private boolean isLinuxEnabled() {
        return Files.exists(linuxDesktopFile());
    }

    // ---- macOS (LaunchAgent) ----

    private Path macPlistFile() {
        return Paths.get(System.getProperty("user.home"), "Library", "LaunchAgents",
            "com.openclaw." + appName.toLowerCase() + ".plist");
    }

    private boolean enableMac() {
        var plist = macPlistFile();
        try {
            Files.createDirectories(plist.getParent());
        } catch (IOException e) {
            return false;
        }
        var content = """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
            <plist version="1.0">
            <dict>
                <key>Label</key>
                <string>com.openclaw.%s</string>
                <key>ProgramArguments</key>
                <array>
                    <string>%s</string>
                </array>
                <key>RunAtLoad</key>
                <true/>
                <key>KeepAlive</key>
                <false/>
            </dict>
            </plist>
            """.formatted(appName.toLowerCase(), appPath);
        try {
            Files.writeString(plist, content);
            log.info("macOS LaunchAgent enabled: {}", plist);
            return true;
        } catch (IOException e) {
            log.warn("Failed to write plist: {}", e.getMessage());
            return false;
        }
    }

    private boolean disableMac() {
        try {
            return Files.deleteIfExists(macPlistFile());
        } catch (IOException e) {
            return false;
        }
    }

    private boolean isMacEnabled() {
        return Files.exists(macPlistFile());
    }

    // ---- 查询 ----

    public String platform() {
        return windows ? "Windows" : mac ? "macOS" : linux ? "Linux" : "Unknown";
    }

    public String autostartLocation() {
        if (windows) return "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run\\" + appName;
        if (mac) return macPlistFile().toString();
        if (linux) return linuxDesktopFile().toString();
        return "N/A";
    }
}
