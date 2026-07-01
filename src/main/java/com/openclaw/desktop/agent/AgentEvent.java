package com.openclaw.desktop.agent;

import com.openclaw.desktop.llm.Message;
import com.openclaw.desktop.llm.MessageContent;
import com.openclaw.desktop.llm.UsageInfo;

import java.util.List;

/**
 * Agent 事件 — v2.0 重构，完整对齐 OpenClaw agent-loop.ts 的生命周期。
 *
 * <p>事件分为三类：
 * <ol>
 *   <li><b>生命周期事件</b>：AgentStart, AgentEnd, TurnStart, TurnEnd, MessageStart, MessageEnd</li>
 *   <li><b>流式内容事件</b>：TextStart/Delta/End, ThinkingStart/Delta/End, ToolCallStart/Delta/End</li>
 *   <li><b>其他</b>：Usage, Error</li>
 * </ol>
 *
 * <p>旧的简化事件类型（MessageReceived, TextDelta 等）已替换为完整生命周期版本。
 * 向后兼容通过 {@link LegacyAdapter} 提供。
 */
public sealed interface AgentEvent {

    // =========================================================
    // 生命周期事件
    // =========================================================

    /** Agent 开始运行 */
    record AgentStart() implements AgentEvent {}

    /** Agent 运行结束，携带本次产生的所有消息 */
    record AgentEnd(List<Message> messages) implements AgentEvent {}

    /** 一轮对话开始 */
    record TurnStart() implements AgentEvent {}

    /** 一轮对话结束，携带本轮的 assistant 消息和工具结果 */
    record TurnEnd(Message assistantMessage, List<Message> toolResults) implements AgentEvent {}

    /** 一条消息开始处理（user 或 toolResult 注入） */
    record MessageStart(Message message) implements AgentEvent {}

    /** 一条消息处理结束 */
    record MessageEnd(Message message) implements AgentEvent {}

    // =========================================================
    // 文本流式事件
    // =========================================================

    record TextStart(int contentIndex) implements AgentEvent {}

    record TextDelta(int contentIndex, String delta) implements AgentEvent {}

    record TextEnd(int contentIndex, String fullText) implements AgentEvent {}

    // =========================================================
    // Thinking 流式事件
    // =========================================================

    record ThinkingStart(int contentIndex) implements AgentEvent {}

    record ThinkingDelta(int contentIndex, String delta) implements AgentEvent {}

    record ThinkingEnd(int contentIndex, String fullThinking) implements AgentEvent {}

    // =========================================================
    // 工具调用流式事件
    // =========================================================

    record ToolCallStart(int index, String toolName) implements AgentEvent {}

    record ToolCallDelta(int index, String argumentsDelta) implements AgentEvent {}

    record ToolCallEnd(int index, String toolCallId, String result) implements AgentEvent {}

    // =========================================================
    // 其他事件
    // =========================================================

    record Usage(UsageInfo usage) implements AgentEvent {}

    record Error(Throwable error) implements AgentEvent {}

    // =========================================================
    // 向后兼容：旧事件类型适配
    // =========================================================

    /**
     * 将新版 AgentEvent 转换为旧版简化事件，供未迁移的消费者使用。
     */
    final class LegacyAdapter {

        private LegacyAdapter() {}

        /**
         * 判断事件是否为文本增量（兼容旧 TextDelta）。
         */
        public static boolean isTextDelta(AgentEvent event) {
            return event instanceof TextDelta;
        }

        /**
         * 提取文本增量内容（兼容旧 TextDelta.delta()）。
         */
        public static String extractDelta(AgentEvent event) {
            if (event instanceof TextDelta(var idx, var delta)) return delta;
            if (event instanceof ThinkingDelta(var idx2, var delta2)) return delta2;
            return "";
        }

        /**
         * 判断事件是否为流结束（兼容旧 StreamCompleted）。
         */
        public static boolean isStreamEnd(AgentEvent event) {
            return event instanceof AgentEnd;
        }

        /**
         * 判断事件是否为错误（兼容旧 ErrorOccurred）。
         */
        public static boolean isError(AgentEvent event) {
            return event instanceof Error;
        }

        /**
         * 判断事件是否为工具调用开始（兼容旧 ToolCallStarted）。
         */
        public static boolean isToolCallStart(AgentEvent event) {
            return event instanceof ToolCallStart;
        }

        /**
         * 判断事件是否为工具调用结束（兼容旧 ToolCallCompleted）。
         */
        public static boolean isToolCallEnd(AgentEvent event) {
            return event instanceof ToolCallEnd;
        }
    }
}
