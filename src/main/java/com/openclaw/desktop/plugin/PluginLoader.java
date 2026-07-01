package com.openclaw.desktop.plugin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 插件加载器 — 从 plugins/ 目录动态加载插件 JAR 并通过 SPI 发现 {@link ClawPlugin} 实现。
 *
 * <p>每个插件 JAR（或一组 JAR）使用独立的 {@link URLClassLoader} 隔离，
 * 实现热加载与热卸载。卸载时关闭 ClassLoader，释放资源。
 *
 * <p>目录结构约定：
 * <pre>
 * plugins/
 * ├── my-plugin/
 * │   ├── my-plugin.jar          # 主 JAR
 * │   └── lib/*.jar              # 依赖 JAR
 * └── standalone.jar             # 单文件插件
 * </pre>
 *
 * <p>插件通过 SPI 声明：在 JAR 内 {@code META-INF/services/com.openclaw.desktop.plugin.ClawPlugin}
 * 文件中写入实现类的全限定名。
 */
public class PluginLoader {

    private static final Logger log = LoggerFactory.getLogger(PluginLoader.class);
    private static final String SPI_FILE = "META-INF/services/" + ClawPlugin.class.getName();

    private final Path pluginsDir;
    private final ClassLoader parentClassLoader;
    private final Map<String, LoadedPlugin> loaded = new ConcurrentHashMap<>();

    /**
     * @param pluginsDir 插件目录
     * @param parentClassLoader 父 ClassLoader（通常为应用 ClassLoader）
     */
    public PluginLoader(Path pluginsDir, ClassLoader parentClassLoader) {
        this.pluginsDir = pluginsDir;
        this.parentClassLoader = parentClassLoader;
    }

    public PluginLoader(Path pluginsDir) {
        this(pluginsDir, PluginLoader.class.getClassLoader());
    }

    /** 插件目录。 */
    public Path pluginsDir() {
        return pluginsDir;
    }

    /**
     * 扫描插件目录，加载所有可发现的插件。
     * @return 本次新加载的插件列表
     */
    public List<ClawPlugin> loadAll() {
        var result = new ArrayList<ClawPlugin>();
        if (!Files.isDirectory(pluginsDir)) {
            log.info("Plugins directory not found, skipping: {}", pluginsDir);
            return result;
        }

        try (var stream = Files.list(pluginsDir)) {
            stream.sorted().forEach(path -> {
                try {
                    if (Files.isDirectory(path)) {
                        result.addAll(loadPluginDir(path));
                    } else if (path.toString().endsWith(".jar")) {
                        result.addAll(loadPluginJar(path));
                    }
                } catch (Exception e) {
                    log.error("Failed to load plugin from {}: {}", path, e.getMessage(), e);
                }
            });
        } catch (IOException e) {
            log.error("Failed to scan plugins directory {}: {}", pluginsDir, e.getMessage(), e);
        }
        return result;
    }

    /**
     * 从插件子目录加载（目录内所有 JAR 共用一个 ClassLoader）。
     */
    public List<ClawPlugin> loadPluginDir(Path dir) throws IOException {
        var pluginId = dir.getFileName().toString();
        if (loaded.containsKey(pluginId)) {
            log.debug("Plugin already loaded, skip: {}", pluginId);
            return List.of();
        }
        var jars = collectJars(dir);
        if (jars.isEmpty()) {
            log.debug("No JARs found in plugin dir: {}", dir);
            return List.of();
        }
        return doLoad(pluginId, jars, dir);
    }

    /**
     * 从单个 JAR 文件加载。
     */
    public List<ClawPlugin> loadPluginJar(Path jar) throws IOException {
        var pluginId = jar.getFileName().toString().replace(".jar", "");
        if (loaded.containsKey(pluginId)) {
            log.debug("Plugin already loaded, skip: {}", pluginId);
            return List.of();
        }
        return doLoad(pluginId, List.of(jar), jar);
    }

    private List<ClawPlugin> doLoad(String pluginId, List<Path> jars, Path source) throws IOException {
        var urls = jars.stream().map(this::toUrl).toArray(URL[]::new);
        var classLoader = new PluginClassLoader(pluginId, urls, parentClassLoader);
        var plugins = new ArrayList<ClawPlugin>();

        try {
            var serviceLoader = ServiceLoader.load(ClawPlugin.class, classLoader);
            for (var plugin : serviceLoader) {
                plugins.add(plugin);
                log.info("Discovered plugin via SPI: {} v{} ({})", plugin.name(), plugin.version(), plugin.id());
            }
        } catch (ServiceConfigurationError e) {
            log.error("SPI configuration error in plugin {}: {}", pluginId, e.getMessage());
            closeQuietly(classLoader);
            throw new IOException("Failed to load plugin " + pluginId, e);
        }

        if (plugins.isEmpty()) {
            log.warn("No ClawPlugin implementation found in {} (missing {})", source, SPI_FILE);
            closeQuietly(classLoader);
            return List.of();
        }

        loaded.put(pluginId, new LoadedPlugin(pluginId, plugins, classLoader, source));
        log.info("Loaded plugin '{}' ({} impl) from {}", pluginId, plugins.size(), source);
        return Collections.unmodifiableList(plugins);
    }

    /**
     * 卸载指定插件 — 关闭其 ClassLoader 并移除注册。
     */
    public boolean unload(String pluginId) {
        var lp = loaded.remove(pluginId);
        if (lp == null) return false;
        closeQuietly(lp.classLoader);
        log.info("Unloaded plugin: {}", pluginId);
        return true;
    }

    /** 卸载所有插件。 */
    public void unloadAll() {
        for (var id : new ArrayList<>(loaded.keySet())) {
            unload(id);
        }
    }

    /** 所有已加载的插件实现。 */
    public List<ClawPlugin> allPlugins() {
        return loaded.values().stream().flatMap(lp -> lp.plugins.stream()).toList();
    }

    public boolean isLoaded(String pluginId) {
        return loaded.containsKey(pluginId);
    }

    public int loadedCount() {
        return loaded.size();
    }

    // ---- helpers ----

    private List<Path> collectJars(Path dir) throws IOException {
        var jars = new ArrayList<Path>();
        try (var stream = Files.list(dir)) {
            stream.filter(p -> p.toString().endsWith(".jar")).forEach(jars::add);
        }
        // lib 子目录
        var libDir = dir.resolve("lib");
        if (Files.isDirectory(libDir)) {
            try (var stream = Files.list(libDir)) {
                stream.filter(p -> p.toString().endsWith(".jar")).forEach(jars::add);
            }
        }
        return jars;
    }

    private URL toUrl(Path p) {
        try {
            return p.toUri().toURL();
        } catch (Exception e) {
            throw new RuntimeException("Invalid path: " + p, e);
        }
    }

    private void closeQuietly(Closeable c) {
        try {
            c.close();
        } catch (IOException e) {
            log.debug("Error closing resource: {}", e.getMessage());
        }
    }

    /** 已加载的插件包（含 ClassLoader，用于卸载）。 */
    private record LoadedPlugin(
        String id,
        List<ClawPlugin> plugins,
        PluginClassLoader classLoader,
        Path source
    ) {}

    /**
     * 插件专用 ClassLoader — 继承 URLClassLoader，记录插件 ID 便于隔离与卸载。
     * 采用「子优先」策略：插件类优先从自身加载，避免与主应用类冲突。
     */
    public static class PluginClassLoader extends URLClassLoader {
        private final String pluginId;

        public PluginClassLoader(String pluginId, URL[] urls, ClassLoader parent) {
            super(urls, parent);
            this.pluginId = pluginId;
        }

        public String pluginId() {
            return pluginId;
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            // 子优先：先尝试自己加载（插件类 + lib），找不到再委托父加载器
            synchronized (getClassLoadingLock(name)) {
                var c = findLoadedClass(name);
                if (c == null) {
                    // 黑名单：JDK 与 ClawDesktop 核心 SPI 必须由父加载器加载，保证类型一致
                    if (shouldDelegateToParent(name)) {
                        c = super.loadClass(name, resolve);
                    } else {
                        try {
                            c = findClass(name);
                        } catch (ClassNotFoundException e) {
                            c = super.loadClass(name, resolve);
                        }
                    }
                }
                if (resolve) resolveClass(c);
                return c;
            }
        }

        /**
         * 判断该类是否必须由父加载器加载。
         * JDK 类、ClawDesktop 核心接口/record 必须共享，否则 instanceof / record 约定会失败。
         */
        private boolean shouldDelegateToParent(String name) {
            if (name.startsWith("java.") || name.startsWith("javax.")
                || name.startsWith("jdk.") || name.startsWith("sun.")
                || name.startsWith("org.w3c.") || name.startsWith("org.xml.")
                || name.startsWith("org.slf4j") || name.startsWith("org.w3c.dom")) {
                return true;
            }
            // ClawDesktop 核心 API（插件通过这些接口与主程序交互）
            return name.startsWith("com.openclaw.desktop.plugin.")
                || name.startsWith("com.openclaw.desktop.tool.")
                || name.startsWith("com.openclaw.desktop.llm.")
                || name.startsWith("com.openclaw.desktop.config.")
                || name.startsWith("com.openclaw.desktop.session.")
                || name.startsWith("com.openclaw.desktop.cron.")
                || name.startsWith("com.openclaw.desktop.channel.")
                || name.startsWith("com.openclaw.desktop.types.")
                || name.startsWith("reactor.core.");
        }
    }
}
