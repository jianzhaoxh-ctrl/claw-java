package com.openclaw.desktop.llm.provider;

import com.openclaw.desktop.llm.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.net.http.HttpClient;
import java.time.Duration;

/**
 * 火山引擎 (豆包) Provider — 字节跳动 AI。
 * 对应 OpenClaw 的 volcengine / byteplus extension。
 * 模型：doubao-pro-32k, doubao-pro-128k, doubao-lite-32k。
 * 使用 OpenAI 兼容 API（ Ark 方舟平台）。
 */
public class VolcEngineProvider implements LlmProvider {
    private static final String BASE_URL = "https://ark.cn-beijing.volces.com/api/v3";
    private final String apiKey;
    private final HttpClient httpClient;

    public VolcEngineProvider(String apiKey) {
        this.apiKey = apiKey;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build();
    }
    @Override public String id() { return "volcengine"; }
    @Override public String name() { return "火山引擎 (豆包)"; }
    @Override public Mono<LlmResponse> chat(LlmRequest request) { return OpenAiCompatibleHelper.chat(httpClient, apiKey, BASE_URL, request); }
    @Override public Flux<LlmEvent> chatStream(LlmRequest request) { return OpenAiCompatibleHelper.chatStream(httpClient, apiKey, BASE_URL, request); }
    @Override public Flux<Model> listModels() {
        return Flux.just(
            Model.of("doubao-pro-32k", "Doubao Pro (32K)", "volcengine"),
            Model.of("doubao-pro-128k", "Doubao Pro (128K)", "volcengine"),
            Model.of("doubao-lite-32k", "Doubao Lite (32K)", "volcengine"),
            Model.of("doubao-1.5-pro-256k", "Doubao 1.5 Pro (256K)", "volcengine"),
            Model.of("deepseek-v3-241226", "DeepSeek V3", "volcengine")
        );
    }
    @Override public Mono<Boolean> healthCheck() { return OpenAiCompatibleHelper.healthCheck(httpClient, apiKey, BASE_URL); }
}
