package com.openclaw.desktop.llm.provider;

import com.openclaw.desktop.llm.*;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.*;

/**
 * LLM Provider 扩展测试 — 验证所有 Provider 的构造和元数据。
 * 实际 API 调用需要真实密钥，不在此测试中。
 */
class LlmProviderExtendedTest {

    @Test
    void testGoogleProviderMetadata() {
        var provider = new GoogleProvider("test-key");
        assertEquals("google", provider.id());
        assertEquals("Google Gemini", provider.name());
        var models = provider.listModels().collectList().block();
        assertNotNull(models);
        assertTrue(models.size() >= 5);
        assertTrue(models.stream().anyMatch(m -> m.id().contains("gemini")));
    }

    @Test
    void testGoogleProviderWithBaseUrl() {
        var provider = new GoogleProvider("test-key", "https://custom.google.api.com/v1");
        assertNotNull(provider);
    }

    @Test
    void testGroqProviderMetadata() {
        var provider = new GroqProvider("test-key");
        assertEquals("groq", provider.id());
        assertEquals("Groq (LPU)", provider.name());
        var models = provider.listModels().collectList().block();
        assertNotNull(models);
        assertTrue(models.size() >= 5);
        assertTrue(models.stream().anyMatch(m -> m.id().contains("llama")));
    }

    @Test
    void testGroqProviderWithBaseUrl() {
        var provider = new GroqProvider("test-key", "https://custom.groq.api.com/v1");
        assertNotNull(provider);
    }

    @Test
    void testMistralProviderMetadata() {
        var provider = new MistralProvider("test-key");
        assertEquals("mistral", provider.id());
        assertEquals("Mistral AI", provider.name());
        var models = provider.listModels().collectList().block();
        assertNotNull(models);
        assertTrue(models.size() >= 5);
        assertTrue(models.stream().anyMatch(m -> m.id().contains("mistral")));
    }

    @Test
    void testMistralProviderWithBaseUrl() {
        var provider = new MistralProvider("test-key", "https://custom.mistral.api.com/v1");
        assertNotNull(provider);
    }

    @Test
    void testAzureOpenAiProviderMetadata() {
        var provider = new AzureOpenAiProvider("test-key", "https://my-resource.openai.azure.com", "gpt-4o-deployment");
        assertEquals("azure-openai", provider.id());
        assertEquals("Azure OpenAI", provider.name());
        var models = provider.listModels().collectList().block();
        assertNotNull(models);
        assertEquals(1, models.size());
        assertEquals("gpt-4o-deployment", models.get(0).id());
    }

    @Test
    void testAllExistingProviders() {
        // 验证所有已有 Provider 的元数据
        var providers = java.util.List.of(
            new OpenAiProvider("test-key"),
            new AnthropicProvider("test-key"),
            new DeepSeekProvider("test-key"),
            new QwenProvider("test-key"),
            new OllamaProvider(),
            new GoogleProvider("test-key"),
            new GroqProvider("test-key"),
            new MistralProvider("test-key"),
            new AzureOpenAiProvider("test-key", "https://test.openai.azure.com", "gpt-4")
        );

        assertEquals(9, providers.size());
        for (var p : providers) {
            assertNotNull(p.id());
            assertNotNull(p.name());
            assertFalse(p.id().isEmpty());
            assertFalse(p.name().isEmpty());
        }
    }

    @Test
    void testLlmErrorClassification() {
        var authError = LlmError.fromStatusCode("openai", 401, "Invalid API key");
        assertTrue(authError instanceof LlmError.AuthError);
        assertFalse(authError.isRetryable());

        var rateLimit = LlmError.fromStatusCode("openai", 429, "Rate limit exceeded");
        assertTrue(rateLimit instanceof LlmError.RateLimitError);
        assertTrue(rateLimit.isRetryable());
        assertTrue(rateLimit.suggestedRetryDelayMs() > 0);

        var serverError = LlmError.fromStatusCode("openai", 500, "Internal server error");
        assertTrue(serverError instanceof LlmError.ServerError);
        assertTrue(serverError.isRetryable());
        assertEquals(2000L, serverError.suggestedRetryDelayMs());

        var notFound = LlmError.fromStatusCode("openai", 404, "Model not found");
        assertTrue(notFound instanceof LlmError.ModelNotFoundError);
        assertFalse(notFound.isRetryable());

        var badRequest = LlmError.fromStatusCode("openai", 400, "Invalid request");
        assertTrue(badRequest instanceof LlmError.InvalidRequestError);
        assertFalse(badRequest.isRetryable());
    }

    @Test
    void testLlmErrorSealedTypes() {
        // 验证 sealed interface 的所有子类型
        var allTypes = java.util.List.of(
            new LlmError.AuthError("openai", "bad key"),
            new LlmError.RateLimitError("openai", 60, "slow down"),
            new LlmError.InvalidRequestError("openai", "bad request"),
            new LlmError.ModelNotFoundError("openai", "gpt-5", "not found"),
            new LlmError.ServerError("openai", 500, "oops"),
            new LlmError.ContextLengthError("openai", 100000, 8000, "too long"),
            new LlmError.ContentFilterError("openai", "safety", "filtered"),
            new LlmError.TimeoutError("openai", "timed out"),
            new LlmError.QuotaExceededError("openai", "no money"),
            new LlmError.ConnectionError("openai", "network down"),
            new LlmError.UnknownError("openai", "mystery", null)
        );
        assertEquals(11, allTypes.size());

        // 验证可重试分类
        var retryableCount = allTypes.stream().filter(LlmError::isRetryable).count();
        assertEquals(4, retryableCount); // RateLimit, Server, Timeout, Connection
    }

    @Test
    void testRetryStrategyDefaults() {
        var strategy = LlmRetryStrategy.defaults();
        assertNotNull(strategy);

        var noRetry = LlmRetryStrategy.noRetry();
        assertNotNull(noRetry);

        var aggressive = LlmRetryStrategy.aggressive();
        assertNotNull(aggressive);
    }

    @Test
    void testLlmRetryStrategyRetryableExceptionFilter() {
        var strategy = LlmRetryStrategy.defaults();
        // wrapWithRetry wraps a Mono/Flux with retry logic; verify it returns a non-null publisher
        assertNotNull(strategy.wrapWithRetry(Mono.error(new RuntimeException("429 Rate limit")), "test"));
        assertNotNull(strategy.wrapWithRetry(Mono.error(new RuntimeException("500 Server error")), "test"));
        assertNotNull(strategy.wrapWithRetry(Mono.error(new RuntimeException("timeout")), "test"));
        assertNotNull(strategy.wrapWithRetry(Mono.error(new RuntimeException("401 Unauthorized")), "test"));
    }

    @Test
    void testLlmProviderRegistryWithNewProviders() {
        var registry = new LlmProviderRegistry();
        registry.register(new OpenAiProvider("test-key"));
        registry.register(new AnthropicProvider("test-key"));
        registry.register(new GoogleProvider("test-key"));
        registry.register(new GroqProvider("test-key"));
        registry.register(new MistralProvider("test-key"));
        registry.register(new OllamaProvider());
        registry.register(new AzureOpenAiProvider("test-key", "https://test.openai.azure.com", "gpt-4"));
        registry.register(new DeepSeekProvider("test-key"));

        assertEquals(8, registry.all().stream().toList().size());
        assertTrue(registry.get("google").isPresent());
        assertTrue(registry.get("groq").isPresent());
        assertTrue(registry.get("mistral").isPresent());
        assertTrue(registry.get("azure-openai").isPresent());
        assertTrue(registry.get("deepseek").isPresent());
    }
}
