package com.openclaw.desktop.llm.provider;

import com.openclaw.desktop.llm.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.net.http.HttpClient;
import java.time.Duration;

/**
 * LiteLLM Provider — 统一代理 100+ LLM API。
 * 对应 OpenClaw 的 litellm extension。
 * LiteLLM 自部署后提供统一 OpenAI 格式 API。
 */
public class LiteLlmProvider implements LlmProvider {
    private final String baseUrl;
    private final String apiKey;
    private final HttpClient httpClient;

    public LiteLlmProvider() {
        this("http://localhost:4000/v1", null);
    }

    public LiteLlmProvider(String baseUrl, String apiKey) {
        this.baseUrl = baseUrl != null ? baseUrl : "http://localhost:4000/v1";
        this.apiKey = apiKey;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build();
    }
    @Override public String id() { return "litellm"; }
    @Override public String name() { return "LiteLLM Proxy"; }
    @Override public Mono<LlmResponse> chat(LlmRequest request) {
        return OpenAiCompatibleHelper.chat(httpClient, apiKey != null ? apiKey : "litellm", baseUrl, request);
    }
    @Override public Flux<LlmEvent> chatStream(LlmRequest request) {
        return OpenAiCompatibleHelper.chatStream(httpClient, apiKey != null ? apiKey : "litellm", baseUrl, request);
    }
    @Override public Flux<Model> listModels() {
        return OpenAiCompatibleHelper.listModels(httpClient, apiKey != null ? apiKey : "litellm", baseUrl, "litellm");
    }
    @Override public Mono<Boolean> healthCheck() {
        return OpenAiCompatibleHelper.healthCheck(httpClient, apiKey != null ? apiKey : "litellm", baseUrl);
    }
}
