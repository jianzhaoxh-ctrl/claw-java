package com.openclaw.desktop.llm.provider;

import com.openclaw.desktop.llm.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.net.http.HttpClient;
import java.time.Duration;

/**
 * MiniMax Provider — 国内大模型公司。
 * 对应 OpenClaw 的 minimax extension。
 * 模型：abab6.5s-chat, abab6.5g-chat, abab6.5t-chat。
 */
public class MiniMaxProvider implements LlmProvider {
    private static final String BASE_URL = "https://api.minimax.chat/v1";
    private final String apiKey;
    private final HttpClient httpClient;

    public MiniMaxProvider(String apiKey) {
        this.apiKey = apiKey;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build();
    }
    @Override public String id() { return "minimax"; }
    @Override public String name() { return "MiniMax"; }
    @Override public Mono<LlmResponse> chat(LlmRequest request) { return OpenAiCompatibleHelper.chat(httpClient, apiKey, BASE_URL, request); }
    @Override public Flux<LlmEvent> chatStream(LlmRequest request) { return OpenAiCompatibleHelper.chatStream(httpClient, apiKey, BASE_URL, request); }
    @Override public Flux<Model> listModels() {
        return Flux.just(
            Model.of("abab6.5s-chat", "ABAB 6.5s Chat", "minimax"),
            Model.of("abab6.5g-chat", "ABAB 6.5g Chat", "minimax"),
            Model.of("abab6.5t-chat", "ABAB 6.5t Chat", "minimax")
        );
    }
    @Override public Mono<Boolean> healthCheck() { return OpenAiCompatibleHelper.healthCheck(httpClient, apiKey, BASE_URL); }
}
