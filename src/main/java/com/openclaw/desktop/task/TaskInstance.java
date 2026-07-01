package com.openclaw.desktop.task;

import java.time.Instant;
import java.util.Map;

/**
 * 后台任务实例 — 运行中的任务对象。
 */
public record TaskInstance(
    String id,
    String taskName,
    TaskDefinition definition,
    TaskStatus status,
    Instant createdAt,
    Map<String, Object> context
) {
    public boolean isRunning() {
        return status instanceof TaskStatus.Running;
    }

    public boolean isCompleted() {
        return status instanceof TaskStatus.Completed;
    }

    public boolean isFailed() {
        return status instanceof TaskStatus.Failed;
    }

    public boolean isCancelled() {
        return status instanceof TaskStatus.Cancelled;
    }

    public boolean isPending() {
        return status instanceof TaskStatus.Pending;
    }
}
