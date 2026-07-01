package com.openclaw.desktop.cron;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Cron 调度器 — 管理定时任务的调度和执行。
 * 对应 OpenClaw 的 cron 系统。
 *
 * <p>支持三种调度方式：
 * <ul>
 *   <li>{@link CronSchedule.At} 一次性：指定时刻执行</li>
 *   <li>{@link CronSchedule.Every} 固定间隔：每 N 毫秒执行</li>
 *   <li>{@link CronSchedule.Cron} Cron 表达式：使用 {@link CronExpression}（Quartz 风格 6-7 位）</li>
 * </ul>
 *
 * <p>可选持久化：传入 {@link CronJobStore} 后，任务会在启动时恢复、增删改时保存、执行后更新运行结果。
 */
public class CronScheduler {

    private static final Logger log = LoggerFactory.getLogger(CronScheduler.class);

    private final Map<String, CronJob> jobs = new ConcurrentHashMap<>();
    private final ScheduledExecutorService executor;
    private final CronExecutor cronExecutor;
    private final CronJobStore store;  // 可为 null（无持久化）
    private final AtomicBoolean running = new AtomicBoolean(false);

    public CronScheduler(CronExecutor cronExecutor) {
        this(cronExecutor, null);
    }

    public CronScheduler(CronExecutor cronExecutor, CronJobStore store) {
        this.cronExecutor = cronExecutor;
        this.store = store;
        this.executor = Executors.newScheduledThreadPool(2, r -> {
            var t = new Thread(r, "cron-scheduler");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * 启动调度器。若配置了持久化存储，会先恢复已保存的任务。
     */
    public void start() {
        // 从持久化存储恢复任务
        if (store != null) {
            try {
                var restored = store.loadAll();
                for (var job : restored) {
                    // 重新计算 nextRun（上次运行的 nextRun 可能已过期）
                    var nextRun = calculateNextRun(job.schedule(), Instant.now());
                    jobs.put(job.id(), job.withNextRun(nextRun));
                }
                log.info("Restored {} cron job(s) from store", restored.size());
            } catch (Exception e) {
                log.error("Failed to restore cron jobs from store: {}", e.getMessage(), e);
            }
        }
        running.set(true);
        executor.scheduleAtFixedRate(this::tick, 0, 30, TimeUnit.SECONDS);
        log.info("CronScheduler started ({} jobs)", jobs.size());
    }

    public void stop() {
        running.set(false);
        executor.shutdown();
        log.info("CronScheduler stopped");
    }

    public CronJob add(CronJob job) {
        var nextRun = calculateNextRun(job.schedule(), Instant.now());
        var scheduled = job.withNextRun(nextRun);
        jobs.put(job.id(), scheduled);
        persist(scheduled);
        log.info("Cron job added: {} ({})", job.name(), job.id());
        return scheduled;
    }

    public CronJob remove(String id) {
        var removed = jobs.remove(id);
        if (removed != null) {
            log.info("Cron job removed: {} ({})", removed.name(), id);
            if (store != null) {
                try { store.delete(id); } catch (Exception e) {
                    log.warn("Failed to delete cron job from store: {}", e.getMessage());
                }
            }
        }
        return removed;
    }

    public CronJob update(String id, CronJob updated) {
        var nextRun = calculateNextRun(updated.schedule(), Instant.now());
        var toStore = updated.withNextRun(nextRun);
        jobs.put(id, toStore);
        persist(toStore);
        log.info("Cron job updated: {} ({})", updated.name(), id);
        return toStore;
    }

    public CronJob get(String id) { return jobs.get(id); }

    public Collection<CronJob> list() {
        return Collections.unmodifiableCollection(jobs.values());
    }

    /** 启用/禁用任务。 */
    public CronJob setEnabled(String id, boolean enabled) {
        var job = jobs.get(id);
        if (job == null) return null;
        var updated = job.withEnabled(enabled);
        jobs.put(id, updated);
        persist(updated);
        return updated;
    }

    public CronJob runNow(String id) {
        var job = jobs.get(id);
        if (job == null) return null;
        executeJob(job);
        return jobs.get(id);
    }

    // ---- 调度循环 ----

    private void tick() {
        if (!running.get()) return;
        var now = Instant.now();
        for (var job : new ArrayList<>(jobs.values())) {
            if (!job.enabled()) continue;
            if (job.nextRunAt() != null && !now.isBefore(job.nextRunAt())) {
                executeJob(job);
                var nextRun = calculateNextRun(job.schedule(), now);
                var updated = job.withNextRun(nextRun);
                jobs.put(job.id(), updated);
            }
        }
    }

    private void executeJob(CronJob job) {
        log.info("Executing cron job: {} ({})", job.name(), job.id());
        try {
            cronExecutor.execute(job);
            var updated = job.withRunResult(true, Instant.now());
            jobs.put(job.id(), updated);
            persist(updated);
        } catch (Exception e) {
            log.error("Cron job execution failed: {} - {}", job.name(), e.getMessage(), e);
            var updated = job.withRunResult(false, Instant.now());
            jobs.put(job.id(), updated);
            persist(updated);
        }
    }

    // ---- 下次运行时间计算 ----

    private Instant calculateNextRun(CronSchedule schedule, Instant from) {
        return switch (schedule) {
            case CronSchedule.At at -> at.at().isAfter(from) ? at.at() : null;
            case CronSchedule.Every every -> {
                var base = every.anchorMs() != null
                    ? from.plusMillis(every.anchorMs() % every.everyMs())
                    : from;
                yield base.plusMillis(every.everyMs());
            }
            case CronSchedule.Cron cron -> calculateCronNextRun(cron.expr(), from, cron.tz());
        };
    }

    /**
     * 使用 {@link CronExpression} 计算下一次运行时间（支持 5/6/7 位 Quartz 风格表达式）。
     */
    private Instant calculateCronNextRun(String expr, Instant from, String tz) {
        try {
            var cron = CronExpression.parse(expr);
            var zone = tz != null ? ZoneId.of(tz) : ZoneId.systemDefault();
            var next = cron.nextTimeAfter(from, zone);
            if (next == null) {
                log.warn("Cron expression '{}' has no future match", expr);
            }
            return next;
        } catch (Exception e) {
            log.warn("Invalid cron expression: {} - {}", expr, e.getMessage());
            return from.plus(Duration.ofHours(1));
        }
    }

    // ---- 持久化 ----

    private void persist(CronJob job) {
        if (store == null) return;
        try {
            store.save(job);
        } catch (Exception e) {
            log.warn("Failed to persist cron job {}: {}", job.id(), e.getMessage());
        }
    }
}
