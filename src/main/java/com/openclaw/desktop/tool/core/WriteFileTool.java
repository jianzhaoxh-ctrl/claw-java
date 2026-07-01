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
 * 文件写入工具 — 对应 OpenClaw 的 write 工具。
 */
public class WriteFileTool implements Tool {

    private static final Logger log = LoggerFactory.getLogger(WriteFileTool.class);

    @Override
    public ToolDescriptor descriptor() {
        var inputSchema = new JsonObject(Map.of(
            "path", Map.of("type", "string"),
            "content", Map.of("type", "string")
        ));
        return new ToolDescriptor(
            "write_file",
            "Write File",
            "Write content to a file at the specified path. Creates the file if it does not exist.",
            inputSchema,
            JsonObject.empty()
        );
    }

    @Override
    public Mono<ToolResult> execute(ToolInput input, ToolContext context) {
        return Mono.fromCallable(() -> {
            var args = new com.fasterxml.jackson.databind.ObjectMapper().readTree(input.arguments());
            var pathStr = args.get("path").asText();
            var content = args.get("content").asText();
            var path = Paths.get(pathStr);

            try {
                if (path.getParent() != null) {
                    Files.createDirectories(path.getParent());
                }
                Files.writeString(path, content);
                return ToolResult.success(input.toolCallId(), "File written: " + pathStr);
            } catch (IOException e) {
                return ToolResult.failure(input.toolCallId(), "Failed to write file: " + e.getMessage());
            }
        });
    }
}
