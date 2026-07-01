package com.openclaw.desktop.llm;

import java.util.List;
import java.util.Map;

/**
 * 消息内容块 — 多类型 content 数组的基本单元。
 * 对应 OpenClaw 的 TextContent / ThinkingContent / ImageContent / ToolCall。
 *
 * <p>一条消息的 content 是一个 {@code List<MessageContent>}，
 * 可以同时包含文本、思考过程、图片、工具调用等多种类型。
 */
public sealed interface MessageContent {

    /** 纯文本内容块 */
    record TextContent(String text) implements MessageContent {}

    /** 推理/思考内容块（Anthropic thinking、OpenAI reasoning 等） */
    record ThinkingContent(String thinking, String thinkingSignature, boolean redacted) implements MessageContent {
        public ThinkingContent(String thinking) {
            this(thinking, null, false);
        }
    }

    /** 图片内容块（base64 编码） */
    record ImageContent(String data, String mimeType) implements MessageContent {}

    /** 工具调用内容块（Assistant 消息中） */
    record ToolCallContent(
        String id,
        String name,
        String arguments,
        String thoughtSignature,
        String executionMode
    ) implements MessageContent {
        public ToolCallContent(String id, String name, String arguments) {
            this(id, name, arguments, null, "parallel");
        }
    }

    // -------- factory helpers --------

    static TextContent text(String text) {
        return new TextContent(text);
    }

    static ThinkingContent thinking(String thinking) {
        return new ThinkingContent(thinking);
    }

    static ImageContent image(String data, String mimeType) {
        return new ImageContent(data, mimeType);
    }

    static ToolCallContent toolCall(String id, String name, String arguments) {
        return new ToolCallContent(id, name, arguments);
    }

    /**
     * 从消息内容列表中提取所有文本，拼接为一个字符串。
     * 用于向后兼容旧 API（旧 Message.content() 返回 String）。
     */
    static String extractText(List<MessageContent> contents) {
        var sb = new StringBuilder();
        for (var c : contents) {
            if (c instanceof TextContent tc) {
                sb.append(tc.text());
            } else if (c instanceof ThinkingContent thc) {
                // thinking 不计入正文
            }
        }
        return sb.toString();
    }

    /**
     * 从消息内容列表中提取所有工具调用。
     */
    static List<ToolCallContent> extractToolCalls(List<MessageContent> contents) {
        return contents.stream()
            .filter(c -> c instanceof ToolCallContent)
            .map(c -> (ToolCallContent) c)
            .toList();
    }
}
