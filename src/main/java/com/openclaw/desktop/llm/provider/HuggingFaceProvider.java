package com.openclaw.desktop.llm.provider;

import com.openclaw.desktop.llm.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.net.http.HttpClient;
import java.time.Duration;

/**
 * HuggingFace Provider — 通过 Inference API 访问 10 万+ 模型。
 * 对应 OpenClaw 的 huggingface extension。
 */
public class HuggingFaceProvider implements LlmProvider {
    private static final String BASE_URL = "https://api-inference.huggingface.co/models";
    private final String apiKey;
    private final HttpClient httpClient;

    public HuggingFaceProvider(String apiKey) {
        this.apiKey = apiKey;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(60)).build();
    }
    @Override public String id() { return "huggingface"; }
    @Override public String name() { return "HuggingFace"; }
    @Override public Mono<LlmResponse> chat(LlmRequest request) { return OpenAiCompatibleHelper.chat(httpClient, "hf_" + apiKey, "https://api-inference.huggingface.co/v1", request); }
    @Override public Flux<LlmEvent> chatStream(LlmRequest request) { return OpenAiCompatibleHelper.chatStream(httpClient, "hf_" + apiKey, "https://api-inference.huggingface.co/v1", request); }
    @Override public Flux<Model> listModels() { return Flux.just(Model.of("meta-llama/Llama-3.3-70B-Instruct", "Llama 3.3 70B", "huggingface"), Model.of("mistralai/Mistral-7B-Instruct-v0.3", "Mistral 7B", "huggingface"), Model.of("Qwen/Qwen2.5-72B-Instruct", "Qwen 2.5 72B", "huggingface")); }
    @Override public Mono<Boolean> healthCheck() { return OpenAiCompatibleHelper.healthCheck(httpClient, "hf_" + apiKey, "https://api-inference.huggingface.co/v1"); }
}
