package com.openclaw.desktop.llm;

import java.util.List;
import java.util.Map;

/**
 * LLM 响应 — v2.0 重构。
 *
 * <p>content 改为 {@code List<MessageContent>}，
 * 支持 text + thinking + toolCall 混合输出。
 * 新增 api / provider / model / responseId / stopReason 等元数据。
 */
public record LlmResponse(
    List<MessageContent> content,
    UsageInfo usage,
    String stopReason,
    String errorMessage,
    String api,
    String provider,
    String model,
    String responseModel,
    String responseId,
    Map<String, Object> extraData
) {
    /**
     * 向后兼容：提取纯文本。
     */
    public String text() {
        return MessageContent.extractText(content);
    }

    /**
     * 向后兼容：提取工具调用。
     */
    public List<MessageContent.ToolCallContent> toolCalls() {
        return MessageContent.extractToolCalls(content);
    }

    /**
     * 向后兼容：从旧格式构造。
     */
    public LlmResponse(String content, List<ToolCall> toolCalls, UsageInfo usage, String finishReason, Map<String, Object> extraData) {
        this(
            buildContent(content, toolCalls),
            usage,
            finishReason,
            null,
            "openai-completions", "openai", "", null, null,
            extraData
        );
    }

    private static List<MessageContent> buildContent(String text, List<ToolCall> toolCalls) {
        var list = new java.util.ArrayList<MessageContent>();
        if (text != null && !text.isEmpty()) {
            list.add(new MessageContent.TextContent(text));
        }
        if (toolCalls != null) {
            for (var tc : toolCalls) {
                list.add(new MessageContent.ToolCallContent(tc.id(), tc.name(), tc.arguments()));
            }
        }
        return list;
    }

    public static LlmResponse empty() {
        return new LlmResponse(
            List.of(), new UsageInfo(0, 0, 0, null, null),
            "stop", null, "openai-completions", "openai", "", null, null, Map.of()
        );
    }

    /**
     * 构造文本响应。
     */
    public static LlmResponse text(String text, UsageInfo usage, String stopReason) {
        return new LlmResponse(
            List.of(new MessageContent.TextContent(text)),
            usage, stopReason, null,
            "openai-completions", "openai", "", null, null, Map.of()
        );
    }
}
