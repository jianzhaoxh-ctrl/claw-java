package com.openclaw.desktop.task;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * TaskManager 单元测试。
 */
class TaskManagerTest {

    @Test
    void testSpawnTask() {
        var manager = new TaskManager();
        var definition = TaskDefinition.of("Test objective");
        var instance = manager.spawn(definition).block();

        assertNotNull(instance, "Spawned task should not be null");
        assertNotNull(instance.id(), "Task ID should not be null");
        assertTrue(instance.isRunning(), "Newly spawned task should be running");
        assertEquals(1, manager.activeCount());
    }

    @Test
    void testSpawnNamedTask() {
        var manager = new TaskManager();
        var definition = TaskDefinition.named("my-task", "Do something");
        var instance = manager.spawn(definition).block();

        assertEquals("my-task", instance.taskName());
    }

    @Test
    void testCompleteTask() {
        var manager = new TaskManager();
        var definition = TaskDefinition.of("Test");
        var instance = manager.spawn(definition).block();
        var taskId = instance.id();

        manager.complete(taskId, "Task completed successfully").block();

        var completed = manager.get(taskId).block();
        assertTrue(completed.isCompleted());
    }

    @Test
    void testFailTask() {
        var manager = new TaskManager();
        var definition = TaskDefinition.of("Test");
        var instance = manager.spawn(definition).block();
        var taskId = instance.id();

        manager.fail(taskId, "Something went wrong").block();

        var failed = manager.get(taskId).block();
        assertTrue(failed.isFailed());
    }

    @Test
    void testCancelTask() {
        var manager = new TaskManager();
        var definition = TaskDefinition.of("Test");
        var instance = manager.spawn(definition).block();
        var taskId = instance.id();

        manager.cancel(taskId).block();

        var cancelled = manager.get(taskId).block();
        assertTrue(cancelled.isCancelled());
    }

    @Test
    void testGetByTaskName() {
        var manager = new TaskManager();
        var definition = TaskDefinition.named("unique-task", "Objective");
        manager.spawn(definition).block();

        var found = manager.getByTaskName("unique-task").block();
        assertNotNull(found);
        assertEquals("unique-task", found.taskName());
    }

    @Test
    void testListActive() {
        var manager = new TaskManager();
        manager.spawn(TaskDefinition.of("Task 1")).block();
        manager.spawn(TaskDefinition.of("Task 2")).block();

        var active = manager.listActive().collectList().block();
        assertEquals(2, active.size());
    }

    @Test
    void testCleanup() {
        var manager = new TaskManager();
        var instance = manager.spawn(TaskDefinition.of("Task")).block();
        manager.complete(instance.id(), "Done").block();

        assertEquals(1, manager.totalCount());
        manager.cleanup().block();
        assertEquals(0, manager.totalCount());
    }
}
