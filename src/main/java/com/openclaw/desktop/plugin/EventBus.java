package com.openclaw.desktop.plugin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * 事件总线 — 插件间解耦通信的核心。
 *
 * <p>特性：
 * <ul>
 *   <li>基于事件类型的订阅（按 {@code Class<? extends PluginEvent>} 分发）</li>
 *   <li>支持同步发布（{@link #publish}）和异步发布（{@link #publishAsync}，虚拟线程）</li>
 *   <li>订阅者异常隔离：单个订阅者抛异常不影响其他订阅者</li>
 *   <li>线程安全</li>
 * </ul>
 *
 * <p>订阅者按事件的具体类型匹配，订阅父类型不会收到子类型事件（精确匹配）。
 * 如需接收所有事件，可订阅 {@link PluginEvent} 本身。
 */
public class EventBus {

    private static final Logger log = LoggerFactory.getLogger(EventBus.class);

    private final Map<Class<? extends PluginEvent>, List<Subscription>> subscribers = new ConcurrentHashMap<>();
    private final ExecutorService asyncExecutor;
    private final AtomicLong publishedCount = new AtomicLong(0);
    private final AtomicLong deliveredCount = new AtomicLong(0);

    public EventBus() {
        // 虚拟线程执行器，适合大量 I/O 阻塞型订阅者
        this.asyncExecutor = Executors.newThreadPerTaskExecutor(
            Thread.ofVirtual().name("eventbus-async-", 0).factory()
        );
    }

    /**
     * 订阅指定类型的事件。
     *
     * @param eventType 事件类型（class）
     * @param consumer  事件处理器
     * @param <T>       事件类型
     * @return 订阅句柄，调用 {@link Subscription#cancel()} 取消订阅
     */
    public <T extends PluginEvent> Subscription subscribe(Class<T> eventType, Consumer<T> consumer) {
        var sub = new Subscription(eventType, consumer);
        subscribers.computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>()).add(sub);
        log.debug("Subscribed to {}: {}", eventType.getSimpleName(), sub.id);
        return sub;
    }

    /**
     * 订阅所有事件（通配订阅）。
     */
    public Subscription subscribeAll(Consumer<PluginEvent> consumer) {
        return subscribe(PluginEvent.class, consumer);
    }

    /**
     * 同步发布事件 — 在调用线程依次执行所有匹配的订阅者。
     * 单个订阅者抛异常被捕获并记录，不影响后续订阅者。
     */
    public void publish(PluginEvent event) {
        publishedCount.incrementAndGet();
        for (var sub : collectTargets(event)) {
            deliver(sub, event);
        }
    }

    /**
     * 异步发布事件 — 每个订阅者在独立虚拟线程中执行，互不阻塞。
     */
    public void publishAsync(PluginEvent event) {
        publishedCount.incrementAndGet();
        for (var sub : collectTargets(event)) {
            asyncExecutor.submit(() -> deliver(sub, event));
        }
    }

    /**
     * 收集应收到该事件的所有订阅者（精确类型 + 通配），去重避免重复投递。
     */
    private java.util.Set<Subscription> collectTargets(PluginEvent event) {
        var targets = new java.util.LinkedHashSet<Subscription>();
        var exact = subscribers.get(event.getClass());
        if (exact != null) targets.addAll(exact);
        if (event.getClass() != PluginEvent.class) {
            var wildcard = subscribers.get(PluginEvent.class);
            if (wildcard != null) targets.addAll(wildcard);
        }
        return targets;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void deliver(Subscription sub, PluginEvent event) {
        try {
            ((Consumer<PluginEvent>) sub.consumer).accept(event);
            deliveredCount.incrementAndGet();
        } catch (Exception e) {
            log.error("Event subscriber {} threw on {}: {}", sub.id, event.getClass().getSimpleName(), e.getMessage(), e);
        }
    }

    /** 取消订阅。 */
    public void unsubscribe(Subscription subscription) {
        var subs = subscribers.get(subscription.eventType);
        if (subs != null) {
            subs.remove(subscription);
            log.debug("Unsubscribed: {}", subscription.id);
        }
    }

    /** 清除所有订阅者并关闭异步执行器。 */
    public void shutdown() {
        subscribers.clear();
        asyncExecutor.shutdown();
        log.info("EventBus shutdown: published={}, delivered={}", publishedCount.get(), deliveredCount.get());
    }

    /** 统计：已发布事件数。 */
    public long publishedCount() { return publishedCount.get(); }

    /** 统计：已投递事件数（含重复投递）。 */
    public long deliveredCount() { return deliveredCount.get(); }

    /** 订阅句柄。 */
    public final class Subscription {
        private final Class<? extends PluginEvent> eventType;
        private final Consumer<? extends PluginEvent> consumer;
        private final long id;

        @SuppressWarnings("rawtypes")
        Subscription(Class<? extends PluginEvent> eventType, Consumer consumer) {
            this.eventType = eventType;
            this.consumer = consumer;
            this.id = SUBSCRIPTION_ID.incrementAndGet();
        }

        public void cancel() {
            unsubscribe(this);
        }
    }

    private static final AtomicLong SUBSCRIPTION_ID = new AtomicLong(0);
}
