package com.openclaw.desktop.mcp;

import java.util.List;
import java.util.Map;

/**
 * MCP 服务器配置。
 */
public record McpServerConfig(
    String name,
    String command,
    List<String> args,
    Map<String, String> env,
    String url,
    boolean enabled
) {
    public static McpServerConfig stdio(String name, String command, List<String> args) {
        return new McpServerConfig(name, command, args, Map.of(), null, true);
    }

    public static McpServerConfig sse(String name, String url) {
        return new McpServerConfig(name, null, List.of(), Map.of(), url, true);
    }
}
