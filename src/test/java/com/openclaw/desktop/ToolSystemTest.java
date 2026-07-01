package com.openclaw.desktop;

import com.openclaw.desktop.config.ClawConfig;
import com.openclaw.desktop.config.ConfigLoader;
import com.openclaw.desktop.tool.ToolRegistry;
import com.openclaw.desktop.tool.core.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 工具系统测试。
 */
class ToolSystemTest {

    private ToolRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new ToolRegistry();
        registry.register(new ReadFileTool());
        registry.register(new WriteFileTool());
        registry.register(new EditFileTool());
        registry.register(new ListFilesTool());
        registry.register(new ShellExecTool());
        registry.register(new WebSearchTool());
        registry.register(new WebFetchTool());
    }

    @Test
    @DisplayName("Tool registry should have all registered tools")
    void testRegistrySize() {
        assertEquals(7, registry.size());
    }

    @Test
    @DisplayName("Should get tool by name")
    void testGetByName() {
        var tool = registry.get("read_file");
        assertTrue(tool.isPresent());
        assertEquals("read_file", tool.get().descriptor().name());
    }

    @Test
    @DisplayName("Should return empty for unknown tool")
    void testUnknownTool() {
        var tool = registry.get("nonexistent");
        assertTrue(tool.isEmpty());
    }

    @Test
    @DisplayName("WriteFile then ReadFile should work")
    void testWriteAndRead() throws Exception {
        var tmpFile = Path.of(System.getProperty("java.io.tmpdir"), "claw-test-" + System.nanoTime() + ".txt");
        var content = "Hello, ClawDesktop!";

        // Write
        var writeTool = registry.get("write_file").orElseThrow();
        var writeInput = new com.openclaw.desktop.tool.ToolInput("test-1",
            "{\"path\":\"" + tmpFile.toString().replace("\\", "\\\\") + "\",\"content\":\"" + content + "\"}");
        var writeResult = writeTool.execute(writeInput, null).block();
        assertNotNull(writeResult);
        assertTrue(writeResult.success());

        // Read
        var readTool = registry.get("read_file").orElseThrow();
        var readInput = new com.openclaw.desktop.tool.ToolInput("test-2",
            "{\"path\":\"" + tmpFile.toString().replace("\\", "\\\\") + "\"}");
        var readResult = readTool.execute(readInput, null).block();
        assertNotNull(readResult);
        assertTrue(readResult.success());
        assertTrue(readResult.content().contains(content));

        // Cleanup
        Files.deleteIfExists(tmpFile);
    }

    @Test
    @DisplayName("ShellExec should run a simple command")
    void testShellExec() {
        var tool = registry.get("shell_exec").orElseThrow();
        var isWindows = System.getProperty("os.name").toLowerCase().contains("win");
        var command = isWindows ? "echo hello" : "echo hello";
        var input = new com.openclaw.desktop.tool.ToolInput("test-3",
            "{\"command\":\"" + command + "\"}");
        var result = tool.execute(input, null).block();
        assertNotNull(result);
        assertTrue(result.success());
        assertTrue(result.content().contains("hello"));
    }
}
