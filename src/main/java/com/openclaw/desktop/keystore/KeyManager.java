package com.openclaw.desktop.keystore;

import com.openclaw.desktop.llm.LlmProvider;
import com.openclaw.desktop.llm.LlmProviderRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * 密钥管理器 — 管理密钥的生命周期（注册/验证/轮换/清理）。
 * 对应 OpenClaw 的 auth-choice / onboard-auth 模块。
 */
public class KeyManager {
    private static final Logger log = LoggerFactory.getLogger(KeyManager.class);
    private final KeyStore keyStore;
    private final HttpClient httpClient;

    public KeyManager() {
        this.keyStore = new KeyStore();
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    }

    public KeyManager(KeyStore keyStore) {
        this.keyStore = keyStore;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    }

    /**
     * 注册一个新的 API Key。
     */
    public Mono<ApiKeyEntry> registerKey(String providerId, String apiKey, String baseUrl) {
        return Mono.fromCallable(() -> {
            var entry = ApiKeyEntry.of(providerId, apiKey, baseUrl);
            keyStore.put(entry);
            log.info("Registered API key for provider: {}", providerId);
            return entry;
        });
    }

    /**
     * 验证密钥是否有效（通过 HTTP 请求测试）。
     */
    public Mono<Boolean> verifyKey(String providerId) {
        var entry = keyStore.get(providerId);
        if (entry.isEmpty()) {
            return Mono.just(false);
        }
        var apiKey = entry.get().apiKey();
        var baseUrl = entry.get().baseUrl();

        return Mono.fromCallable(() -> {
            var testUrl = switch (providerId) {
                case "openai"        -> (baseUrl != null ? baseUrl : "https://api.openai.com/v1") + "/models";
                case "anthropic"     -> "https://api.anthropic.com/v1/messages";
                case "deepseek"      -> (baseUrl != null ? baseUrl : "https://api.deepseek.com/v1") + "/models";
                case "qwen"          -> "https://dashscope.aliyuncs.com/compatible-mode/v1/models";
                case "ollama"        -> "http://localhost:11434/api/tags";
                case "google"        -> (baseUrl != null ? baseUrl : "https://generativelanguage.googleapis.com/v1beta") + "/models";
                case "groq"          -> (baseUrl != null ? baseUrl : "https://api.groq.com/openai/v1") + "/models";
                case "mistral"       -> (baseUrl != null ? baseUrl : "https://api.mistral.ai/v1") + "/models";
                case "azure-openai"  -> baseUrl != null ? baseUrl : "";
                default              -> baseUrl != null ? baseUrl : "";
            };

            if (testUrl.isEmpty()) return false;

            var requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(testUrl))
                .timeout(Duration.ofSeconds(10))
                .GET();

            if (!providerId.equals("ollama") && apiKey != null && !apiKey.isBlank()) {
                requestBuilder.header("Authorization", "Bearer " + apiKey);
            }
            if (providerId.equals("anthropic")) {
                requestBuilder.header("x-api-key", apiKey);
                requestBuilder.header("anthropic-version", "2023-06-01");
            }

            var response = httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
            var ok = response.statusCode() < 500;
            if (ok) {
                keyStore.markVerified(providerId);
                log.info("API key verified for provider: {}", providerId);
            } else {
                log.warn("API key verification failed for provider: {} — status {}", providerId, response.statusCode());
            }
            return ok;
        });
    }

    /**
     * 轮换密钥 — 替换旧的 API Key 为新的。
     */
    public Mono<ApiKeyEntry> rotateKey(String providerId, String newApiKey, String newBaseUrl) {
        return Mono.fromCallable(() -> {
            keyStore.remove(providerId);
            var entry = ApiKeyEntry.of(providerId, newApiKey, newBaseUrl);
            keyStore.put(entry);
            log.info("Rotated API key for provider: {}", providerId);
            return entry;
        });
    }

    /**
     * 移除密钥。
     */
    public Mono<Void> removeKey(String providerId) {
        return Mono.fromRunnable(() -> {
            keyStore.remove(providerId);
            log.info("Removed API key for provider: {}", providerId);
        });
    }

    /**
     * 将 KeyStore 中的密钥注入到 LlmProviderRegistry。
     * 即：根据存储的密钥创建 Provider 并注册。
     */
    public Mono<Void> injectProviders(LlmProviderRegistry providerRegistry) {
        return Flux.fromIterable(keyStore.listAll())
            .filter(entry -> entry.enabled())
            .flatMap(entry -> Mono.fromCallable(() -> {
                var provider = createProvider(entry);
                if (provider != null) {
                    providerRegistry.register(provider);
                    log.info("Injected provider from KeyStore: {}", entry.providerId());
                }
                return provider;
            }))
            .then();
    }

    private LlmProvider createProvider(ApiKeyEntry entry) {
        var baseUrl = entry.baseUrl();
        return switch (entry.providerId()) {
            case "openai"        -> new com.openclaw.desktop.llm.provider.OpenAiProvider(entry.apiKey(), baseUrl);
            case "anthropic"     -> new com.openclaw.desktop.llm.provider.AnthropicProvider(entry.apiKey(), baseUrl);
            case "deepseek"      -> new com.openclaw.desktop.llm.provider.DeepSeekProvider(entry.apiKey(), baseUrl != null ? baseUrl : "https://api.deepseek.com/v1");
            case "qwen"          -> new com.openclaw.desktop.llm.provider.QwenProvider(entry.apiKey(), baseUrl != null ? baseUrl : "https://dashscope.aliyuncs.com/compatible-mode/v1");
            case "ollama"        -> new com.openclaw.desktop.llm.provider.OllamaProvider(baseUrl != null ? baseUrl : "http://localhost:11434");
            case "google"        -> new com.openclaw.desktop.llm.provider.GoogleProvider(entry.apiKey(), baseUrl);
            case "groq"          -> new com.openclaw.desktop.llm.provider.GroqProvider(entry.apiKey(), baseUrl);
            case "mistral"       -> new com.openclaw.desktop.llm.provider.MistralProvider(entry.apiKey(), baseUrl);
            case "azure-openai"  -> new com.openclaw.desktop.llm.provider.AzureOpenAiProvider(entry.apiKey(), baseUrl != null ? baseUrl : "https://YOUR_RESOURCE.openai.azure.com", entry.apiKey());
            default              -> null;
        };
    }

    /**
     * 获取 KeyStore。
     */
    public KeyStore getKeyStore() {
        return keyStore;
    }
}
