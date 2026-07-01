package com.openclaw.desktop.agent;

/**
 * 工具调用请求 — LLM 返回的工具调用。
 * @deprecated 使用 {@link com.openclaw.desktop.llm.ToolCall} 代替。
 */
@Deprecated(forRemoval = true)
public record ToolCall(
    String id,
    String name,
    int index,
    String arguments   // JSON string
) {}
