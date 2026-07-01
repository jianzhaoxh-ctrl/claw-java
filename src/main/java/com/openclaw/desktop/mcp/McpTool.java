package com.openclaw.desktop.mcp;

/**
 * MCP 工具 — 由 MCP 服务器提供的工具。
 */
public record McpTool(
    String serverName,
    String name,
    String description,
    String inputSchema
) {}
