package com.openclaw.desktop.llm.provider;

import com.openclaw.desktop.llm.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.net.http.HttpClient;
import java.time.Duration;

/**
 * NVIDIA NIM Provider — NVIDIA 推理微服务。
 * 对应 OpenClaw 的 nvidia extension。
 * 模型：meta/llama-3.3-70b-instruct, nvidia/llama-3.1-nemotron-70b-instruct 等。
 */
public class NvidiaProvider implements LlmProvider {
    private static final String BASE_URL = "https://integrate.api.nvidia.com/v1";
    private final String apiKey;
    private final HttpClient httpClient;

    public NvidiaProvider(String apiKey) {
        this.apiKey = apiKey;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build();
    }
    @Override public String id() { return "nvidia"; }
    @Override public String name() { return "NVIDIA NIM"; }
    @Override public Mono<LlmResponse> chat(LlmRequest request) { return OpenAiCompatibleHelper.chat(httpClient, apiKey, BASE_URL, request); }
    @Override public Flux<LlmEvent> chatStream(LlmRequest request) { return OpenAiCompatibleHelper.chatStream(httpClient, apiKey, BASE_URL, request); }
    @Override public Flux<Model> listModels() { return OpenAiCompatibleHelper.listModels(httpClient, apiKey, BASE_URL, "nvidia"); }
    @Override public Mono<Boolean> healthCheck() { return OpenAiCompatibleHelper.healthCheck(httpClient, apiKey, BASE_URL); }
}
