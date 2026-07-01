package com.openclaw.desktop.tool.core;

import com.openclaw.desktop.tool.Tool;
import com.openclaw.desktop.tool.ToolContext;
import com.openclaw.desktop.llm.ToolDescriptor;
import com.openclaw.desktop.tool.ToolInput;
import com.openclaw.desktop.tool.ToolResult;
import com.openclaw.desktop.types.JsonObject;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * 文件列表工具 — 对应 OpenClaw 的 ls。
 */
public class ListFilesTool implements Tool {

    private static final Logger log = LoggerFactory.getLogger(ListFilesTool.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public ToolDescriptor descriptor() {
        ObjectNode schema = MAPPER.createObjectNode();
        schema.put("type", "object");
        schema.set("properties", MAPPER.createObjectNode()
            .set("path", MAPPER.createObjectNode()
                .put("type", "string")
                .put("description", "Directory path to list")));
        schema.set("required", MAPPER.createArrayNode().add("path"));
        return new ToolDescriptor("list_files", "List Files",
            "List files and directories at the specified path.",
            JsonObject.wrap(schema), JsonObject.empty());
    }

    @Override
    public Mono<ToolResult> execute(ToolInput input, ToolContext context) {
        return Mono.fromCallable(() -> {
            var args = MAPPER.readTree(input.arguments());
            var pathStr = args.path("path").asText(".");
            var path = Paths.get(pathStr);

            if (!Files.isDirectory(path)) {
                return ToolResult.failure(input.toolCallId(), "Not a directory: " + pathStr);
            }
            try (Stream<Path> stream = Files.list(path)) {
                var sb = new StringBuilder();
                stream.sorted().forEach(p -> {
                    try {
                        var attrs = Files.readAttributes(p, java.nio.file.attribute.BasicFileAttributes.class);
                        var name = p.getFileName().toString();
                        var type = attrs.isDirectory() ? "DIR " : "FILE";
                        var size = attrs.isDirectory() ? "-" : String.valueOf(attrs.size());
                        sb.append(String.format("%-5s %10s  %s%n", type, size, name));
                    } catch (IOException e) {
                        sb.append("ERR   ?          ").append(p.getFileName()).append('\n');
                    }
                });
                return ToolResult.success(input.toolCallId(), sb.toString());
            }
        });
    }
}
