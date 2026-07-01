package com.openclaw.desktop.llm;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * LLM 流式事件 — v2.0 重构，对齐 OpenClaw AgentEvent 生命周期。
 *
 * <p>事件分为三类：
 * <ol>
 *   <li><b>生命周期事件</b>：agent_start, agent_end, turn_start, turn_end, message_start, message_end</li>
 *   <li><b>流式内容事件</b>：text_start/delta/end, thinking_start/delta/end, toolcall_start/delta/end</li>
 *   <li><b>其他</b>：usage, error</li>
 * </ol>
 */
public sealed interface LlmEvent {

    // =========================================================
    // 生命周期事件（对齐 OpenClaw agent-loop.ts）
    // =========================================================

    /** Agent 开始运行 */
    record AgentStart() implements LlmEvent {}

    /** Agent 运行结束，携带本次产生的所有消息 */
    record AgentEnd(java.util.List<Message> messages) implements LlmEvent {}

    /** 一轮对话开始（一个 AgentLoop 可能有多轮） */
    record TurnStart() implements LlmEvent {}

    /** 一轮对话结束，携带本轮的 assistant 消息和工具结果 */
    record TurnEnd(Message assistantMessage, java.util.List<Message> toolResults) implements LlmEvent {}

    /** 一条消息开始处理（user 或 toolResult） */
    record MessageStart(Message message) implements LlmEvent {}

    /** 一条消息处理结束 */
    record MessageEnd(Message message) implements LlmEvent {}

    // =========================================================
    // 文本内容流事件
    // =========================================================

    /** 第 contentIndex 个文本块开始 */
    record TextStart(int contentIndex, Message partialMessage) implements LlmEvent {
        public TextStart(int contentIndex) { this(contentIndex, null); }
    }

    /** 第 contentIndex 个文本块收到 delta */
    record TextDelta(int contentIndex, String delta, Message partialMessage) implements LlmEvent {
        public TextDelta(int contentIndex, String delta) { this(contentIndex, delta, null); }
    }

    /** 第 contentIndex 个文本块结束 */
    record TextEnd(int contentIndex, String fullText, Message partialMessage) implements LlmEvent {
        public TextEnd(int contentIndex, String fullText) { this(contentIndex, fullText, null); }
    }

    // =========================================================
    // Thinking / 推理内容流事件
    // =========================================================

    /** Thinking 块开始 */
    record ThinkingStart(int contentIndex, Message partialMessage) implements LlmEvent {
        public ThinkingStart(int contentIndex) { this(contentIndex, null); }
    }

    /** Thinking 块收到 delta */
    record ThinkingDelta(int contentIndex, String delta, Message partialMessage) implements LlmEvent {
        public ThinkingDelta(int contentIndex, String delta) { this(contentIndex, delta, null); }
    }

    /** Thinking 块结束 */
    record ThinkingEnd(int contentIndex, String fullThinking, Message partialMessage) implements LlmEvent {
        public ThinkingEnd(int contentIndex, String fullThinking) { this(contentIndex, fullThinking, null); }
    }

    // =========================================================
    // 工具调用流事件
    // =========================================================

    /** 工具调用块开始 */
    record ToolCallStart(int contentIndex, String name, Message partialMessage) implements LlmEvent {
        public ToolCallStart(int contentIndex, String name) { this(contentIndex, name, null); }
    }

    /** 工具调用参数 delta */
    record ToolCallDelta(int contentIndex, String delta, Message partialMessage) implements LlmEvent {
        public ToolCallDelta(int contentIndex, String delta) { this(contentIndex, delta, null); }
    }

    /** 工具调用块结束 */
    record ToolCallEnd(int contentIndex, MessageContent.ToolCallContent toolCall, Message partialMessage) implements LlmEvent {
        public ToolCallEnd(int contentIndex, MessageContent.ToolCallContent toolCall) { this(contentIndex, toolCall, null); }
    }

    // =========================================================
    // 其他事件
    // =========================================================

    record Usage(UsageInfo usage) implements LlmEvent {}

    record Error(Throwable error, Message partialMessage) implements LlmEvent {
        public Error(Throwable error) { this(error, null); }
    }

    // =========================================================
    // 静态工具方法
    // =========================================================

    /**
     * 将 LlmEvent 列表聚合成 LlmResponse。
     * 用于非流式调用（收集所有事件后一次性返回）。
     */
    static LlmResponse toResponse(java.util.List<LlmEvent> events) {
        var textBlocks = new java.util.ArrayList<StringBuilder>();
        var thinkingBlocks = new java.util.ArrayList<StringBuilder>();
        var toolCalls = new java.util.ArrayList<MessageContent.ToolCallContent>();
        var toolCallArgs = new java.util.HashMap<Integer, StringBuilder>();
        var toolCallNames = new java.util.HashMap<Integer, String>();
        UsageInfo usage = new UsageInfo(0, 0, 0, null, null);
        String api = "openai-completions";
        String provider = "openai";
        String model = "";
        String responseId = null;
        String stopReason = "stop";

        for (var event : events) {
            switch (event) {
                case TextStart(var idx, var msg) -> {
                    while (textBlocks.size() <= idx) textBlocks.add(null);
                    textBlocks.set(idx, new StringBuilder());
                }
                case TextDelta(var idx, var delta, var msg) -> {
                    if (idx < textBlocks.size() && textBlocks.get(idx) != null) {
                        textBlocks.get(idx).append(delta);
                    } else {
                        while (textBlocks.size() <= idx) textBlocks.add(null);
                        var sb = new StringBuilder();
                        sb.append(delta);
                        textBlocks.set(idx, sb);
                    }
                }
                case TextEnd(var idx1, var txt, var msg) -> {}

                case ThinkingStart(var idx, var msg) -> {
                    while (thinkingBlocks.size() <= idx) thinkingBlocks.add(null);
                    thinkingBlocks.set(idx, new StringBuilder());
                }
                case ThinkingDelta(var idx, var delta, var msg) -> {
                    if (idx < thinkingBlocks.size() && thinkingBlocks.get(idx) != null) {
                        thinkingBlocks.get(idx).append(delta);
                    }
                }
                case ThinkingEnd(var idx1, var txt, var msg) -> {}

                case ToolCallStart(var idx, var name, var msg) -> {
                    toolCallNames.put(idx, name);
                    toolCallArgs.put(idx, new StringBuilder());
                }
                case ToolCallDelta(var idx, var delta, var msg) -> {
                    toolCallArgs.computeIfAbsent(idx, k -> new StringBuilder()).append(delta);
                }
                case ToolCallEnd(var idx, var tc, var msg) -> {
                    var args = toolCallArgs.getOrDefault(idx, new StringBuilder()).toString();
                    toolCalls.add(new MessageContent.ToolCallContent(
                        "call_" + idx,
                        tc != null ? tc.name() : toolCallNames.getOrDefault(idx, ""),
                        args,
                        null,
                        "parallel"
                    ));
                }

                case Usage(var u) -> usage = u;
                case AgentEnd(var msgs) -> {}
                case Error(var e, var msg) -> stopReason = "error";
                default -> {}
            }
        }

        // 组装 content 列表
        var contents = new java.util.ArrayList<MessageContent>();
        for (var sb : textBlocks) {
            if (sb != null && sb.length() > 0) {
                contents.add(new MessageContent.TextContent(sb.toString()));
            }
        }
        for (var sb : thinkingBlocks) {
            if (sb != null && sb.length() > 0) {
                contents.add(new MessageContent.ThinkingContent(sb.toString()));
            }
        }
        contents.addAll(toolCalls);

        return new LlmResponse(
            contents,
            usage,
            stopReason,
            null,               // errorMessage
            api,
            provider,
            model,
            model,              // responseModel
            responseId,
            java.util.Map.of()
        );
    }
}
