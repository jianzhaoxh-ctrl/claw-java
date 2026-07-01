package com.openclaw.desktop.task;

import java.time.Instant;
import java.util.Map;

/**
 * 后台任务状态 — 对应 OpenClaw 的 sessions_spawn subagent 任务。
 */
public sealed interface TaskStatus {

    /** 任务已创建，等待执行 */
    record Pending(Instant createdAt) implements TaskStatus {}

    /** 任务正在执行 */
    record Running(Instant startedAt) implements TaskStatus {}

    /** 任务已完成 */
    record Completed(Instant completedAt, String resultSummary) implements TaskStatus {}

    /** 任务失败 */
    record Failed(Instant failedAt, String errorMessage) implements TaskStatus {}

    /** 任务被取消 */
    record Cancelled(Instant cancelledAt, String reason) implements TaskStatus {}
}
