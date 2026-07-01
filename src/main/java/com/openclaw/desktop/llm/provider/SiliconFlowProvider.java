package com.openclaw.desktop.llm.provider;

import com.openclaw.desktop.llm.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.net.http.HttpClient;
import java.time.Duration;

/**
 * SiliconFlow (硅基流动) Provider — 国内 AI 推理平台。
 * 提供大量开源模型的快速推理服务，OpenAI 兼容 API。
 * 模型：Qwen/Qwen2.5-72B-Instruct, deepseek-ai/DeepSeek-V3 等。
 */
public class SiliconFlowProvider implements LlmProvider {
    private static final String BASE_URL = "https://api.siliconflow.cn/v1";
    private final String apiKey;
    private final HttpClient httpClient;

    public SiliconFlowProvider(String apiKey) {
        this.apiKey = apiKey;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build();
    }
    @Override public String id() { return "siliconflow"; }
    @Override public String name() { return "SiliconFlow (硅基流动)"; }
    @Override public Mono<LlmResponse> chat(LlmRequest request) { return OpenAiCompatibleHelper.chat(httpClient, apiKey, BASE_URL, request); }
    @Override public Flux<LlmEvent> chatStream(LlmRequest request) { return OpenAiCompatibleHelper.chatStream(httpClient, apiKey, BASE_URL, request); }
    @Override public Flux<Model> listModels() {
        return Flux.just(
            Model.of("Qwen/Qwen2.5-72B-Instruct", "Qwen 2.5 72B", "siliconflow"),
            Model.of("Qwen/Qwen2.5-7B-Instruct", "Qwen 2.5 7B", "siliconflow"),
            Model.of("deepseek-ai/DeepSeek-V3", "DeepSeek V3", "siliconflow"),
            Model.of("deepseek-ai/DeepSeek-R1", "DeepSeek R1", "siliconflow")
        );
    }
    @Override public Mono<Boolean> healthCheck() { return OpenAiCompatibleHelper.healthCheck(httpClient, apiKey, BASE_URL); }
}
