package com.openclaw.desktop.llm.provider;

import com.openclaw.desktop.llm.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Fireworks AI Provider — 高速开源模型推理。
 * 对应 OpenClaw 的 fireworks extension。
 * 模型：accounts/fireworks/models/llama-v3p3-70b-instruct 等。
 */
public class FireworksProvider implements LlmProvider {
    private static final String BASE_URL = "https://api.fireworks.ai/inference/v1";
    private final String apiKey;
    private final HttpClient httpClient;

    public FireworksProvider(String apiKey) {
        this.apiKey = apiKey;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build();
    }
    @Override public String id() { return "fireworks"; }
    @Override public String name() { return "Fireworks AI"; }
    @Override public Mono<LlmResponse> chat(LlmRequest request) { return OpenAiCompatibleHelper.chat(httpClient, apiKey, BASE_URL, request); }
    @Override public Flux<LlmEvent> chatStream(LlmRequest request) { return OpenAiCompatibleHelper.chatStream(httpClient, apiKey, BASE_URL, request); }
    @Override public Flux<Model> listModels() { return OpenAiCompatibleHelper.listModels(httpClient, apiKey, BASE_URL, "fireworks"); }
    @Override public Mono<Boolean> healthCheck() { return OpenAiCompatibleHelper.healthCheck(httpClient, apiKey, BASE_URL); }
}
