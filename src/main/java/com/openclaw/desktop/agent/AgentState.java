package com.openclaw.desktop.agent;

import java.util.List;

/**
 * Agent 状态 — 运行时的动态状态快照。
 */
public record AgentState(
    List<AgentMessage> messages,
    boolean streaming,
    String errorMessage
) {
    public static AgentState initial() {
        return new AgentState(List.of(), false, null);
    }
}
