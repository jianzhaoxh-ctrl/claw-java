package com.openclaw.desktop.cron;

/**
 * 定时任务调度 — 对应 OpenClaw 的 cron 系统。
 * 支持一次性、固定间隔、cron 表达式三种调度方式。
 */
public sealed interface CronSchedule {

    record At(java.time.Instant at) implements CronSchedule {}
    record Every(long everyMs, Long anchorMs) implements CronSchedule {}
    record Cron(String expr, String tz) implements CronSchedule {}

    /**
     * 解析 cron 表达式（简化版，支持 5 段：分 时 日 月 周）。
     */
    static Cron parse(String expr) {
        return new Cron(expr, null);
    }

    static At at(java.time.Instant instant) {
        return new At(instant);
    }

    static Every every(long everyMs) {
        return new Every(everyMs, null);
    }

    static Every every(long everyMs, long anchorMs) {
        return new Every(everyMs, anchorMs);
    }
}
