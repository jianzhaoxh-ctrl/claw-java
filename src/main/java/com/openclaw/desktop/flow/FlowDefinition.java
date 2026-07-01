package com.openclaw.desktop.flow;

import java.time.Instant;
import java.util.Map;

/**
 * 流程定义 — 对应 OpenClaw 的 Flow / Workflow。
 *
 * <p>一个 Flow 是一组有序步骤的编排：
 * <ul>
 *   <li>每个步骤可调用工具或 LLM</li>
 *   <li>步骤之间有条件分支</li>
 *   <li>支持并行执行</li>
 *   <li>支持等待/超时</li>
 * </ul>
 */
public record FlowDefinition(
    String id,
    String name,
    String description,
    Map<String, StepDefinition> steps,
    String startStep,
    Instant createdAt
) {
    /**
     * 步骤定义。
     */
    public record StepDefinition(
        String id,
        String name,
        StepType type,
        String action,
        Map<String, String> inputs,
        Map<String, String> outputs,
        String nextStep,
        String condition,
        String errorStep
    ) {}

    /**
     * 步骤类型。
     */
    public enum StepType {
        /** 调用工具 */
        TOOL_CALL,
        /** 调用 LLM */
        LLM_CALL,
        /** 条件分支 */
        CONDITION,
        /** 并行执行 */
        PARALLEL,
        /** 等待事件 */
        WAIT,
        /** 子流程 */
        SUB_FLOW,
        /** 结束 */
        END
    }
}
