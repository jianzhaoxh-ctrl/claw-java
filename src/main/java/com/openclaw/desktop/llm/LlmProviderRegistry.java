package com.openclaw.desktop.llm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * LLM Provider 注册表 — 管理所有已注册的 LLM Provider。
 */
public class LlmProviderRegistry {

    private static final Logger log = LoggerFactory.getLogger(LlmProviderRegistry.class);

    private final Map<String, LlmProvider> providers = new ConcurrentHashMap<>();
    private String defaultProviderId;

    public void register(LlmProvider provider) {
        providers.put(provider.id(), provider);
        log.info("Registered LLM provider: {} ({})", provider.id(), provider.name());
    }

    public void unregister(String id) {
        providers.remove(id);
        log.info("Unregistered LLM provider: {}", id);
    }

    public Optional<LlmProvider> get(String id) {
        return Optional.ofNullable(providers.get(id));
    }

    public LlmProvider getDefault() {
        if (defaultProviderId != null) {
            var p = providers.get(defaultProviderId);
            if (p != null) return p;
        }
        // 返回第一个可用的
        return providers.values().stream().findFirst().orElse(null);
    }

    public void setDefault(String id) {
        this.defaultProviderId = id;
        log.info("Default LLM provider set to: {}", id);
    }

    public java.util.Collection<LlmProvider> all() {
        return providers.values();
    }

    public int size() { return providers.size(); }
}
