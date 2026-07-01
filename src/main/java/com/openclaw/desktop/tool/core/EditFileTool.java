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
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;

/**
 * 文件编辑工具 — 精确文本替换，对应 OpenClaw 的 edit。
 */
public class EditFileTool implements Tool {

    private static final Logger log = LoggerFactory.getLogger(EditFileTool.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public ToolDescriptor descriptor() {
        ObjectNode schema = MAPPER.createObjectNode();
        schema.put("type", "object");
        var props = MAPPER.createObjectNode();
        props.set("path", MAPPER.createObjectNode().put("type", "string").put("description", "File path"));
        props.set("oldText", MAPPER.createObjectNode().put("type", "string").put("description", "Text to find"));
        props.set("newText", MAPPER.createObjectNode().put("type", "string").put("description", "Replacement text"));
        schema.set("properties", props);
        schema.set("required", MAPPER.createArrayNode().add("path").add("oldText").add("newText"));
        return new ToolDescriptor("edit_file", "Edit File",
            "Find and replace text in a file. All occurrences of oldText will be replaced with newText.",
            JsonObject.wrap(schema), JsonObject.empty());
    }

    @Override
    public Mono<ToolResult> execute(ToolInput input, ToolContext context) {
        return Mono.fromCallable(() -> {
            var args = MAPPER.readTree(input.arguments());
            var pathStr = args.path("path").asText();
            var oldText = args.path("oldText").asText();
            var newText = args.path("newText").asText();
            var path = Paths.get(pathStr);

            if (!Files.exists(path)) {
                return ToolResult.failure(input.toolCallId(), "File not found: " + pathStr);
            }

            var content = Files.readString(path);
            if (!content.contains(oldText)) {
                return ToolResult.failure(input.toolCallId(), "oldText not found in file");
            }

            var newContent = content.replace(oldText, newText);
            Files.writeString(path, newContent);

            var replacements = (content.length() - content.replace(oldText, "").length()) / oldText.length();
            return ToolResult.success(input.toolCallId(),
                "Replaced " + replacements + " occurrence(s) in " + pathStr);
        });
    }
}
