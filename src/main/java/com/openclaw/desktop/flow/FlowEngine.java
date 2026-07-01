package com.openclaw.desktop.flow;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 流程引擎 — 对应 OpenClaw 的 FlowEngine。
 *
 * <p>编排多步骤工作流，支持：
 * <ul>
 *   <li>顺序执行</li>
 *   <li>条件分支</li>
 *   <li>并行执行</li>
 *   <li>错误处理</li>
 *   <li>暂停/恢复</li>
 * </ul>
 */
public class FlowEngine {

    private static final Logger log = LoggerFactory.getLogger(FlowEngine.class);

    private final ConcurrentHashMap<String, FlowInstance> instances = new ConcurrentHashMap<>();

    /**
     * 启动流程。
     */
    public Mono<FlowInstance> start(FlowDefinition definition, Map<String, Object> inputs) {
        var instanceId = UUID.randomUUID().toString();
        var instance = new FlowInstance(
            instanceId,
            definition,
            new FlowStatus.Running(Instant.now(), definition.startStep()),
            inputs,
            Map.of(),
            Instant.now()
        );
        instances.put(instanceId, instance);
        log.info("Flow started: id={}, name={}, startStep={}",
            instanceId, definition.name(), definition.startStep());
        return Mono.just(instance);
    }

    /**
     * 执行下一步。
     */
    public Mono<StepResult> executeStep(String instanceId, String stepId) {
        var instance = instances.get(instanceId);
        if (instance == null) {
            return Mono.error(new RuntimeException("Flow instance not found: " + instanceId));
        }
        if (!(instance.status() instanceof FlowStatus.Running)) {
            return Mono.error(new RuntimeException("Flow is not running: " + instanceId));
        }

        var definition = instance.definition();
        var stepDef = definition.steps().get(stepId);
        if (stepDef == null) {
            return Mono.error(new RuntimeException("Step not found: " + stepId));
        }

        log.info("Executing step: flow={}, step={}, type={}",
            instanceId, stepId, stepDef.type());

        // 根据 StepType 执行不同逻辑
        return switch (stepDef.type()) {
            case FlowDefinition.StepType.TOOL_CALL -> executeToolCall(instance, stepDef);
            case FlowDefinition.StepType.LLM_CALL -> executeLlmCall(instance, stepDef);
            case FlowDefinition.StepType.CONDITION -> executeCondition(instance, stepDef);
            case FlowDefinition.StepType.PARALLEL -> executeParallel(instance, stepDef);
            case FlowDefinition.StepType.WAIT -> executeWait(instance, stepDef);
            case FlowDefinition.StepType.SUB_FLOW -> executeSubFlow(instance, stepDef);
            case FlowDefinition.StepType.END -> executeEnd(instance, stepDef);
        };
    }

    /**
     * 获取流程实例。
     */
    public Mono<FlowInstance> getInstance(String instanceId) {
        var instance = instances.get(instanceId);
        if (instance == null) {
            return Mono.error(new RuntimeException("Flow instance not found: " + instanceId));
        }
        return Mono.just(instance);
    }

    /**
     * 暂停流程。
     */
    public Mono<Void> pause(String instanceId, String reason) {
        var instance = instances.get(instanceId);
        if (instance == null) return Mono.error(new RuntimeException("Flow not found"));

        var running = (FlowStatus.Running) instance.status();
        var paused = new FlowInstance(
            instance.id(), instance.definition(),
            new FlowStatus.Paused(Instant.now(), running.currentStepId(), reason),
            instance.inputs(), instance.outputs(), instance.createdAt()
        );
        instances.put(instanceId, paused);
        log.info("Flow paused: id={}, reason={}", instanceId, reason);
        return Mono.empty();
    }

    /**
     * 恢复流程。
     */
    public Mono<Void> resume(String instanceId) {
        var instance = instances.get(instanceId);
        if (instance == null) return Mono.error(new RuntimeException("Flow not found"));

        var paused = (FlowStatus.Paused) instance.status();
        var resumed = new FlowInstance(
            instance.id(), instance.definition(),
            new FlowStatus.Running(Instant.now(), paused.pausedStepId()),
            instance.inputs(), instance.outputs(), instance.createdAt()
        );
        instances.put(instanceId, resumed);
        log.info("Flow resumed: id={}", instanceId);
        return Mono.empty();
    }

    /**
     * 取消流程。
     */
    public Mono<Void> cancel(String instanceId) {
        instances.remove(instanceId);
        log.info("Flow cancelled: id={}", instanceId);
        return Mono.empty();
    }

    // ---- 步骤执行器 ----

    private Mono<StepResult> executeToolCall(FlowInstance instance, FlowDefinition.StepDefinition step) {
        // 实际调用 ToolRegistry — 此处为框架骨架
        log.info("Tool call step: action={}", step.action());
        return Mono.just(StepResult.success(step.id(), Map.of("result", "tool call placeholder")));
    }

    private Mono<StepResult> executeLlmCall(FlowInstance instance, FlowDefinition.StepDefinition step) {
        log.info("LLM call step: action={}", step.action());
        return Mono.just(StepResult.success(step.id(), Map.of("result", "llm call placeholder")));
    }

    private Mono<StepResult> executeCondition(FlowInstance instance, FlowDefinition.StepDefinition step) {
        log.info("Condition step: condition={}", step.condition());
        // 简化：总是走 nextStep
        return Mono.just(StepResult.success(step.id(), Map.of("branch", step.nextStep())));
    }

    private Mono<StepResult> executeParallel(FlowInstance instance, FlowDefinition.StepDefinition step) {
        log.info("Parallel step: action={}", step.action());
        return Mono.just(StepResult.success(step.id(), Map.of("parallel", "placeholder")));
    }

    private Mono<StepResult> executeWait(FlowInstance instance, FlowDefinition.StepDefinition step) {
        log.info("Wait step: action={}", step.action());
        // 暂停流程等待外部事件
        return pause(instance.id(), "waiting for: " + step.action())
            .then(Mono.just(StepResult.success(step.id(), Map.of("waiting", step.action()))));
    }

    private Mono<StepResult> executeSubFlow(FlowInstance instance, FlowDefinition.StepDefinition step) {
        log.info("Sub-flow step: action={}", step.action());
        return Mono.just(StepResult.success(step.id(), Map.of("subflow", "placeholder")));
    }

    private Mono<StepResult> executeEnd(FlowInstance instance, FlowDefinition.StepDefinition step) {
        log.info("End step: flow={}", instance.id());
        var completed = new FlowInstance(
            instance.id(), instance.definition(),
            new FlowStatus.Completed(Instant.now(), instance.outputs()),
            instance.inputs(), instance.outputs(), instance.createdAt()
        );
        instances.put(instance.id(), completed);
        return Mono.just(StepResult.success(step.id(), Map.of()));
    }

    /**
     * 流程实例数量。
     */
    public int count() { return instances.size(); }
}
