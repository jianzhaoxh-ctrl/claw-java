package com.openclaw.desktop.desktop;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;

/**
 * 桌面操作 — 与操作系统桌面交互。
 * 对应 OpenClaw 的 desktop 操作工具集。
 *
 * <p>支持：
 * <ul>
 *   <li>打开文件/URL</li>
 *   <li>剪贴板操作</li>
 *   <li>文件管理器</li>
 *   <li>屏幕截图（通过 JavaFX）</li>
 * </ul>
 */
public class DesktopOps {

    private static final Logger log = LoggerFactory.getLogger(DesktopOps.class);

    /** 打开 URL — 在默认浏览器中打开 */
    public boolean openUrl(String url) {
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().browse(new URI(url));
                log.info("Opened URL: {}", url);
                return true;
            }
            // Linux fallback
            return runShellCommand("xdg-open " + url);
        } catch (URISyntaxException | IOException e) {
            log.error("Failed to open URL: {} - {}", url, e.getMessage());
            return false;
        }
    }

    /** 打开文件 — 在默认应用中打开 */
    public boolean openFile(String path) {
        try {
            var file = new File(path);
            if (!file.exists()) {
                log.warn("File not found: {}", path);
                return false;
            }
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(file);
                log.info("Opened file: {}", path);
                return true;
            }
            return runShellCommand("xdg-open " + path);
        } catch (IOException e) {
            log.error("Failed to open file: {} - {}", path, e.getMessage());
            return false;
        }
    }

    /** 打开文件管理器 — 在目录位置打开 */
    public boolean openFileManager(String dirPath) {
        try {
            var dir = new File(dirPath);
            if (!dir.exists() || !dir.isDirectory()) {
                log.warn("Directory not found: {}", dirPath);
                return false;
            }
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(dir);
                log.info("Opened file manager: {}", dirPath);
                return true;
            }
            return runShellCommand("xdg-open " + dirPath);
        } catch (IOException e) {
            log.error("Failed to open file manager: {} - {}", dirPath, e.getMessage());
            return false;
        }
    }

    /** 获取剪贴板内容 */
    public String getClipboard() {
        var clipboardManager = new ClipboardManager();
        return clipboardManager.paste();
    }

    /** 设置剪贴板内容 */
    public boolean setClipboard(String content) {
        var clipboardManager = new ClipboardManager();
        clipboardManager.copy(content);
        return true;
    }

    /** 获取用户主目录 */
    public String getHomeDir() {
        return System.getProperty("user.home");
    }

    /** 获取临时目录 */
    public String getTempDir() {
        return System.getProperty("java.io.tmpdir");
    }

    /** 检查文件是否存在 */
    public boolean fileExists(String path) {
        return new File(path).exists();
    }

    /** 创建目录 */
    public boolean createDirectory(String path) {
        var dir = new File(path);
        if (dir.exists()) return true;
        return dir.mkdirs();
    }

    private boolean runShellCommand(String command) {
        try {
            var osName = System.getProperty("os.name").toLowerCase();
            var processBuilder = new ProcessBuilder();
            if (osName.contains("win")) {
                processBuilder.command("cmd", "/c", command);
            } else if (osName.contains("mac")) {
                processBuilder.command("open", command.split(" ")[1]);
            } else {
                processBuilder.command("sh", "-c", command);
            }
            processBuilder.start();
            return true;
        } catch (IOException e) {
            log.error("Shell command failed: {}", e.getMessage());
            return false;
        }
    }
}
