package com.openclaw.desktop.trajectory;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.openclaw.desktop.llm.Message;
import com.openclaw.desktop.llm.UsageInfo;

import java.time.Instant;

/**
 * 轨迹事件 — 记录 Agent 运行时的关键事件。
 * 对应 OpenClaw 的 TrajectoryEvent（src/trajectory/）。
 *
 * <p>用于会话回放、调试、导出。
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(name = "AgentStarted", value = TrajectoryEvent.AgentStarted.class),
    @JsonSubTypes.Type(name = "AgentEnded", value = TrajectoryEvent.AgentEnded.class),
    @JsonSubTypes.Type(name = "TurnStarted", value = TrajectoryEvent.TurnStarted.class),
    @JsonSubTypes.Type(name = "TurnEnded", value = TrajectoryEvent.TurnEnded.class),
    @JsonSubTypes.Type(name = "UserMessageInjected", value = TrajectoryEvent.UserMessageInjected.class),
    @JsonSubTypes.Type(name = "LlmRequestSent", value = TrajectoryEvent.LlmRequestSent.class),
    @JsonSubTypes.Type(name = "LlmResponseReceived", value = TrajectoryEvent.LlmResponseReceived.class),
    @JsonSubTypes.Type(name = "ToolCallStarted", value = TrajectoryEvent.ToolCallStarted.class),
    @JsonSubTypes.Type(name = "ToolCallEnded", value = TrajectoryEvent.ToolCallEnded.class),
    @JsonSubTypes.Type(name = "ContextCompacted", value = TrajectoryEvent.ContextCompacted.class),
    @JsonSubTypes.Type(name = "ErrorOccurred", value = TrajectoryEvent.ErrorOccurred.class),
    @JsonSubTypes.Type(name = "SteeringInjected", value = TrajectoryEvent.SteeringInjected.class)
})
public sealed interface TrajectoryEvent {

    /** Agent 开始运行 */
    record AgentStarted(String agentId, String modelId, Instant timestamp) implements TrajectoryEvent {}

    /** Agent 结束运行 */
    record AgentEnded(String agentId, String stopReason, Instant timestamp) implements TrajectoryEvent {}

    /** 一轮对话开始 */
    record TurnStarted(int turnIndex, Instant timestamp) implements TrajectoryEvent {}

    /** 一轮对话结束 */
    record TurnEnded(int turnIndex, String stopReason, UsageInfo usage, Instant timestamp) implements TrajectoryEvent {}

    /** 用户消息注入 */
    record UserMessageInjected(String messageId, String contentPreview, Instant timestamp) implements TrajectoryEvent {}

    /** LLM 请求发送 */
    record LlmRequestSent(String provider, String model, int messageCount, Instant timestamp) implements TrajectoryEvent {}

    /** LLM 响应接收 */
    record LlmResponseReceived(String provider, String model, String stopReason, int tokens, Instant timestamp) implements TrajectoryEvent {}

    /** 工具调用开始 */
    record ToolCallStarted(String toolCallId, String toolName, String argumentsPreview, Instant timestamp) implements TrajectoryEvent {}

    /** 工具调用结束 */
    record ToolCallEnded(String toolCallId, String toolName, boolean success, String resultPreview, Instant timestamp) implements TrajectoryEvent {}

    /** 上下文压缩触发 */
    record ContextCompacted(int originalTokens, int compactedTokens, Instant timestamp) implements TrajectoryEvent {}

    /** 错误发生 */
    record ErrorOccurred(String errorType, String errorMessage, Instant timestamp) implements TrajectoryEvent {}

    /** Steering 消息注入 */
    record SteeringInjected(String source, int messageCount, Instant timestamp) implements TrajectoryEvent {}
}
