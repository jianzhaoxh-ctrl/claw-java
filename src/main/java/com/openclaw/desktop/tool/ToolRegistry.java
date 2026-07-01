package com.openclaw.desktop.tool;

import com.openclaw.desktop.llm.ToolDescriptor;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 工具注册表 — 注册和管理所有可用工具。
 */
public class ToolRegistry {

    private final java.util.Map<String, Tool> tools = new ConcurrentHashMap<>();

    public void register(Tool tool) {
        tools.put(tool.descriptor().name(), tool);
    }

    public void unregister(String name) {
        tools.remove(name);
    }

    public Optional<Tool> get(String name) {
        return Optional.ofNullable(tools.get(name));
    }

    public List<ToolDescriptor> listDescriptors() {
        return tools.values().stream()
            .map(Tool::descriptor)
            .toList();
    }

    public boolean contains(String name) {
        return tools.containsKey(name);
    }

    public int size() {
        return tools.size();
    }

    /**
     * 执行工具调用。
     */
    public reactor.core.publisher.Mono<ToolResult> execute(String toolName, ToolInput input, ToolContext context) {
        var toolOpt = get(toolName);
        if (toolOpt.isEmpty()) {
            return reactor.core.publisher.Mono.just(ToolResult.failure(input.toolCallId(), "Tool not found: " + toolName));
        }
        return toolOpt.get().execute(input, context);
    }

    /**
     * 批量执行工具调用（保持顺序）。
     */
    public reactor.core.publisher.Flux<ToolResult> executeAll(java.util.List<com.openclaw.desktop.llm.ToolCall> toolCalls, ToolContext context) {
        return reactor.core.publisher.Flux.fromIterable(toolCalls)
            .flatMap(tc -> {
                var input = new ToolInput(tc.id(), tc.arguments());
                return execute(tc.name(), input, context);
            });
    }
}
