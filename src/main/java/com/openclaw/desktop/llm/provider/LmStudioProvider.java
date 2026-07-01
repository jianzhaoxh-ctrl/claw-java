package com.openclaw.desktop.llm.provider;

import com.openclaw.desktop.llm.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.net.http.HttpClient;
import java.time.Duration;

/**
 * LM Studio Provider — 本地模型推理（OpenAI 兼容 API）。
 * 对应 OpenClaw 的 lmstudio extension。
 * LM Studio 是桌面应用，本地运行开源模型，通过 localhost:1234 提供 API。
 */
public class LmStudioProvider implements LlmProvider {
    private static final String DEFAULT_BASE_URL = "http://localhost:1234/v1";
    private final String baseUrl;
    private final HttpClient httpClient;

    public LmStudioProvider() {
        this(null);
    }

    public LmStudioProvider(String baseUrl) {
        this.baseUrl = baseUrl != null ? baseUrl : DEFAULT_BASE_URL;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }
    @Override public String id() { return "lmstudio"; }
    @Override public String name() { return "LM Studio (Local)"; }
    @Override public Mono<LlmResponse> chat(LlmRequest request) {
        // LM Studio 不需要 API Key，传空字符串
        return OpenAiCompatibleHelper.chat(httpClient, "lm-studio", baseUrl, request);
    }
    @Override public Flux<LlmEvent> chatStream(LlmRequest request) {
        return OpenAiCompatibleHelper.chatStream(httpClient, "lm-studio", baseUrl, request);
    }
    @Override public Flux<Model> listModels() {
        return OpenAiCompatibleHelper.listModels(httpClient, "lm-studio", baseUrl, "lmstudio");
    }
    @Override public Mono<Boolean> healthCheck() {
        return OpenAiCompatibleHelper.healthCheck(httpClient, "lm-studio", baseUrl);
    }
}
