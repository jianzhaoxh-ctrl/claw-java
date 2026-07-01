package com.openclaw.desktop.tool.core;

import com.openclaw.desktop.tool.Tool;
import com.openclaw.desktop.tool.ToolContext;
import com.openclaw.desktop.llm.ToolDescriptor;
import com.openclaw.desktop.tool.ToolInput;
import com.openclaw.desktop.tool.ToolResult;
import com.openclaw.desktop.types.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

/**
 * 文件读取工具 — 对应 OpenClaw 的 read 工具。
 */
public class ReadFileTool implements Tool {

    private static final Logger log = LoggerFactory.getLogger(ReadFileTool.class);

    @Override
    public ToolDescriptor descriptor() {
        var inputSchema = new JsonObject(Map.of(
            "path", Map.of("type", "string"),
            "offset", Map.of("type", "number"),
            "limit", Map.of("type", "number")
        ));
        return new ToolDescriptor(
            "read_file",
            "Read File",
            "Read the contents of a file at the specified path.",
            inputSchema,
            JsonObject.empty()
        );
    }

    @Override
    public Mono<ToolResult> execute(ToolInput input, ToolContext context) {
        return Mono.fromCallable(() -> {
            var args = new com.fasterxml.jackson.databind.ObjectMapper().readTree(input.arguments());
            var pathStr = args.get("path").asText();
            var path = Paths.get(pathStr);

            if (!Files.exists(path)) {
                return ToolResult.failure(input.toolCallId(), "File not found: " + pathStr);
            }
            try {
                var content = Files.readString(path);
                return ToolResult.success(input.toolCallId(), content);
            } catch (IOException e) {
                return ToolResult.failure(input.toolCallId(), "Failed to read file: " + e.getMessage());
            }
        });
    }
}
