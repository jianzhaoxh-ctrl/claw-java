package com.openclaw.desktop.llm.provider;

import com.openclaw.desktop.llm.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Perplexity Provider — AI 搜索 + 对话。
 * 对应 OpenClaw 的 perplexity extension。
 * 模型：sonar, sonar-pro, sonar-reasoning, sonar-reasoning-pro。
 */
public class PerplexityProvider implements LlmProvider {
    private static final String BASE_URL = "https://api.perplexity.ai";
    private final String apiKey;
    private final HttpClient httpClient;

    public PerplexityProvider(String apiKey) {
        this.apiKey = apiKey;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build();
    }
    @Override public String id() { return "perplexity"; }
    @Override public String name() { return "Perplexity AI"; }
    @Override public Mono<LlmResponse> chat(LlmRequest request) { return OpenAiCompatibleHelper.chat(httpClient, apiKey, BASE_URL, request); }
    @Override public Flux<LlmEvent> chatStream(LlmRequest request) { return OpenAiCompatibleHelper.chatStream(httpClient, apiKey, BASE_URL, request); }
    @Override public Flux<Model> listModels() {
        return Flux.just(
            Model.of("sonar", "Sonar", "perplexity"),
            Model.of("sonar-pro", "Sonar Pro", "perplexity"),
            Model.of("sonar-reasoning", "Sonar Reasoning", "perplexity"),
            Model.of("sonar-reasoning-pro", "Sonar Reasoning Pro", "perplexity")
        );
    }
    @Override public Mono<Boolean> healthCheck() { return OpenAiCompatibleHelper.healthCheck(httpClient, apiKey, BASE_URL); }
}
