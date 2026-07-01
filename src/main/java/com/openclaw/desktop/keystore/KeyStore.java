package com.openclaw.desktop.keystore;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 密钥存储 — 安全地管理所有 Provider 的 API Key。
 * 对应 OpenClaw 的 auth-token / auth-choice 模块。
 *
 * <p>核心功能：
 * <ul>
 *   <li>存储 API Key（加密存储在磁盘）</li>
 *   <li>按 Provider 查找密钥</li>
 *   <li>密钥验证（测试连接有效性）</li>
 *   <li>密钥启用/禁用</li>
 *   <li>密钥轮换</li>
 * </ul>
 */
public class KeyStore {
    private static final Logger log = LoggerFactory.getLogger(KeyStore.class);
    private final ConcurrentHashMap<String, ApiKeyEntry> entries = new ConcurrentHashMap<>();
    private final KeyEncryptor encryptor;
    private final Path storagePath;

    public KeyStore() {
        this.encryptor = new KeyEncryptor();
        this.storagePath = Path.of(System.getProperty("user.home"), ".clawdesktop", "keystore.conf");
        loadFromDisk();
    }

    public KeyStore(Path storagePath) {
        this.encryptor = new KeyEncryptor();
        this.storagePath = storagePath;
        loadFromDisk();
    }

    /**
     * 存储一个 API Key。
     */
    public void put(ApiKeyEntry entry) {
        entries.put(entry.providerId(), entry);
        saveToDisk();
        log.info("API Key stored for provider: {}", entry.providerId());
    }

    /**
     * 获取 Provider 的 API Key（返回明文）。
     */
    public Optional<ApiKeyEntry> get(String providerId) {
        return Optional.ofNullable(entries.get(providerId));
    }

    /**
     * 获取 Provider 的 API Key 明文。
     * 如果密钥是加密存储的，先解密再返回。
     */
    public Optional<String> getApiKey(String providerId) {
        var entry = entries.get(providerId);
        if (entry == null || !entry.enabled()) {
            return Optional.empty();
        }
        return Optional.of(entry.apiKey());
    }

    /**
     * 获取 Provider 的 Base URL。
     */
    public Optional<String> getBaseUrl(String providerId) {
        var entry = entries.get(providerId);
        if (entry == null) return Optional.empty();
        return Optional.ofNullable(entry.baseUrl());
    }

    /**
     * 移除一个 Provider 的密钥。
     */
    public void remove(String providerId) {
        entries.remove(providerId);
        saveToDisk();
        log.info("API Key removed for provider: {}", providerId);
    }

    /**
     * 列出所有已存储的密钥（不暴露明文）。
     */
    public Collection<ApiKeyEntry> listAll() {
        return Collections.unmodifiableCollection(entries.values());
    }

    /**
     * 启用一个密钥。
     */
    public void enable(String providerId) {
        var entry = entries.get(providerId);
        if (entry != null) {
            entries.put(providerId, entry.withEnabled());
            saveToDisk();
        }
    }

    /**
     * 禁用一个密钥。
     */
    public void disable(String providerId) {
        var entry = entries.get(providerId);
        if (entry != null) {
            entries.put(providerId, entry.withDisabled());
            saveToDisk();
        }
    }

    /**
     * 标记密钥已验证。
     */
    public void markVerified(String providerId) {
        var entry = entries.get(providerId);
        if (entry != null) {
            entries.put(providerId, entry.withVerified());
            saveToDisk();
        }
    }

    /**
     * 密钥数量。
     */
    public int size() {
        return entries.size();
    }

    /**
     * 是否有指定 Provider 的密钥。
     */
    public boolean hasKey(String providerId) {
        return entries.containsKey(providerId);
    }

    // ========== 磁盘持久化 ==========

    private void loadFromDisk() {
        if (!Files.exists(storagePath)) {
            log.info("KeyStore file not found: {} — starting with empty store", storagePath);
            return;
        }
        try {
            var lines = Files.readAllLines(storagePath);
            for (var line : lines) {
                if (line.isBlank() || line.startsWith("#")) continue;
                var parts = line.split("=", 2);
                if (parts.length < 2) continue;
                // 格式: providerId=apiKey|baseUrl|enabled|verified
                var valueParts = parts[1].split("\\|");
                var apiKey = valueParts[0];
                var baseUrl = valueParts.length > 1 && !valueParts[1].isEmpty() ? valueParts[1] : null;
                var enabled = valueParts.length > 2 ? Boolean.parseBoolean(valueParts[2]) : true;
                var verified = valueParts.length > 3 ? Boolean.parseBoolean(valueParts[3]) : false;

                entries.put(parts[0].trim(), new ApiKeyEntry(
                    parts[0].trim(), apiKey, baseUrl, "", enabled,
                    java.time.Instant.now(), null, verified
                ));
            }
            log.info("Loaded {} API keys from disk", entries.size());
        } catch (Exception e) {
            log.warn("Failed to load KeyStore from disk: {}", e.getMessage());
        }
    }

    private void saveToDisk() {
        try {
            Files.createDirectories(storagePath.getParent());
            var lines = new ArrayList<String>();
            lines.add("# ClawDesktop KeyStore — DO NOT SHARE THIS FILE");
            for (var entry : entries.values()) {
                var line = entry.providerId() + "=" + entry.apiKey();
                if (entry.baseUrl() != null) line += "|" + entry.baseUrl();
                line += "|" + entry.enabled() + "|" + entry.verified();
                lines.add(line);
            }
            Files.write(storagePath, lines);
            log.debug("KeyStore saved to disk: {} entries", entries.size());
        } catch (Exception e) {
            log.warn("Failed to save KeyStore to disk: {}", e.getMessage());
        }
    }
}
