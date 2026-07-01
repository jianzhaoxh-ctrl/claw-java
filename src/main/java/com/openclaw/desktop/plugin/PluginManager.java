package com.openclaw.desktop.plugin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.*;

/**
 * 插件管理器 — 协调 {@link PluginLoader}、{@link EventBus} 与插件生命周期。
 *
 * <p>职责：
 * <ul>
 *   <li>启动时加载内置插件（classpath SPI）+ 外部插件（plugins/ 目录 JAR）</li>
 *   <li>调用 {@link ClawPlugin#init} 时注入针对该插件的 {@link PluginContext}</li>
 *   <li>发布插件生命周期事件到 {@link EventBus}</li>
 *   <li>支持运行时热加载/卸载单个插件</li>
 *   <li>关闭时逆序卸载所有插件</li>
 * </ul>
 */
public class PluginManager {

    private static final Logger log = LoggerFactory.getLogger(PluginManager.class);

    private final PluginContext baseContext;
    private final PluginLoader pluginLoader;
    private final EventBus eventBus;

    /** 已初始化的插件（按加载顺序，卸载时逆序）。 */
    private final List<ActivePlugin> activePlugins = new ArrayList<>();
    private final Map<String, ActivePlugin> byId = new HashMap<>();

    public PluginManager(PluginContext baseContext, Path pluginsDir) {
        this.baseContext = baseContext;
        this.eventBus = baseContext.eventBus();
        this.pluginLoader = new PluginLoader(pluginsDir);
    }

    /**
     * 启动：先加载内置插件（classpath SPI），再加载外部 JAR 插件。
     */
    public synchronized void loadAll() {
        log.info("Loading built-in plugins (classpath SPI)...");
        loadBuiltinPlugins();

        log.info("Loading external plugins from: {}", pluginLoader.pluginsDir());
        var external = pluginLoader.loadAll();
        for (var plugin : external) {
            initPlugin(plugin, "external");
        }
        log.info("PluginManager ready: {} plugin(s) active", activePlugins.size());
    }

    /**
     * 加载内置插件 — 通过应用 classpath 的 ServiceLoader 发现。
     */
    private void loadBuiltinPlugins() {
        var loader = java.util.ServiceLoader.load(ClawPlugin.class);
        for (var plugin : loader) {
            initPlugin(plugin, "builtin");
        }
    }

    /**
     * 初始化单个插件：注入 PluginContext，记录状态，发布事件。
     */
    private void initPlugin(ClawPlugin plugin, String source) {
        var pluginId = plugin.id();
        if (byId.containsKey(pluginId)) {
            log.warn("Plugin already active, skip: {} (source={})", pluginId, source);
            return;
        }
        try {
            var ctx = baseContext.forPlugin(pluginId);
            plugin.init(ctx);
            var active = new ActivePlugin(plugin, ctx, source);
            activePlugins.add(active);
            byId.put(pluginId, active);
            log.info("Plugin active: {} v{} [{}] ({})", plugin.name(), plugin.version(), source, pluginId);
            eventBus.publish(new PluginEvent.PluginLoaded(pluginId, plugin.name()));
        } catch (Exception e) {
            log.error("Failed to init plugin {} ({}): {}", plugin.name(), pluginId, e.getMessage(), e);
        }
    }

    /**
     * 运行时热加载指定路径的插件 JAR / 插件目录。
     * @return 加载的插件列表（可能为空）
     */
    public synchronized List<ClawPlugin> load(Path pluginPath) {
        List<ClawPlugin> plugins;
        try {
            if (pluginPath.toString().endsWith(".jar")) {
                plugins = pluginLoader.loadPluginJar(pluginPath);
            } else {
                plugins = pluginLoader.loadPluginDir(pluginPath);
            }
        } catch (Exception e) {
            log.error("Hot-load plugin failed: {} - {}", pluginPath, e.getMessage(), e);
            return List.of();
        }
        for (var plugin : plugins) {
            initPlugin(plugin, "hot");
        }
        return plugins;
    }

    /**
     * 卸载指定插件（逆序销毁）。
     */
    public synchronized boolean unload(String pluginId) {
        var active = byId.remove(pluginId);
        if (active == null) return false;
        try {
            active.plugin.destroy();
        } catch (Exception e) {
            log.error("Error destroying plugin {}: {}", pluginId, e.getMessage(), e);
        }
        activePlugins.remove(active);
        // 卸载外部插件的 ClassLoader
        if ("external".equals(active.source) || "hot".equals(active.source)) {
            pluginLoader.unload(pluginId);
        }
        eventBus.publish(new PluginEvent.PluginUnloaded(pluginId));
        log.info("Plugin unloaded: {}", pluginId);
        return true;
    }

    /**
     * 卸载所有插件（逆序）。
     */
    public synchronized void unloadAll() {
        for (var i = activePlugins.listIterator(activePlugins.size()); i.hasPrevious(); ) {
            var active = i.previous();
            try {
                active.plugin.destroy();
                eventBus.publish(new PluginEvent.PluginUnloaded(active.plugin.id()));
                log.info("Plugin destroyed: {}", active.plugin.id());
            } catch (Exception e) {
                log.error("Error destroying plugin {}: {}", active.plugin.id(), e.getMessage(), e);
            }
        }
        activePlugins.clear();
        byId.clear();
        pluginLoader.unloadAll();
    }

    /** 关闭管理器（卸载全部 + 关闭事件总线）。 */
    public void shutdown() {
        unloadAll();
        eventBus.shutdown();
    }

    public List<ClawPlugin> plugins() {
        return activePlugins.stream().map(a -> a.plugin).toList();
    }

    public Optional<ClawPlugin> get(String id) {
        var active = byId.get(id);
        return active != null ? Optional.of(active.plugin) : Optional.empty();
    }

    public EventBus eventBus() {
        return eventBus;
    }

    public int activeCount() {
        return activePlugins.size();
    }

    /** 活跃插件记录。 */
    private record ActivePlugin(ClawPlugin plugin, PluginContext context, String source) {}
}
