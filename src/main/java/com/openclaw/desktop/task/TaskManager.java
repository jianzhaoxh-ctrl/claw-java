package com.openclaw.desktop.task;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 后台任务管理器 — 对应 OpenClaw 的 subagent sessions_spawn 机制。
 *
 * <p>核心功能：
 * <ul>
 *   <li>spawn — 创建并启动后台任务</li>
 *   <li>list — 列出所有活跃任务</li>
 *   <li>cancel — 取消任务</li>
 *   <li>waitFor — 等待任务完成</li>
 * </ul>
 *
 * <p>每个任务在独立的 Agent 实例中运行，使用指定的模型和配置。
 * 任务完成后推送完成事件到主会话。
 */
public class TaskManager {

    private static final Logger log = LoggerFactory.getLogger(TaskManager.class);

    private final ConcurrentHashMap<String, TaskInstance> tasks = new ConcurrentHashMap<>();

    /**
     * 创建并启动一个后台任务。
     *
     * @param definition 任务定义
     * @return 任务实例
     */
    public Mono<TaskInstance> spawn(TaskDefinition definition) {
        var taskId = UUID.randomUUID().toString();
        var taskName = definition.taskName() != null
            ? definition.taskName()
            : "task-" + taskId.substring(0, 8);

        var instance = new TaskInstance(
            taskId,
            taskName,
            definition,
            new TaskStatus.Pending(Instant.now()),
            Instant.now(),
            Map.of()
        );

        tasks.put(taskId, instance);
        log.info("Task spawned: id={}, name={}, objective={}",
            taskId, taskName, definition.objective());

        // 立即标记为 Running
        var running = new TaskInstance(
            taskId, taskName, definition,
            new TaskStatus.Running(Instant.now()),
            instance.createdAt(), instance.context()
        );
        tasks.put(taskId, running);

        return Mono.just(running);
    }

    /**
     * 获取任务实例。
     */
    public Mono<TaskInstance> get(String taskId) {
        var instance = tasks.get(taskId);
        if (instance == null) {
            return Mono.error(new RuntimeException("Task not found: " + taskId));
        }
        return Mono.just(instance);
    }

    /**
     * 通过 taskName 获取任务实例。
     */
    public Mono<TaskInstance> getByTaskName(String taskName) {
        var instance = tasks.values().stream()
            .filter(t -> t.taskName().equals(taskName))
            .findFirst();
        if (instance.isEmpty()) {
            return Mono.error(new RuntimeException("Task not found by name: " + taskName));
        }
        return Mono.just(instance.get());
    }

    /**
     * 列出所有活跃任务。
     */
    public Flux<TaskInstance> listActive() {
        return Flux.fromIterable(tasks.values())
            .filter(t -> t.isPending() || t.isRunning());
    }

    /**
     * 列出所有任务（包括已完成）。
     */
    public Flux<TaskInstance> listAll() {
        return Flux.fromIterable(tasks.values());
    }

    /**
     * 取消任务。
     */
    public Mono<Void> cancel(String taskId) {
        var instance = tasks.get(taskId);
        if (instance == null) {
            return Mono.error(new RuntimeException("Task not found: " + taskId));
        }
        if (!instance.isRunning() && !instance.isPending()) {
            log.warn("Task {} is not running, cannot cancel", taskId);
            return Mono.empty();
        }

        var cancelled = new TaskInstance(
            instance.id(), instance.taskName(), instance.definition(),
            new TaskStatus.Cancelled(Instant.now(), "user cancel"),
            instance.createdAt(), instance.context()
        );
        tasks.put(taskId, cancelled);
        log.info("Task cancelled: id={}", taskId);
        return Mono.empty();
    }

    /**
     * 标记任务完成。
     */
    public Mono<Void> complete(String taskId, String resultSummary) {
        var instance = tasks.get(taskId);
        if (instance == null) {
            return Mono.error(new RuntimeException("Task not found: " + taskId));
        }

        var completed = new TaskInstance(
            instance.id(), instance.taskName(), instance.definition(),
            new TaskStatus.Completed(Instant.now(), resultSummary),
            instance.createdAt(), instance.context()
        );
        tasks.put(taskId, completed);
        log.info("Task completed: id={}, summary={}", taskId,
            resultSummary.substring(0, Math.min(50, resultSummary.length())));
        return Mono.empty();
    }

    /**
     * 标记任务失败。
     */
    public Mono<Void> fail(String taskId, String errorMessage) {
        var instance = tasks.get(taskId);
        if (instance == null) {
            return Mono.error(new RuntimeException("Task not found: " + taskId));
        }

        var failed = new TaskInstance(
            instance.id(), instance.taskName(), instance.definition(),
            new TaskStatus.Failed(Instant.now(), errorMessage),
            instance.createdAt(), instance.context()
        );
        tasks.put(taskId, failed);
        log.error("Task failed: id={}, error={}", taskId, errorMessage);
        return Mono.empty();
    }

    /**
     * 等待任务完成（阻塞直到任务结束）。
     */
    public Mono<TaskResult> waitFor(String taskId) {
        return Mono.defer(() -> {
            var instance = tasks.get(taskId);
            if (instance == null) {
                return Mono.error(new RuntimeException("Task not found: " + taskId));
            }
            if (instance.isCompleted()) {
                var status = (TaskStatus.Completed) instance.status();
                return Mono.just(TaskResult.success(taskId, instance.taskName(), status.resultSummary()));
            }
            if (instance.isFailed()) {
                var status = (TaskStatus.Failed) instance.status();
                return Mono.just(TaskResult.failure(taskId, instance.taskName(), status.errorMessage()));
            }
            if (instance.isCancelled()) {
                var status = (TaskStatus.Cancelled) instance.status();
                return Mono.just(TaskResult.failure(taskId, instance.taskName(), "Cancelled: " + status.reason()));
            }
            // 尚未完成，稍后重试
            return Mono.empty();
        });
    }

    /**
     * 清理已完成的任务。
     */
    public Mono<Void> cleanup() {
        var toRemove = tasks.values().stream()
            .filter(t -> t.isCompleted() || t.isFailed() || t.isCancelled())
            .map(TaskInstance::id)
            .toList();
        for (var id : toRemove) {
            tasks.remove(id);
        }
        log.info("Cleaned up {} completed tasks", toRemove.size());
        return Mono.empty();
    }

    /**
     * 活跃任务数量。
     */
    public int activeCount() {
        return (int) tasks.values().stream()
            .filter(t -> t.isPending() || t.isRunning())
            .count();
    }

    /**
     * 总任务数量。
     */
    public int totalCount() {
        return tasks.size();
    }
}
