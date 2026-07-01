package com.openclaw.desktop.llm;

import java.util.List;

/**
 * 消息类型 — LLM 请求中的消息。
 * sealed 接口，对应 OpenClaw 的 Message。
 *
 * <p>v2.0 重构：content 改为 {@code List<MessageContent>} 多类型数组，
 * 支持 text / thinking / image / toolCall 混合内容。
 * 新增 role() / content() / timestamp() 统一方法。
 */
public sealed interface Message {

    /** 角色标识 */
    String role();

    /** 内容块列表 */
    List<MessageContent> content();

    /** 时间戳（Unix epoch milliseconds） */
    long timestamp();

    // ==================== SystemMessage ====================

    record SystemMessage(List<MessageContent> content, long timestamp) implements Message {
        public SystemMessage(String content) {
            this(List.of(new MessageContent.TextContent(content)), System.currentTimeMillis());
        }

        public SystemMessage(String content, long timestamp) {
            this(List.of(new MessageContent.TextContent(content)), timestamp);
        }

        @Override
        public String role() { return "system"; }
    }

    // ==================== UserMessage ====================

    record UserMessage(List<MessageContent> content, long timestamp) implements Message {
        public UserMessage(String content) {
            this(List.of(new MessageContent.TextContent(content)), System.currentTimeMillis());
        }

        public UserMessage(String content, long timestamp) {
            this(List.of(new MessageContent.TextContent(content)), timestamp);
        }

        @Override
        public String role() { return "user"; }
    }

    // ==================== AssistantMessage ====================

    record AssistantMessage(
        List<MessageContent> content,
        String api,
        String provider,
        String model,
        String responseModel,
        String responseId,
        UsageInfo usage,
        String stopReason,
        String errorMessage,
        long timestamp
    ) implements Message {
        @Override
        public String role() { return "assistant"; }

        /**
         * 向后兼容：提取纯文本内容。
         */
        public String text() {
            return MessageContent.extractText(content);
        }

        /**
         * 向后兼容：提取工具调用列表。
         */
        public List<MessageContent.ToolCallContent> toolCalls() {
            return MessageContent.extractToolCalls(content);
        }

        /**
         * 向后兼容：从旧格式构造。
         */
        public AssistantMessage(String text, List<ToolCall> toolCalls) {
            this(
                buildContent(text, toolCalls),
                "openai-completions", "openai", "", null, null,
                new UsageInfo(0, 0, 0, null, null),
                toolCalls.isEmpty() ? "stop" : "toolUse",
                null,
                System.currentTimeMillis()
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
    }

    // ==================== ToolResultMessage ====================

    record ToolResultMessage(
        String toolCallId,
        String toolName,
        List<MessageContent> content,
        boolean isError,
        long timestamp
    ) implements Message {
        @Override
        public String role() { return "toolResult"; }

        /**
         * 向后兼容：从旧格式构造。
         */
        public ToolResultMessage(String toolCallId, String content) {
            this(toolCallId, null,
                List.of(new MessageContent.TextContent(content)),
                false, System.currentTimeMillis());
        }

        /**
         * 向后兼容：提取纯文本内容。
         */
        public String text() {
            return MessageContent.extractText(content);
        }
    }
}
