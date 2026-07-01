package com.openclaw.desktop.llm.provider;

import com.openclaw.desktop.llm.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.net.http.HttpClient;
import java.time.Duration;

/**
 * vLLM Provider — 高吞吐本地模型推理引擎。
 * 对应 OpenClaw 的 vllm extension。
 * vLLM 通常部署在 GPU 服务器，提供 OpenAI 兼容 API。
 */
public class VllmProvider implements LlmProvider {
    private final String baseUrl;
    private final HttpClient httpClient;

    public VllmProvider() {
        this("http://localhost:8000/v1");
    }

    public VllmProvider(String baseUrl) {
        this.baseUrl = baseUrl != null ? baseUrl : "http://localhost:8000/v1";
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build();
    }
    @Override public String id() { return "vllm"; }
    @Override public String name() { return "vLLM"; }
    @Override public Mono<LlmResponse> chat(LlmRequest request) {
        return OpenAiCompatibleHelper.chat(httpClient, "vllm", baseUrl, request);
    }
    @Override public Flux<LlmEvent> chatStream(LlmRequest request) {
        return OpenAiCompatibleHelper.chatStream(httpClient, "vllm", baseUrl, request);
    }
    @Override public Flux<Model> listModels() {
        return OpenAiCompatibleHelper.listModels(httpClient, "vllm", baseUrl, "vllm");
    }
    @Override public Mono<Boolean> healthCheck() {
        return OpenAiCompatibleHelper.healthCheck(httpClient, "vllm", baseUrl);
    }
}
