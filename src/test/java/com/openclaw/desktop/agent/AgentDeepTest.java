package com.openclaw.desktop.agent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Agent 深化系统测试 — 验证 AbortSignal、GoalTracker、SubAgentManager、AgentContext。
 */
class AgentDeepTest {

    @BeforeEach
    void setUp() {
    }

    @Test
    void testAbortSignalAbort() {
        var signal = new AbortSignal();
        assertFalse(signal.isAborted());
        assertFalse(signal.isPaused());

        signal.abort("test reason");
        assertTrue(signal.isAborted());
        assertEquals("test reason", signal.abortReason());
    }

    @Test
    void testAbortSignalPauseResume() {
        var signal = new AbortSignal();
        assertFalse(signal.isPaused());

        signal.pause();
        assertTrue(signal.isPaused());

        signal.resume();
        assertFalse(signal.isPaused());
    }

    @Test
    void testAbortSignalReset() {
        var signal = new AbortSignal();
        signal.abort("reason");
        signal.pause();
        assertTrue(signal.isAborted());
        assertTrue(signal.isPaused());

        signal.reset();
        assertFalse(signal.isAborted());
        assertFalse(signal.isPaused());
        assertNull(signal.abortReason());
    }

    @Test
    void testAbortSignalToString() {
        var signal = new AbortSignal();
        assertTrue(signal.toString().contains("ACTIVE"));

        signal.abort("stop");
        assertTrue(signal.toString().contains("ABORTED"));

        signal.reset();
        signal.pause();
        assertTrue(signal.toString().contains("PAUSED"));
    }

    @Test
    void testGoalTrackerCreateGoal() {
        var tracker = new GoalTracker();
        assertNull(tracker.currentGoal());

        var goal = tracker.createGoal("Build a web app");
        assertNotNull(goal);
        assertEquals("Build a web app", goal.objective());
        assertEquals(GoalTracker.GoalStatus.PENDING, goal.status());
        assertEquals(goal.id(), tracker.currentGoal().id());
    }

    @Test
    void testGoalTrackerCompleteGoal() {
        var tracker = new GoalTracker();
        var goal = tracker.createGoal("Test goal");
        tracker.completeGoal(goal.id(), "Done!");
        var updated = tracker.getGoal(goal.id());
        assertEquals(GoalTracker.GoalStatus.COMPLETE, updated.status());
        assertEquals("Done!", updated.note());
        assertTrue(updated.isComplete());
        assertNull(tracker.currentGoal());
    }

    @Test
    void testGoalTrackerBlockGoal() {
        var tracker = new GoalTracker();
        var goal = tracker.createGoal("Blocked goal");
        tracker.blockGoal(goal.id(), "Cannot proceed");
        var updated = tracker.getGoal(goal.id());
        assertEquals(GoalTracker.GoalStatus.BLOCKED, updated.status());
        assertTrue(updated.isBlocked());
    }

    @Test
    void testGoalTrackerTokenBudget() {
        var tracker = new GoalTracker();
        var goal = tracker.createGoal("Budgeted goal", 1000);
        assertEquals(1000, goal.tokenBudget());
        assertFalse(goal.isBudgetExceeded());

        tracker.updateTokenUsage(goal.id(), 800);
        var updated = tracker.getGoal(goal.id());
        assertEquals(800, updated.tokenUsage());
        assertFalse(updated.isBudgetExceeded());

        tracker.updateTokenUsage(goal.id(), 1000);
        var exceeded = tracker.getGoal(goal.id());
        assertTrue(exceeded.isBudgetExceeded());
    }

    @Test
    void testGoalTrackerAllGoals() {
        var tracker = new GoalTracker();
        tracker.createGoal("Goal 1");
        tracker.createGoal("Goal 2");
        tracker.createGoal("Goal 3");
        assertEquals(3, tracker.allGoals().size());
    }

    @Test
    void testGoalTrackerClearCurrentGoal() {
        var tracker = new GoalTracker();
        tracker.createGoal("To clear");
        assertNotNull(tracker.currentGoal());
        tracker.clearCurrentGoal();
        assertNull(tracker.currentGoal());
    }

    @Test
    void testSubAgentConfigDefaults() {
        var config = SubAgentManager.SubAgentConfig.defaults("parent-1");
        assertEquals("parent-1", config.parentAgentId());
        assertEquals("SubAgent", config.name());
        assertEquals("gpt-4o", config.modelId());
        assertTrue(config.isolatedContext());
    }

    @Test
    void testSubAgentStatusEnum() {
        var statuses = SubAgentManager.SubAgentStatus.values();
        assertEquals(4, statuses.length);
    }

    @Test
    void testAgentEventLegacyAdapterCoverage() {
        // 验证 v2.0 AgentEvent 类型完整性
        // 生命周期事件
        assertNotNull(AgentEvent.AgentStart.class);
        assertNotNull(AgentEvent.AgentEnd.class);
        assertNotNull(AgentEvent.TurnStart.class);
        assertNotNull(AgentEvent.TurnEnd.class);
        assertNotNull(AgentEvent.MessageStart.class);
        assertNotNull(AgentEvent.MessageEnd.class);
        // 流式事件
        assertNotNull(AgentEvent.TextStart.class);
        assertNotNull(AgentEvent.TextDelta.class);
        assertNotNull(AgentEvent.TextEnd.class);
        assertNotNull(AgentEvent.ThinkingStart.class);
        assertNotNull(AgentEvent.ThinkingDelta.class);
        assertNotNull(AgentEvent.ThinkingEnd.class);
        assertNotNull(AgentEvent.ToolCallStart.class);
        assertNotNull(AgentEvent.ToolCallDelta.class);
        assertNotNull(AgentEvent.ToolCallEnd.class);
        assertNotNull(AgentEvent.Usage.class);
        assertNotNull(AgentEvent.Error.class);
    }

    @Test
    void testReasoningLevelValues() {
        var levels = ReasoningLevel.values();
        assertEquals(4, levels.length); // OFF, LOW, MEDIUM, HIGH
    }
}
