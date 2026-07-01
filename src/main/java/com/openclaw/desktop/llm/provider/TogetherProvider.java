package com.openclaw.desktop.llm.provider;

import com.openclaw.desktop.llm.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Together AI Provider — 开源模型托管平台。
 * 对应 OpenClaw 的 together extension。
 * 模型：meta-llama/Llama-3.3-70B-Instruct-Turbo, mistralai/Mixtral-8x7B-Instruct-v0.1 等。
 */
public class TogetherProvider implements LlmProvider {

    private static final String BASE_URL = "https://api.together.xyz/v1";
    private final String apiKey;
    private final HttpClient httpClient;

    public TogetherProvider(String apiKey) {
        this.apiKey = apiKey;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build();
    }

    @Override public String id() { return "together"; }
    @Override public String name() { return "Together AI"; }
    @Override public Mono<LlmResponse> chat(LlmRequest request) {
        return OpenAiCompatibleHelper.chat(httpClient, apiKey, BASE_URL, request);
    }
    @Override public Flux<LlmEvent> chatStream(LlmRequest request) {
        return OpenAiCompatibleHelper.chatStream(httpClient, apiKey, BASE_URL, request);
    }
    @Override public Flux<Model> listModels() {
        return OpenAiCompatibleHelper.listModels(httpClient, apiKey, BASE_URL, "together");
    }
    @Override public Mono<Boolean> healthCheck() {
        return OpenAiCompatibleHelper.healthCheck(httpClient, apiKey, BASE_URL);
    }
}
