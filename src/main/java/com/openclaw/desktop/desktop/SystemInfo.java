package com.openclaw.desktop.desktop;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Locale;

/**
 * 系统信息 — 检测操作系统和硬件环境。
 * 对应 OpenClaw 的 doctor/platform 检测。
 *
 * <p>支持：
 * <ul>
 *   <li>操作系统检测（Windows/Linux/macOS/信创）</li>
 *   <li>JDK 版本</li>
 *   <li>系统架构（x86/ARM/龙芯）</li>
 *   <li>桌面环境检测</li>
 *   <li>磁盘空间</li>
 *   <li>网络状态</li>
 * </ul>
 */
public class SystemInfo {

    private static final Logger log = LoggerFactory.getLogger(SystemInfo.class);

    /** 操作系统类型 */
    public enum OSType {
        WINDOWS, LINUX, MACOS, UNKNOWN,
        // 信创操作系统
        UOS, KYLIN, DEEPIN
    }

    /** CPU 架构 */
    public enum ArchType {
        X86_64, ARM64, LOONGARCH64, UNKNOWN
    }

    /** 检测操作系统 */
    public OSType detectOS() {
        var osName = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        if (osName.contains("win")) return OSType.WINDOWS;
        if (osName.contains("mac")) return OSType.MACOS;
        if (osName.contains("linux")) {
            // 检测信创 OS
            if (isUOS()) return OSType.UOS;
            if (isKYLIN()) return OSType.KYLIN;
            if (isDeepin()) return OSType.DEEPIN;
            return OSType.LINUX;
        }
        return OSType.UNKNOWN;
    }

    /** 检测 CPU 架构 */
    public ArchType detectArch() {
        var arch = System.getProperty("os.arch").toLowerCase(Locale.ROOT);
        if (arch.contains("amd64") || arch.contains("x86_64")) return ArchType.X86_64;
        if (arch.contains("aarch64") || arch.contains("arm64")) return ArchType.ARM64;
        if (arch.contains("loongarch")) return ArchType.LOONGARCH64;
        return ArchType.UNKNOWN;
    }

    /** 检测 JDK 版本 */
    public String jdkVersion() {
        return System.getProperty("java.version");
    }

    /** 检测 JDK 供应商 */
    public String jdkVendor() {
        return System.getProperty("java.vendor");
    }

    /** 检测 JDK 主版本 */
    public int jdkMajorVersion() {
        var version = jdkVersion();
        try {
            if (version.startsWith("1.")) {
                return Integer.parseInt(version.substring(2, 3));
            }
            var parts = version.split("\\.");
            return Integer.parseInt(parts[0]);
        } catch (Exception e) {
            return -1;
        }
    }

    /** 检测桌面环境 */
    public String detectDesktopEnvironment() {
        var os = detectOS();
        if (os == OSType.WINDOWS) return "Windows Desktop";
        if (os == OSType.MACOS) return "macOS Aqua";

        // Linux 桌面环境
        var desktop = System.getenv("XDG_CURRENT_DESKTOP");
        if (desktop != null) return desktop;

        var session = System.getenv("DESKTOP_SESSION");
        if (session != null) return session;

        return "Unknown";
    }

    /** 磁盘可用空间（MB） */
    public long availableDiskSpaceMB(String path) {
        var file = new java.io.File(path);
        return file.getFreeSpace() / (1024 * 1024);
    }

    /** 总磁盘空间（MB） */
    public long totalDiskSpaceMB(String path) {
        var file = new java.io.File(path);
        return file.getTotalSpace() / (1024 * 1024);
    }

    /** 系统内存（近似，通过 Runtime） */
    public long maxMemoryMB() {
        return Runtime.getRuntime().maxMemory() / (1024 * 1024);
    }

    /** 是否支持 JavaFX */
    public boolean supportsJavaFX() {
        try {
            Class.forName("javafx.application.Application");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /** 是否支持系统托盘 */
    public boolean supportsSystemTray() {
        return java.awt.SystemTray.isSupported();
    }

    /** 网络可达性检查 */
    public boolean isHostReachable(String host, int timeoutMs) {
        try {
            return java.net.InetAddress.getByName(host).isReachable(timeoutMs);
        } catch (Exception e) {
            return false;
        }
    }

    /** 系统信息摘要 */
    public String summary() {
        return String.format("""
            ClawDesktop System Info:
            OS: %s (%s)
            Arch: %s
            JDK: %s (%s) v%d
            Desktop: %s
            Disk: %dMB free / %dMB total
            Memory: %dMB max
            JavaFX: %s
            SystemTray: %s
            """,
            detectOS(), System.getProperty("os.name"),
            detectArch(),
            jdkVersion(), jdkVendor(), jdkMajorVersion(),
            detectDesktopEnvironment(),
            availableDiskSpaceMB(System.getProperty("user.home")),
            totalDiskSpaceMB(System.getProperty("user.home")),
            maxMemoryMB(),
            supportsJavaFX() ? "Yes" : "No",
            supportsSystemTray() ? "Yes" : "No"
        );
    }

    // ========== 信创检测 ==========

    private boolean isUOS() {
        // 统信 UOS 检测
        return checkFileContent("/etc/os-release", "UOS") ||
               checkFileContent("/etc/os-release", "UnionTech");
    }

    private boolean isKYLIN() {
        // 麒麟 OS 检测
        return checkFileContent("/etc/os-release", "Kylin") ||
               checkFileContent("/etc/os-release", "kylin");
    }

    private boolean isDeepin() {
        // Deepin 检测
        return checkFileContent("/etc/os-release", "Deepin");
    }

    private boolean checkFileContent(String filePath, String keyword) {
        try {
            var file = new java.io.File(filePath);
            if (!file.exists()) return false;
            var reader = new BufferedReader(new InputStreamReader(new java.io.FileInputStream(file)));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains(keyword)) return true;
            }
            reader.close();
        } catch (Exception e) {
            // 文件不存在或不可读
        }
        return false;
    }
}
