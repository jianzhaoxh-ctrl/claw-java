package com.openclaw.desktop.tool;

/**
 * 工具执行结果。
 */
public record ToolResult(
    String toolCallId,
    boolean success,
    String content,
    String errorMessage
) {
    public static ToolResult success(String toolCallId, String content) {
        return new ToolResult(toolCallId, true, content, null);
    }

    public static ToolResult failure(String toolCallId, String error) {
        return new ToolResult(toolCallId, false, null, error);
    }
}
