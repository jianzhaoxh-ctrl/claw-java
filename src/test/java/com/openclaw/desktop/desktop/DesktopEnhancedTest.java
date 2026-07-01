package com.openclaw.desktop.desktop;

import com.openclaw.desktop.desktop.SystemInfo;
import com.openclaw.desktop.desktop.SystemInfo.OSType;
import com.openclaw.desktop.desktop.SystemInfo.ArchType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DesktopEnhancedTest {

    private SystemInfo sysInfo;

    @BeforeEach
    void setUp() {
        sysInfo = new SystemInfo();
    }

    @Test
    void testSystemInfoDetectOS() {
        var os = sysInfo.detectOS();
        assertEquals(OSType.WINDOWS, os);
    }

    @Test
    void testSystemInfoDetectArch() {
        var arch = sysInfo.detectArch();
        assertTrue(arch == ArchType.X86_64 || arch == ArchType.ARM64 || arch == ArchType.LOONGARCH64);
    }

    @Test
    void testSystemInfoJdkVersion() {
        var version = sysInfo.jdkVersion();
        assertNotNull(version);
        assertTrue(version.startsWith("21"));
    }

    @Test
    void testSystemInfoJdkMajorVersion() {
        var major = sysInfo.jdkMajorVersion();
        assertEquals(21, major);
    }

    @Test
    void testSystemInfoJdkVendor() {
        var vendor = sysInfo.jdkVendor();
        assertNotNull(vendor);
    }

    @Test
    void testSystemInfoDiskSpace() {
        var home = System.getProperty("user.home");
        var freeSpace = sysInfo.availableDiskSpaceMB(home);
        assertTrue(freeSpace > 0);
        var totalSpace = sysInfo.totalDiskSpaceMB(home);
        assertTrue(totalSpace > 0);
        assertTrue(totalSpace >= freeSpace);
    }

    @Test
    void testSystemInfoMaxMemory() {
        var maxMem = sysInfo.maxMemoryMB();
        assertTrue(maxMem > 0);
    }

    @Test
    void testSystemInfoSummary() {
        var summary = sysInfo.summary();
        assertNotNull(summary);
        assertTrue(summary.contains("ClawDesktop"));
        assertTrue(summary.contains("JDK"));
    }

    @Test
    void testOSTypeValues() {
        var types = OSType.values();
        assertTrue(types.length >= 4);
    }

    @Test
    void testArchTypeValues() {
        var types = ArchType.values();
        assertTrue(types.length >= 3);
    }

    @Test
    void testDesktopOpsHomeDir() {
        var ops = new DesktopOps();
        var home = ops.getHomeDir();
        assertNotNull(home);
        assertFalse(home.isEmpty());
    }

    @Test
    void testDesktopOpsFileExists() {
        var ops = new DesktopOps();
        var home = ops.getHomeDir();
        assertTrue(ops.fileExists(home));
        assertFalse(ops.fileExists("/nonexistent/path/that/does/not/exist"));
    }

    @Test
    void testDesktopOpsTempDir() {
        var ops = new DesktopOps();
        var temp = ops.getTempDir();
        assertNotNull(temp);
        assertFalse(temp.isEmpty());
    }

    @Test
    void testDesktopOpsCreateDirectory() {
        var ops = new DesktopOps();
        var tempDir = ops.getTempDir();
        var testDir = tempDir + "\\clawtest_" + System.currentTimeMillis();
        assertTrue(ops.createDirectory(testDir));
        assertTrue(ops.fileExists(testDir));
        // Cleanup
        new java.io.File(testDir).delete();
    }
}
