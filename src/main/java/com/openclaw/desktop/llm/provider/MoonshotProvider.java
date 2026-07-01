package com.openclaw.desktop.llm.provider;

import com.openclaw.desktop.llm.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Moonshot (Kimi) Provider — 月之暗面 AI。
 * 对应 OpenClaw 的 moonshot extension。
 * 模型：moonshot-v1-8k, moonshot-v1-32k, moonshot-v1-128k。
 */
public class MoonshotProvider implements LlmProvider {
    private static final String BASE_URL = "https://api.moonshot.cn/v1";
    private final String apiKey;
    private final HttpClient httpClient;

    public MoonshotProvider(String apiKey) {
        this.apiKey = apiKey;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build();
    }
    @Override public String id() { return "moonshot"; }
    @Override public String name() { return "Moonshot (Kimi)"; }
    @Override public Mono<LlmResponse> chat(LlmRequest request) { return OpenAiCompatibleHelper.chat(httpClient, apiKey, BASE_URL, request); }
    @Override public Flux<LlmEvent> chatStream(LlmRequest request) { return OpenAiCompatibleHelper.chatStream(httpClient, apiKey, BASE_URL, request); }
    @Override public Flux<Model> listModels() {
        return Flux.just(
            Model.of("moonshot-v1-8k", "Moonshot v1 (8K)", "moonshot"),
            Model.of("moonshot-v1-32k", "Moonshot v1 (32K)", "moonshot"),
            Model.of("moonshot-v1-128k", "Moonshot v1 (128K)", "moonshot"),
            Model.of("kimi-latest", "Kimi Latest", "moonshot")
        );
    }
    @Override public Mono<Boolean> healthCheck() { return OpenAiCompatibleHelper.healthCheck(httpClient, apiKey, BASE_URL); }
}
