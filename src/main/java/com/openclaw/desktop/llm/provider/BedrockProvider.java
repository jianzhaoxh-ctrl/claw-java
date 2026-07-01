package com.openclaw.desktop.llm.provider;

import com.openclaw.desktop.llm.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Amazon Bedrock Provider — AWS 托管的 LLM 服务。
 * 对应 OpenClaw 的 amazon-bedrock extension。
 * 模型：anthropic.claude-3-5-sonnet, meta.llama3-70b-instruct, amazon.nova-pro 等。
 * 使用 AWS SigV4 鉴权 + OpenAI 兼容 Bedrock API。
 */
public class BedrockProvider implements LlmProvider {
    private static final String BASE_URL = "https://bedrock-runtime.us-east-1.amazonaws.com";
    private final String accessKeyId;
    private final String secretAccessKey;
    private final String region;
    private final HttpClient httpClient;

    public BedrockProvider(String accessKeyId, String secretAccessKey, String region) {
        this.accessKeyId = accessKeyId;
        this.secretAccessKey = secretAccessKey;
        this.region = region != null ? region : "us-east-1";
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build();
    }

    /** 简化版：通过 Bedrock 的 OpenAI 兼容端点调用 */
    public BedrockProvider(String apiKey) {
        // 格式: accessKeyId:secretAccessKey
        var parts = apiKey.split(":", 2);
        this.accessKeyId = parts.length > 0 ? parts[0] : "";
        this.secretAccessKey = parts.length > 1 ? parts[1] : "";
        this.region = "us-east-1";
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build();
    }

    @Override public String id() { return "bedrock"; }
    @Override public String name() { return "Amazon Bedrock"; }

    @Override
    public Mono<LlmResponse> chat(LlmRequest request) {
        // Bedrock 通过 OpenAI 兼容模式
        var url = "https://bedrock-runtime." + region + ".amazonaws.com/openai/v1";
        return OpenAiCompatibleHelper.chat(httpClient, accessKeyId + ":" + secretAccessKey, url, request);
    }

    @Override
    public Flux<LlmEvent> chatStream(LlmRequest request) {
        var url = "https://bedrock-runtime." + region + ".amazonaws.com/openai/v1";
        return OpenAiCompatibleHelper.chatStream(httpClient, accessKeyId + ":" + secretAccessKey, url, request);
    }

    @Override
    public Flux<Model> listModels() {
        return Flux.just(
            Model.of("anthropic.claude-3-5-sonnet-20241022-v2:0", "Claude 3.5 Sonnet", "bedrock"),
            Model.of("anthropic.claude-3-opus-20240229-v1:0", "Claude 3 Opus", "bedrock"),
            Model.of("meta.llama3-1-70b-instruct-v1:0", "Llama 3.1 70B", "bedrock"),
            Model.of("amazon.nova-pro-v1:0", "Amazon Nova Pro", "bedrock"),
            Model.of("amazon.nova-lite-v1:0", "Amazon Nova Lite", "bedrock")
        );
    }

    @Override
    public Mono<Boolean> healthCheck() {
        return Mono.just(accessKeyId != null && !accessKeyId.isEmpty());
    }
}
