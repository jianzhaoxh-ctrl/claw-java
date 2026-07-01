package com.openclaw.desktop.llm;

import com.openclaw.desktop.types.JsonObject;

/**
 * 工具描述符 — 描述工具的元数据，用于 LLM function calling。
 */
public record ToolDescriptor(
    String name,
    String title,
    String description,
    JsonObject inputSchema,
    JsonObject outputSchema
) {}
