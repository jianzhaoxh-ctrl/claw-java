package com.openclaw.desktop.cron;

/**
 * Cron 任务定义。
 */
public record CronJob(
    String id,
    String name,
    CronSchedule schedule,
    CronPayload payload,
    boolean enabled,
    java.time.Instant createdAt,
    java.time.Instant lastRunAt,
    java.time.Instant nextRunAt,
    int runCount,
    int failureCount
) {
    public static CronJob create(String name, CronSchedule schedule, CronPayload payload) {
        return new CronJob(
            java.util.UUID.randomUUID().toString(),
            name, schedule, payload,
            true,
            java.time.Instant.now(), null, null,
            0, 0
        );
    }

    public CronJob withNextRun(java.time.Instant next) {
        return new CronJob(id, name, schedule, payload, enabled, createdAt, lastRunAt, next, runCount, failureCount);
    }

    public CronJob withRunResult(boolean success, java.time.Instant runAt) {
        return new CronJob(id, name, schedule, payload, enabled, createdAt, runAt, null,
            runCount + 1, success ? failureCount : failureCount + 1);
    }

    public CronJob withEnabled(boolean enabled) {
        return new CronJob(id, name, schedule, payload, enabled, createdAt, lastRunAt, nextRunAt, runCount, failureCount);
    }
}
