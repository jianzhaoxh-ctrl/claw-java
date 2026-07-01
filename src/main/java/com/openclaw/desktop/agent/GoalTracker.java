package com.openclaw.desktop.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 目标追踪系统 — Agent 的当前目标和进度。
 * 对应 OpenClaw 的 Goal 系统。
 *
 * <p>Agent 可以设置和追踪目标，系统自动管理目标状态。
 */
public class GoalTracker {

    private static final Logger log = LoggerFactory.getLogger(GoalTracker.class);
    private final Map<String, Goal> goals = new ConcurrentHashMap<>();
    private volatile Goal currentGoal;

    /** 创建目标 */
    public Goal createGoal(String objective, double tokenBudget) {
        var goal = new Goal(
            "goal_" + System.currentTimeMillis() + "_" + goals.size(),
            objective,
            GoalStatus.PENDING,
            tokenBudget,
            0.0,
            System.currentTimeMillis(),
            null,
            null
        );
        goals.put(goal.id(), goal);
        if (currentGoal == null) {
            currentGoal = goal;
        }
        log.info("Goal created: id={}, objective={}", goal.id(), objective);
        return goal;
    }

    /** 创建目标（无 token 预算） */
    public Goal createGoal(String objective) {
        return createGoal(objective, 0);
    }

    /** 获取当前目标 */
    public Goal currentGoal() { return currentGoal; }

    /** 获取指定目标 */
    public Goal getGoal(String id) { return goals.get(id); }

    /** 标记目标完成 */
    public void completeGoal(String id, String note) {
        var goal = goals.get(id);
        if (goal != null) {
            goals.put(id, new Goal(goal.id(), goal.objective(), GoalStatus.COMPLETE,
                goal.tokenBudget(), goal.tokenUsage(), goal.createdAt(),
                System.currentTimeMillis(), note));
            if (currentGoal != null && currentGoal.id().equals(id)) {
                currentGoal = null;
            }
            log.info("Goal completed: id={}, note={}", id, note);
        }
    }

    /** 标记目标阻塞 */
    public void blockGoal(String id, String note) {
        var goal = goals.get(id);
        if (goal != null) {
            goals.put(id, new Goal(goal.id(), goal.objective(), GoalStatus.BLOCKED,
                goal.tokenBudget(), goal.tokenUsage(), goal.createdAt(),
                System.currentTimeMillis(), note));
            log.warn("Goal blocked: id={}, note={}", id, note);
        }
    }

    /** 更新目标 token 使用量 */
    public void updateTokenUsage(String id, double usage) {
        var goal = goals.get(id);
        if (goal != null) {
            goals.put(id, new Goal(goal.id(), goal.objective(), goal.status(),
                goal.tokenBudget(), usage, goal.createdAt(),
                goal.completedAt(), goal.note()));
        }
    }

    /** 清除当前目标 */
    public void clearCurrentGoal() {
        if (currentGoal != null) {
            log.info("Goal cleared: id={}", currentGoal.id());
            currentGoal = null;
        }
    }

    /** 所有目标 */
    public java.util.Collection<Goal> allGoals() { return goals.values(); }

    // ========== 类型定义 ==========

    public enum GoalStatus {
        PENDING,    // 目标已创建，尚未开始
        ACTIVE,     // 目标正在进行
        COMPLETE,   // 目标已完成
        BLOCKED     // 目标被阻塞
    }

    public record Goal(
        String id,
        String objective,
        GoalStatus status,
        double tokenBudget,
        double tokenUsage,
        long createdAt,
        Long completedAt,
        String note
    ) {
        /** 是否已完成 */
        public boolean isComplete() { return status == GoalStatus.COMPLETE; }

        /** 是否被阻塞 */
        public boolean isBlocked() { return status == GoalStatus.BLOCKED; }

        /** 是否 token 预算耗尽 */
        public boolean isBudgetExceeded() {
            return tokenBudget > 0 && tokenUsage >= tokenBudget;
        }
    }
}
