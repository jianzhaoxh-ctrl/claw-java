package com.openclaw.desktop.flow;

import java.time.Instant;
import java.util.Map;

/**
 * 流程实例 — 运行中的 Flow 对象。
 */
public record FlowInstance(
    String id,
    FlowDefinition definition,
    FlowStatus status,
    Map<String, Object> inputs,
    Map<String, Object> outputs,
    Instant createdAt
) {
    public boolean isRunning() {
        return status instanceof FlowStatus.Running;
    }

    public boolean isCompleted() {
        return status instanceof FlowStatus.Completed;
    }

    public boolean isPaused() {
        return status instanceof FlowStatus.Paused;
    }

    public boolean isFailed() {
        return status instanceof FlowStatus.Failed;
    }
}
