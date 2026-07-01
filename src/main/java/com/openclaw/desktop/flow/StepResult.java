package com.openclaw.desktop.flow;

import java.time.Instant;
import java.util.Map;

/**
 * 步骤执行结果。
 */
public record StepResult(
    String stepId,
    boolean success,
    Map<String, Object> outputs,
    String errorMessage,
    Instant executedAt
) {
    public static StepResult success(String stepId, Map<String, Object> outputs) {
        return new StepResult(stepId, true, outputs, null, Instant.now());
    }

    public static StepResult failure(String stepId, String error) {
        return new StepResult(stepId, false, Map.of(), error, Instant.now());
    }
}
