package com.openclaw.desktop.tool;

/**
 * 工具输入。
 */
public record ToolInput(
    String toolCallId,
    String arguments   // JSON string
) {}
