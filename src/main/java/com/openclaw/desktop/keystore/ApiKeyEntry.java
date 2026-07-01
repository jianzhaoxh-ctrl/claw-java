package com.openclaw.desktop.keystore;

import java.time.Instant;

/**
 * API 密钥记录 — 存储一个 Provider 的 API Key 及元数据。
 */
public record ApiKeyEntry(
    String providerId,
    String apiKey,
    String baseUrl,
    String description,
    boolean enabled,
    Instant createdAt,
    Instant lastVerifiedAt,
    boolean verified
) {
    public static ApiKeyEntry of(String providerId, String apiKey) {
        return new ApiKeyEntry(providerId, apiKey, null, "", true, Instant.now(), null, false);
    }

    public static ApiKeyEntry of(String providerId, String apiKey, String baseUrl) {
        return new ApiKeyEntry(providerId, apiKey, baseUrl, "", true, Instant.now(), null, false);
    }

    /**
     * 标记已验证。
     */
    public ApiKeyEntry withVerified() {
        return new ApiKeyEntry(providerId, apiKey, baseUrl, description, enabled, createdAt, Instant.now(), true);
    }

    /**
     * 禁用。
     */
    public ApiKeyEntry withDisabled() {
        return new ApiKeyEntry(providerId, apiKey, baseUrl, description, false, createdAt, lastVerifiedAt, verified);
    }

    /**
     * 启用。
     */
    public ApiKeyEntry withEnabled() {
        return new ApiKeyEntry(providerId, apiKey, baseUrl, description, true, createdAt, lastVerifiedAt, verified);
    }
}
