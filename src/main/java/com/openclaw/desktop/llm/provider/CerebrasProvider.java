package com.openclaw.desktop.llm.provider;

import com.openclaw.desktop.llm.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Cerebras Provider — 超高速 AI 推理。
 * 对应 OpenClaw 的 cerebras extension。
 * 模型：llama3.1-8b, llama3.1-70b — 推理速度极快。
 */
public class CerebrasProvider implements LlmProvider {
    private static final String BASE_URL = "https://api.cerebras.ai/v1";
    private final String apiKey;
    private final HttpClient httpClient;

    public CerebrasProvider(String apiKey) {
        this.apiKey = apiKey;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build();
    }
    @Override public String id() { return "cerebras"; }
    @Override public String name() { return "Cerebras"; }
    @Override public Mono<LlmResponse> chat(LlmRequest request) { return OpenAiCompatibleHelper.chat(httpClient, apiKey, BASE_URL, request); }
    @Override public Flux<LlmEvent> chatStream(LlmRequest request) { return OpenAiCompatibleHelper.chatStream(httpClient, apiKey, BASE_URL, request); }
    @Override public Flux<Model> listModels() {
        return Flux.just(
            Model.of("llama3.1-8b", "Llama 3.1 8B (超快)", "cerebras"),
            Model.of("llama3.1-70b", "Llama 3.1 70B", "cerebras")
        );
    }
    @Override public Mono<Boolean> healthCheck() { return OpenAiCompatibleHelper.healthCheck(httpClient, apiKey, BASE_URL); }
}
