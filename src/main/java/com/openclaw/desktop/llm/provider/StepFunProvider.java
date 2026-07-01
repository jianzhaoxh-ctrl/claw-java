package com.openclaw.desktop.llm.provider;

import com.openclaw.desktop.llm.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.net.http.HttpClient;
import java.time.Duration;

/**
 * StepFun (阶跃星辰) Provider — 国产大模型。
 * 模型：step-2-16k, step-1-8k, step-1v-8k。
 * 使用 OpenAI 兼容 API。
 */
public class StepFunProvider implements LlmProvider {
    private static final String BASE_URL = "https://api.stepfun.com/v1";
    private final String apiKey;
    private final HttpClient httpClient;

    public StepFunProvider(String apiKey) {
        this.apiKey = apiKey;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build();
    }
    @Override public String id() { return "stepfun"; }
    @Override public String name() { return "StepFun (阶跃星辰)"; }
    @Override public Mono<LlmResponse> chat(LlmRequest request) { return OpenAiCompatibleHelper.chat(httpClient, apiKey, BASE_URL, request); }
    @Override public Flux<LlmEvent> chatStream(LlmRequest request) { return OpenAiCompatibleHelper.chatStream(httpClient, apiKey, BASE_URL, request); }
    @Override public Flux<Model> listModels() {
        return Flux.just(
            Model.of("step-2-16k", "Step 2 (16K)", "stepfun"),
            Model.of("step-1-8k", "Step 1 (8K)", "stepfun"),
            Model.of("step-1v-8k", "Step 1V Vision (8K)", "stepfun")
        );
    }
    @Override public Mono<Boolean> healthCheck() { return OpenAiCompatibleHelper.healthCheck(httpClient, apiKey, BASE_URL); }
}
