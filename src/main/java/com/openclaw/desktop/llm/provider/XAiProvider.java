package com.openclaw.desktop.llm.provider;

import com.openclaw.desktop.llm.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.net.http.HttpClient;
import java.time.Duration;

/**
 * xAI (Grok) Provider — Elon Musk 的 AI 公司。
 * 对应 OpenClaw 的 xai extension。
 * 模型：grok-3, grok-3-mini, grok-2, grok-beta。
 */
public class XAiProvider implements LlmProvider {
    private static final String BASE_URL = "https://api.x.ai/v1";
    private final String apiKey;
    private final HttpClient httpClient;

    public XAiProvider(String apiKey) {
        this.apiKey = apiKey;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build();
    }
    @Override public String id() { return "xai"; }
    @Override public String name() { return "xAI (Grok)"; }
    @Override public Mono<LlmResponse> chat(LlmRequest request) { return OpenAiCompatibleHelper.chat(httpClient, apiKey, BASE_URL, request); }
    @Override public Flux<LlmEvent> chatStream(LlmRequest request) { return OpenAiCompatibleHelper.chatStream(httpClient, apiKey, BASE_URL, request); }
    @Override public Flux<Model> listModels() {
        return Flux.just(
            Model.of("grok-3", "Grok 3", "xai"),
            Model.of("grok-3-mini", "Grok 3 Mini", "xai"),
            Model.of("grok-2", "Grok 2", "xai"),
            Model.of("grok-beta", "Grok Beta", "xai")
        );
    }
    @Override public Mono<Boolean> healthCheck() { return OpenAiCompatibleHelper.healthCheck(httpClient, apiKey, BASE_URL); }
}
