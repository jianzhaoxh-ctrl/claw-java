package com.openclaw.desktop.task;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 后台任务结果 — 任务完成后的输出。
 */
public record TaskResult(
    String taskId,
    String taskName,
    TaskStatus status,
    String resultSummary,
    List<String> outputFiles,
    Map<String, Object> metadata,
    Instant completedAt
) {
    public static TaskResult success(String taskId, String taskName, String summary) {
        return new TaskResult(taskId, taskName,
            new TaskStatus.Completed(Instant.now(), summary),
            summary, List.of(), Map.of(), Instant.now());
    }

    public static TaskResult failure(String taskId, String taskName, String error) {
        return new TaskResult(taskId, taskName,
            new TaskStatus.Failed(Instant.now(), error),
            null, List.of(), Map.of(), Instant.now());
    }
}
