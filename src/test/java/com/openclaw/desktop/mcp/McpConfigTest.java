package com.openclaw.desktop.mcp;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MCP 配置与数据模型单元测试。
 * 注：McpClient 涉及子进程通信，需集成测试环境，这里仅测数据模型。
 */
class McpConfigTest {

    @Test
    @DisplayName("McpServerConfig.stdio creates stdio config")
    void testStdioConfig() {
        var config = McpServerConfig.stdio("test", "node", List.of("server.js"));
        assertEquals("test", config.name());
        assertEquals("node", config.command());
        assertEquals(List.of("server.js"), config.args());
        assertNull(config.url());
        assertTrue(config.enabled());
    }

    @Test
    @DisplayName("McpServerConfig.sse creates sse config")
    void testSseConfig() {
        var config = McpServerConfig.sse("remote", "http://localhost:3000/sse");
        assertEquals("remote", config.name());
        assertNull(config.command());
        assertEquals("http://localhost:3000/sse", config.url());
        assertTrue(config.enabled());
    }

    @Test
    @DisplayName("McpTool record holds server/tool info")
    void testMcpToolRecord() {
        var tool = new McpTool("server1", "search", "Search the web", "{\"type\":\"object\"}");
        assertEquals("server1", tool.serverName());
        assertEquals("search", tool.name());
        assertEquals("Search the web", tool.description());
        assertEquals("{\"type\":\"object\"}", tool.inputSchema());
    }

    @Test
    @DisplayName("McpServerConfig default env is empty map")
    void testDefaultEnv() {
        var config = McpServerConfig.stdio("test", "cmd", List.of());
        assertTrue(config.env().isEmpty());
    }

    @Test
    @DisplayName("McpTool with null description")
    void testMcpToolNullDesc() {
        var tool = new McpTool("s", "t", null, null);
        assertNull(tool.description());
        assertNull(tool.inputSchema());
    }

    @Test
    @DisplayName("McpTool with empty schema")
    void testMcpToolEmptySchema() {
        var tool = new McpTool("s", "t", "desc", "");
        assertEquals("", tool.inputSchema());
    }
}
