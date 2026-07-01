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

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 文件删除工具 — 对应 OpenClaw 的 delete。
 * 使用 trash 而非永久删除（如果系统支持）。
 */
public class DeleteFileTool implements Tool {

    private static final Logger log = LoggerFactory.getLogger(DeleteFileTool.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public ToolDescriptor descriptor() {
        ObjectNode schema = MAPPER.createObjectNode();
        schema.put("type", "object");
        schema.set("properties", MAPPER.createObjectNode()
            .set("path", MAPPER.createObjectNode()
                .put("type", "string")
                .put("description", "File or directory path to delete")));
        schema.set("required", MAPPER.createArrayNode().add("path"));
        return new ToolDescriptor("delete_file", "Delete File",
            "Delete a file or directory. Uses trash when available for recoverability.",
            JsonObject.wrap(schema), JsonObject.empty());
    }

    @Override
    public Mono<ToolResult> execute(ToolInput input, ToolContext context) {
        return Mono.fromCallable(() -> {
            var args = MAPPER.readTree(input.arguments());
            var pathStr = args.path("path").asText();
            var path = Paths.get(pathStr);

            if (!Files.exists(path)) {
                return ToolResult.failure(input.toolCallId(), "File not found: " + pathStr);
            }

            // 尝试移动到回收站（桌面环境），失败则永久删除
            try {
                var desktop = java.awt.Desktop.getDesktop();
                if (desktop.isSupported(java.awt.Desktop.Action.MOVE_TO_TRASH)) {
                    if (desktop.moveToTrash(path.toFile())) {
                        return ToolResult.success(input.toolCallId(), "Moved to trash: " + pathStr);
                    }
                }
            } catch (Exception e) {
                log.debug("Trash not available, doing permanent delete");
            }

            if (Files.isDirectory(path)) {
                Files.walk(path)
                    .sorted(java.util.Comparator.reverseOrder())
                    .forEach(p -> {
                        try { Files.deleteIfExists(p); } catch (Exception ex) { }
                    });
                return ToolResult.success(input.toolCallId(), "Deleted directory: " + pathStr);
            } else {
                Files.delete(path);
                return ToolResult.success(input.toolCallId(), "Deleted file: " + pathStr);
            }
        });
    }
}
