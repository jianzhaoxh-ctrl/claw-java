package com.openclaw.desktop.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.*;
import java.util.function.Consumer;

/**
 * 配置文件监视器 — 监听配置文件变化实现热重载。
 */
public final class ConfigWatcher {

    private static final Logger log = LoggerFactory.getLogger(ConfigWatcher.class);

    private final Path configPath;
    private final Consumer<ClawConfig> onReload;
    private WatchService watchService;
    private Thread watchThread;
    private volatile boolean running;

    public ConfigWatcher(Path configPath, Consumer<ClawConfig> onReload) {
        this.configPath = configPath;
        this.onReload = onReload;
    }

    public void start() {
        if (running) return;
        running = true;
        try {
            watchService = FileSystems.getDefault().newWatchService();
            var parent = configPath.getParent();
            if (parent != null) {
                parent.register(watchService, StandardWatchEventKinds.ENTRY_MODIFY);
            }
        } catch (Exception e) {
            log.error("Failed to start config watcher", e);
            return;
        }

        watchThread = Thread.ofVirtual().name("config-watcher").start(() -> {
            log.info("Config watcher started for: {}", configPath);
            while (running) {
                try {
                    var key = watchService.take();
                    for (var event : key.pollEvents()) {
                        var changedFile = (Path) event.context();
                        if (configPath.getFileName().equals(changedFile)) {
                            log.info("Config file changed, reloading...");
                            Thread.sleep(200); // debounce
                            var newConfig = ConfigLoader.load(configPath);
                            onReload.accept(newConfig);
                            log.info("Config reloaded");
                        }
                    }
                    key.reset();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    log.error("Config watcher error", e);
                }
            }
        });
    }

    public void stop() {
        running = false;
        if (watchThread != null) watchThread.interrupt();
        try {
            if (watchService != null) watchService.close();
        } catch (Exception ignored) {}
        log.info("Config watcher stopped");
    }
}
