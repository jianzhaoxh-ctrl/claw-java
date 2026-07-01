package com.openclaw.desktop.task;

import java.time.Instant;
import java.util.Map;

/**
 * 后台任务定义 — 对应 OpenClaw 的 sessions_spawn 配置。
 *
 * <p>一个 Task 定义了：
 * <ul>
 *   <li>任务目标（objective）— 自然语言描述</li>
 *   <li>执行模型（model）— 可指定不同的 LLM 模型</li>
 *   <li>运行时（runtime）— subagent / isolated</li>
 *   <li>上下文模式（context）— isolated / fork</li>
 *   <li>任务名（taskName）— 稳定标识</li>
 * </ul>
 */
public record TaskDefinition(
    String taskName,
    String objective,
    String model,
    String runtime,
    String context,
    Map<String, String> options,
    Instant createdAt
) {
    public static TaskDefinition of(String objective) {
        return new TaskDefinition(
            null, objective, null, "subagent", "isolated",
            Map.of(), Instant.now()
        );
    }

    public static TaskDefinition named(String taskName, String objective) {
        return new TaskDefinition(
            taskName, objective, null, "subagent", "isolated",
            Map.of(), Instant.now()
        );
    }

    public static TaskDefinition withModel(String objective, String model) {
        return new TaskDefinition(
            null, objective, model, "subagent", "isolated",
            Map.of(), Instant.now()
        );
    }
}
