package com.openclaw.desktop.cron;

/**
 * Cron 任务执行器接口。
 */
public interface CronExecutor {
    void execute(CronJob job) throws Exception;
}
