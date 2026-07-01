package com.openclaw.desktop.plugin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * 插件安装器 — 从远程仓库下载、安装、更新、卸载插件。
 * 对应 OpenClaw 的插件安装/管理系统。
 *
 * 插件目录结构：
 * ~/.clawdesktop/plugins/
 *   ├── my-plugin/
 *   │   ├── plugin.json        # 元数据
 *   │   ├── plugin.jar         # JAR 文件
 *   │   └── lib/               # 依赖 JAR
 *   └── another-plugin/
 */
public class PluginInstaller {

    private static final Logger log = LoggerFactory.getLogger(PluginInstaller.class);

    private final Path pluginsDir;
    private final HttpClient httpClient;
    private final Map<String, PluginMeta> installedPlugins = new ConcurrentHashMap<>();

    public PluginInstaller() {
        this(Path.of(System.getProperty("user.home"), ".clawdesktop", "plugins"));
    }

    public PluginInstaller(Path pluginsDir) {
        this.pluginsDir = pluginsDir;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
        try { Files.createDirectories(pluginsDir); } catch (Exception ignored) {}
        scanInstalled();
    }

    /**
     * 安装插件 — 从 URL 下载 JAR 并解压。
     */
    public PluginMeta install(String pluginId, String downloadUrl) {
        log.info("Installing plugin: {} from {}", pluginId, downloadUrl);
        try {
            var pluginDir = pluginsDir.resolve(pluginId);
            Files.createDirectories(pluginDir);

            // 下载
            var jarBytes = downloadFile(downloadUrl);
            var jarPath = pluginDir.resolve("plugin.jar");
            Files.write(jarPath, jarBytes);

            // 提取元数据（如果 JAR 中有 plugin.json）
            var meta = extractMeta(pluginId, jarPath);
            writeMeta(pluginDir, meta);

            installedPlugins.put(pluginId, meta);
            log.info("Plugin installed: {} v{}", pluginId, meta.version());
            return meta;
        } catch (Exception e) {
            log.error("Failed to install plugin {}: {}", pluginId, e.getMessage());
            throw new RuntimeException(e);
        }
    }

    /**
     * 从本地 JAR 安装。
     */
    public PluginMeta installLocal(String pluginId, Path jarFile) {
        try {
            var pluginDir = pluginsDir.resolve(pluginId);
            Files.createDirectories(pluginDir);
            Files.copy(jarFile, pluginDir.resolve("plugin.jar"), StandardCopyOption.REPLACE_EXISTING);
            var meta = extractMeta(pluginId, jarFile);
            writeMeta(pluginDir, meta);
            installedPlugins.put(pluginId, meta);
            log.info("Local plugin installed: {} v{}", pluginId, meta.version());
            return meta;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 卸载插件。
     */
    public boolean uninstall(String pluginId) {
        var pluginDir = pluginsDir.resolve(pluginId);
        try {
            if (Files.exists(pluginDir)) {
                deleteDirectory(pluginDir);
                installedPlugins.remove(pluginId);
                log.info("Plugin uninstalled: {}", pluginId);
                return true;
            }
            return false;
        } catch (Exception e) {
            log.error("Failed to uninstall {}: {}", pluginId, e.getMessage());
            return false;
        }
    }

    /**
     * 检查更新。
     */
    public List<PluginUpdateInfo> checkUpdates() {
        var updates = new ArrayList<PluginUpdateInfo>();
        for (var meta : installedPlugins.values()) {
            if (meta.sourceUrl() != null) {
                try {
                    var latest = fetchLatestVersion(meta.sourceUrl());
                    if (!meta.version().equals(latest)) {
                        updates.add(new PluginUpdateInfo(meta.id(), meta.version(), latest, meta.sourceUrl()));
                    }
                } catch (Exception ignored) {}
            }
        }
        return updates;
    }

    /**
     * 更新插件。
     */
    public PluginMeta update(String pluginId) {
        var meta = installedPlugins.get(pluginId);
        if (meta == null || meta.sourceUrl() == null) return null;
        uninstall(pluginId);
        return install(pluginId, meta.sourceUrl());
    }

    /**
     * 列出已安装插件。
     */
    public Collection<PluginMeta> listInstalled() {
        return installedPlugins.values();
    }

    /**
     * 扫描已安装插件（启动时调用）。
     */
    public void scanInstalled() {
        installedPlugins.clear();
        try (var dirs = Files.list(pluginsDir)) {
            dirs.filter(Files::isDirectory).forEach(pluginDir -> {
                var metaFile = pluginDir.resolve("plugin.json");
                if (Files.exists(metaFile)) {
                    try {
                        var json = Files.readString(metaFile);
                        var meta = parseMeta(json);
                        installedPlugins.put(meta.id(), meta);
                    } catch (Exception ignored) {}
                }
            });
        } catch (Exception ignored) {}
        log.info("Scanned {} installed plugins", installedPlugins.size());
    }

    // ---- internal ----

    private byte[] downloadFile(String url) throws Exception {
        var request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofSeconds(120))
            .GET()
            .build();
        var response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() != 200) {
            throw new RuntimeException("Download failed: HTTP " + response.statusCode());
        }
        return response.body();
    }

    private PluginMeta extractMeta(String pluginId, Path jarPath) {
        // 简化：返回默认元数据
        return new PluginMeta(pluginId, "1.0.0", "", "", "", "", true);
    }

    private void writeMeta(Path pluginDir, PluginMeta meta) throws Exception {
        var json = String.format(
            "{\"id\":\"%s\",\"version\":\"%s\",\"description\":\"%s\",\"author\":\"%s\",\"sourceUrl\":\"%s\",\"homepage\":\"%s\",\"enabled\":%s}",
            meta.id(), meta.version(), meta.description(), meta.author(),
            meta.sourceUrl(), meta.homepage(), meta.enabled()
        );
        Files.writeString(pluginDir.resolve("plugin.json"), json);
    }

    private PluginMeta parseMeta(String json) throws Exception {
        var root = new com.fasterxml.jackson.databind.ObjectMapper().readTree(json);
        return new PluginMeta(
            root.path("id").asText(""),
            root.path("version").asText("0.0.0"),
            root.path("description").asText(""),
            root.path("author").asText(""),
            root.path("sourceUrl").asText(""),
            root.path("homepage").asText(""),
            root.path("enabled").asBoolean(true)
        );
    }

    private String fetchLatestVersion(String sourceUrl) throws Exception {
        // 简化：返回当前版本（实际应查询远程仓库）
        var meta = installedPlugins.values().stream()
            .filter(m -> sourceUrl.equals(m.sourceUrl()))
            .findFirst();
        return meta.map(PluginMeta::version).orElse("0.0.0");
    }

    private void deleteDirectory(Path dir) throws Exception {
        try (var stream = Files.walk(dir)) {
            stream.sorted(java.util.Comparator.reverseOrder())
                .forEach(p -> {
                    try { Files.delete(p); } catch (Exception ignored) {}
                });
        }
    }

    // ---- records ----

    public record PluginMeta(
        String id, String version, String description,
        String author, String sourceUrl, String homepage, boolean enabled
    ) {}

    public record PluginUpdateInfo(String pluginId, String currentVersion, String latestVersion, String downloadUrl) {}
}
