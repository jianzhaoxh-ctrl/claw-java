package com.openclaw.desktop.flow;

import java.time.Instant;
import java.util.Map;

/**
 * 流程执行状态 — sealed 接口。
 */
public sealed interface FlowStatus {

    /** 流程尚未启动 */
    record NotStarted() implements FlowStatus {}

    /** 流程正在执行 */
    record Running(Instant startedAt, String currentStepId) implements FlowStatus {}

    /** 流程已完成 */
    record Completed(Instant completedAt, Map<String, Object> outputs) implements FlowStatus {}

    /** 流程失败 */
    record Failed(Instant failedAt, String errorMessage, String failedStepId) implements FlowStatus {}

    /** 流程被暂停 */
    record Paused(Instant pausedAt, String pausedStepId, String reason) implements FlowStatus {}
}
