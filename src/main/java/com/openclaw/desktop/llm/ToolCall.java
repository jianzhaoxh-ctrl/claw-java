package com.openclaw.desktop.llm;

/**
 * 工具调用 — LLM 返回的工具调用请求。
 */
public record ToolCall(
    String id,
    String name,
    int index,
    String arguments   // JSON string
) {}
