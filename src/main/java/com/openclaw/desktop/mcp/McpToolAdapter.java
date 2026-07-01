package com.openclaw.desktop.mcp;

import com.openclaw.desktop.llm.ToolDescriptor;
import com.openclaw.desktop.tool.Tool;
import com.openclaw.desktop.tool.ToolContext;
import com.openclaw.desktop.tool.ToolInput;
import com.openclaw.desktop.tool.ToolResult;
import com.openclaw.desktop.types.JsonObject;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

/**
 * MCP 工具适配器 — 将 MCP 服务器提供的工具适配为 {@link Tool} 接口，
 * 使 Agent 可以像调用内置工具一样调用 MCP 工具。
 *
 * <p>工具名采用 {@code mcp__{server}__{tool}} 格式，避免与内置工具冲突。
 */
public class McpToolAdapter implements Tool {

    private static final Logger log = LoggerFactory.getLogger(McpToolAdapter.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final McpClient client;
    private final McpTool mcpTool;
    private final ToolDescriptor descriptor;

    public McpToolAdapter(McpClient client, McpTool mcpTool) {
        this.client = client;
        this.mcpTool = mcpTool;
        this.descriptor = buildDescriptor();
    }

    private ToolDescriptor buildDescriptor() {
        JsonObject inputSchema = JsonObject.empty();
        try {
            if (mcpTool.inputSchema() != null && !mcpTool.inputSchema().isBlank()) {
                var node = MAPPER.readTree(mcpTool.inputSchema());
                inputSchema = JsonObject.wrap(node);
            }
        } catch (Exception e) {
            log.warn("Failed to parse inputSchema for MCP tool {}: {}", mcpTool.name(), e.getMessage());
        }
        var fullName = "mcp__" + mcpTool.serverName() + "__" + mcpTool.name();
        return new ToolDescriptor(
            fullName,
            mcpTool.name(),
            mcpTool.description().isBlank() ? ("MCP tool from " + mcpTool.serverName()) : mcpTool.description(),
            inputSchema,
            JsonObject.empty()
        );
    }

    @Override
    public ToolDescriptor descriptor() {
        return descriptor;
    }

    @Override
    public Mono<ToolResult> execute(ToolInput input, ToolContext context) {
        return Mono.fromCallable(() -> {
            try {
                var result = client.callTool(mcpTool.name(), input.arguments());
                return ToolResult.success(input.toolCallId(), result);
            } catch (Exception e) {
                log.error("MCP tool call failed [{}.{}]: {}", mcpTool.serverName(), mcpTool.name(), e.getMessage());
                return ToolResult.failure(input.toolCallId(), "MCP tool error: " + e.getMessage());
            }
        });
    }

    public McpTool mcpTool() { return mcpTool; }
    public String serverName() { return mcpTool.serverName(); }
}
