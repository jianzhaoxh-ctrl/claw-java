package com.openclaw.desktop.llm.provider;

import com.openclaw.desktop.llm.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;

/**
 * OpenRouter Provider — 统一路由 100+ 模型。
 * 对应 OpenClaw 的 openrouter extension。
 * 支持的模型：openai/gpt-4o, anthropic/claude-3.5-sonnet, google/gemini-pro 等。
 */
public class OpenRouterProvider implements LlmProvider {

    private static final String BASE_URL = "https://openrouter.ai/api/v1";
    private final String apiKey;
    private final HttpClient httpClient;

    public OpenRouterProvider(String apiKey) {
        this.apiKey = apiKey;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();
    }

    @Override public String id() { return "openrouter"; }
    @Override public String name() { return "OpenRouter"; }

    @Override public Mono<LlmResponse> chat(LlmRequest request) {
        return OpenAiCompatibleHelper.chat(httpClient, apiKey, BASE_URL, request);
    }

    @Override public Flux<LlmEvent> chatStream(LlmRequest request) {
        return OpenAiCompatibleHelper.chatStream(httpClient, apiKey, BASE_URL, request);
    }

    @Override public Flux<Model> listModels() {
        return OpenAiCompatibleHelper.listModels(httpClient, apiKey, BASE_URL, "openrouter");
    }

    @Override public Mono<Boolean> healthCheck() {
        return OpenAiCompatibleHelper.healthCheck(httpClient, apiKey, BASE_URL);
    }
}
