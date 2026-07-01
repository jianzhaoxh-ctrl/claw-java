package com.openclaw.desktop.tool.core;

import com.openclaw.desktop.llm.ToolDescriptor;
import com.openclaw.desktop.tool.*;
import com.openclaw.desktop.types.JsonObject;
import com.openclaw.desktop.memory.MemoryDatabase;
import com.openclaw.desktop.memory.MemoryEntry;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * 记忆搜索工具 — 搜索 ClawDesktop 的持久化记忆数据库。
 * 对应 OpenClaw 的 memory-core extension。
 */
public class MemorySearchTool implements Tool {

    private static final Logger log = LoggerFactory.getLogger(MemorySearchTool.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final MemoryDatabase memoryDatabase;

    public MemorySearchTool(MemoryDatabase memoryDatabase) {
        this.memoryDatabase = memoryDatabase;
    }

    @Override
    public ToolDescriptor descriptor() {
        var inputSchema = new JsonObject(Map.of(
            "query", Map.of("type", "string", "description", "Search query"),
            "limit", Map.of("type", "number", "description", "Max results (default 10)"),
            "tags", Map.of("type", "string", "description", "Filter by tags (comma-separated)"),
            "sessionKey", Map.of("type", "string", "description", "Filter by session key"),
            "recentOnly", Map.of("type", "boolean", "description", "Only recent entries")
        ));
        return new ToolDescriptor(
            "memory_search",
            "Memory Search",
            "Search the assistant's persistent memory database for past conversations, decisions, and learned facts.",
            inputSchema,
            JsonObject.empty()
        );
    }

    @Override
    public Mono<ToolResult> execute(ToolInput input, ToolContext context) {
        return Mono.fromCallable(() -> {
            var args = MAPPER.readTree(input.arguments());
            var query = args.path("query").asText("");
            var limit = args.path("limit").asInt(10);
            var recentOnly = args.path("recentOnly").asBoolean(false);

            if (query.isEmpty() && !recentOnly) {
                return ToolResult.failure(input.toolCallId(), "Query is required (or set recentOnly=true)");
            }

            List<MemoryEntry> entries;
            if (recentOnly) {
                entries = memoryDatabase.recent(limit);
            } else {
                entries = memoryDatabase.search(query, limit);
            }

            if (entries.isEmpty()) {
                return ToolResult.success(input.toolCallId(),
                    "🔍 No memory entries found" +
                    (query.isEmpty() ? "" : " for '" + query + "'") + ".");
            }

            // 按标签过滤
            var tagsStr = args.path("tags").asText("");
            if (!tagsStr.isEmpty()) {
                var filterTags = tagsStr.split(",");
                entries = entries.stream()
                    .filter(e -> {
                        if (e.tags() == null) return false;
                        for (var ft : filterTags) {
                            var t = ft.trim();
                            for (var et : e.tags()) {
                                if (et != null && et.contains(t)) return true;
                            }
                        }
                        return false;
                    })
                    .toList();
            }

            var sb = new StringBuilder();
            sb.append("🔍 Found ").append(entries.size()).append(" memory entr(y/ies):\n\n");
            for (int i = 0; i < entries.size(); i++) {
                var entry = entries.get(i);
                sb.append("## ").append(i + 1).append(". ");
                sb.append("[").append(entry.createdAt() != null ? entry.createdAt().toString().substring(0, 19) : "unknown").append("]\n");
                if (entry.tags() != null && entry.tags().length > 0) {
                    sb.append("Tags: ").append(String.join(", ", entry.tags())).append("\n");
                }
                var content = entry.content();
                if (content.length() > 500) {
                    content = content.substring(0, 500) + "...";
                }
                sb.append(content).append("\n\n");
            }
            return ToolResult.success(input.toolCallId(), sb.toString().trim());
        });
    }
}
