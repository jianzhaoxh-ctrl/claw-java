package com.openclaw.desktop.llm.provider;

import com.openclaw.desktop.llm.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Cohere Provider — Command R+ 系列模型。
 * 对应 OpenClaw 的 cohere extension。
 * 模型：command-r-plus, command-r, command, command-light。
 */
public class CohereProvider implements LlmProvider {
    private static final String BASE_URL = "https://api.cohere.ai/v1";
    private final String apiKey;
    private final HttpClient httpClient;

    public CohereProvider(String apiKey) {
        this.apiKey = apiKey;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build();
    }
    @Override public String id() { return "cohere"; }
    @Override public String name() { return "Cohere"; }
    @Override public Mono<LlmResponse> chat(LlmRequest request) { return OpenAiCompatibleHelper.chat(httpClient, apiKey, BASE_URL, request); }
    @Override public Flux<LlmEvent> chatStream(LlmRequest request) { return OpenAiCompatibleHelper.chatStream(httpClient, apiKey, BASE_URL, request); }
    @Override public Flux<Model> listModels() {
        return Flux.just(
            Model.of("command-r-plus", "Command R+", "cohere"),
            Model.of("command-r", "Command R", "cohere"),
            Model.of("command", "Command", "cohere"),
            Model.of("command-light", "Command Light", "cohere")
        );
    }
    @Override public Mono<Boolean> healthCheck() { return OpenAiCompatibleHelper.healthCheck(httpClient, apiKey, BASE_URL); }
}
