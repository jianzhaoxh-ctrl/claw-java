package com.openclaw.desktop.agent;

import reactor.core.publisher.Mono;

/**
 * 中断信号 — 控制 Agent Loop 的暂停/恢复/取消。
 * 对应 OpenClaw 的 AbortSignal。
 *
 * <p>用法：
 * <pre>
 * var signal = new AbortSignal();
 * // 暂停
 * signal.pause();
 * // 恢复
 * signal.resume();
 * // 取消
 * signal.abort("用户请求停止");
 * </pre>
 *
 * <p>AgentLoop 在每个循环迭代中检查 isAborted/isPaused。
 */
public class AbortSignal {

    private volatile boolean aborted = false;
    private volatile boolean paused = false;
    private volatile String abortReason = null;

    /** 请求中断 — 终止 Agent Loop */
    public void abort(String reason) {
        this.aborted = true;
        this.abortReason = reason;
    }

    /** 请求中断（无原因） */
    public void abort() {
        abort("AbortSignal triggered");
    }

    /** 暂停 — Agent Loop 暂停执行，等待 resume */
    public void pause() {
        this.paused = true;
    }

    /** 恢复 — Agent Loop 继续执行 */
    public void resume() {
        this.paused = false;
    }

    /** 检查是否已中断 */
    public boolean isAborted() { return aborted; }

    /** 检查是否已暂停 */
    public boolean isPaused() { return paused; }

    /** 获取中断原因 */
    public String abortReason() { return abortReason; }

    /** 等待暂停恢复 — 在 Agent Loop 中使用 */
    public Mono<Void> waitForResume() {
        if (!paused) return Mono.empty();
        return Mono.defer(() -> {
            if (!paused) return Mono.empty();
            // 简化实现：轮询等待恢复
            return Mono.delay(java.time.Duration.ofMillis(100))
                .repeat(() -> paused)
                .then();
        });
    }

    /** 重置 — 将信号恢复到初始状态 */
    public void reset() {
        aborted = false;
        paused = false;
        abortReason = null;
    }

    @Override
    public String toString() {
        var state = aborted ? "ABORTED" : (paused ? "PAUSED" : "ACTIVE");
        var reason = abortReason != null ? "(" + abortReason + ")" : "";
        return "AbortSignal[" + state + reason + "]";
    }
}
