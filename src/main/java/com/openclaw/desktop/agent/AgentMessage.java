package com.openclaw.desktop.agent;

import java.util.List;

/**
 * 消息类型 — sealed 接口表达对话中的消息角色。
 * 对应 OpenClaw 的 Message 类型。
 */
public sealed interface AgentMessage {

    record SystemMessage(String content)                                    implements AgentMessage {}
    record UserMessage(String content)                                      implements AgentMessage {}
    record AssistantMessage(String content, List<ToolCall> toolCalls)       implements AgentMessage {}
    record ToolResultMessage(String toolCallId, String content)             implements AgentMessage {}
}
