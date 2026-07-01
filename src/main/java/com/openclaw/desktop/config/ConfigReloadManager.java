package com.openclaw.desktop.config;

import com.openclaw.desktop.plugin.EventBus;
import com.openclaw.desktop.plugin.PluginEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * 配置热重载管理器 — 封装 {@link ConfigWatcher}，支持多订阅者与事件总线广播。
 *
 * <p>当配置文件变化时：
 * <ol>
 *   <li>重新加载配置（debounce 避免频繁触发）</li>
 *   <li>通知所有注册的 {@link Consumer} 订阅者（子系统可按需更新）</li>
 *   <li>通过 {@link EventBus} 广播 {@link PluginEvent.CustomEvent}（type=config.reloaded）</li>
 * </ol>
 *
 * <p>典型用法：
 * <pre>
 * var manager = new ConfigReloadManager(eventBus);
 * manager.subscribe(newConfig -> llmRegistry.updateProviders(newConfig));
 * manager.subscribe(newConfig -> agentConfig.update(newConfig));
 * manager.start();
 * </pre>
 */
public class ConfigReloadManager {

    private static final Logger log = LoggerFactory.getLogger(ConfigReloadManager.class);
    private static final long DEBOUNCE_MS = 500;

    private final EventBus eventBus;
    private final List<Consumer<ClawConfig>> subscribers = new CopyOnWriteArrayList<>();
    private ConfigWatcher watcher;
    private ClawConfig currentConfig;
    private Thread debounceThread;
    private volatile long lastChangeTime;
    private final Path configPath;

    public ConfigReloadManager(EventBus eventBus) {
        this(eventBus, Paths.get("application.conf"));
    }

    public ConfigReloadManager(EventBus eventBus, Path configPath) {
        this.eventBus = eventBus;
        this.configPath = configPath;
    }

    /**
     * 启动配置监视。首次加载配置并通知订阅者。
     */
    public void start() {
        try {
            currentConfig = ConfigLoader.load(configPath);
            notifySubscribers(currentConfig);
        } catch (Exception e) {
            log.warn("Initial config load failed: {}", e.getMessage());
            currentConfig = ClawConfig.defaults();
        }

        watcher = new ConfigWatcher(configPath, newConfig -> {
            lastChangeTime = System.currentTimeMillis();
            // debounce：合并短时间内多次文件变化事件
            if (debounceThread == null || !debounceThread.isAlive()) {
                debounceThread = Thread.ofVirtual().name("config-debounce").start(() -> {
                    try {
                        Thread.sleep(DEBOUNCE_MS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                    reload();
                });
            }
        });
        watcher.start();
        log.info("ConfigReloadManager started, watching: {}", configPath);
    }

    /**
     * 停止配置监视。
     */
    public void stop() {
        if (watcher != null) {
            watcher.stop();
        }
        if (debounceThread != null) {
            debounceThread.interrupt();
        }
        log.info("ConfigReloadManager stopped");
    }

    /**
     * 强制重新加载配置。
     */
    public synchronized void reload() {
        try {
            var newConfig = ConfigLoader.load(configPath);
            var oldConfig = currentConfig;
            currentConfig = newConfig;
            log.info("Config reloaded successfully");
            notifySubscribers(newConfig);
            // 通过事件总线广播
            if (eventBus != null) {
                eventBus.publish(new PluginEvent.CustomEvent(
                    "config.reloaded",
                    java.util.Map.of("old", oldConfig != null ? oldConfig.toString() : "", "new", newConfig.toString()),
                    "config"
                ));
            }
        } catch (Exception e) {
            log.error("Config reload failed, keeping old config: {}", e.getMessage(), e);
            if (eventBus != null) {
                eventBus.publish(new PluginEvent.CustomEvent(
                    "config.reload.failed", e.getMessage(), "config"
                ));
            }
        }
    }

    /**
     * 订阅配置变化。
     * @return 取消订阅的 Runnable
     */
    public Runnable subscribe(Consumer<ClawConfig> subscriber) {
        subscribers.add(subscriber);
        // 立即用当前配置通知一次
        if (currentConfig != null) {
            try { subscriber.accept(currentConfig); } catch (Exception e) {
                log.warn("Initial config notify failed: {}", e.getMessage());
            }
        }
        return () -> subscribers.remove(subscriber);
    }

    /**
     * 取消订阅。
     */
    public void unsubscribe(Consumer<ClawConfig> subscriber) {
        subscribers.remove(subscriber);
    }

    /**
     * 当前配置快照。
     */
    public ClawConfig current() {
        return currentConfig;
    }

    /**
     * 订阅者数量。
     */
    public int subscriberCount() {
        return subscribers.size();
    }

    private void notifySubscribers(ClawConfig config) {
        for (var sub : subscribers) {
            try {
                sub.accept(config);
            } catch (Exception e) {
                log.error("Config subscriber threw: {}", e.getMessage(), e);
            }
        }
    }
}
