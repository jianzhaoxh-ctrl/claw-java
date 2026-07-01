package com.openclaw.desktop.llm.provider;

import com.openclaw.desktop.llm.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.net.http.HttpClient;
import java.time.Duration;

/**
 * 百度千帆 Provider — 文心一言大模型。
 * 对应 OpenClaw 的 qianfan extension。
 * 模型：ernie-4.0-8k-latest, ernie-3.5-8k, ernie-speed-128k, ernie-tiny-8k。
 * 千帆使用 OpenAI 兼容模式 API。
 */
public class QianfanProvider implements LlmProvider {
    private static final String BASE_URL = "https://qianfan.baidubce.com/v2";
    private final String apiKey;
    private final HttpClient httpClient;

    public QianfanProvider(String apiKey) {
        this.apiKey = apiKey;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build();
    }
    @Override public String id() { return "qianfan"; }
    @Override public String name() { return "百度千帆 (文心一言)"; }
    @Override public Mono<LlmResponse> chat(LlmRequest request) { return OpenAiCompatibleHelper.chat(httpClient, apiKey, BASE_URL, request); }
    @Override public Flux<LlmEvent> chatStream(LlmRequest request) { return OpenAiCompatibleHelper.chatStream(httpClient, apiKey, BASE_URL, request); }
    @Override public Flux<Model> listModels() {
        return Flux.just(
            Model.of("ernie-4.0-8k-latest", "ERNIE 4.0 (8K)", "qianfan"),
            Model.of("ernie-3.5-8k", "ERNIE 3.5 (8K)", "qianfan"),
            Model.of("ernie-speed-128k", "ERNIE Speed (128K)", "qianfan"),
            Model.of("ernie-tiny-8k", "ERNIE Tiny (8K)", "qianfan"),
            Model.of("deepseek-v3", "DeepSeek V3", "qianfan"),
            Model.of("qwen-max", "Qwen Max", "qianfan")
        );
    }
    @Override public Mono<Boolean> healthCheck() { return OpenAiCompatibleHelper.healthCheck(httpClient, apiKey, BASE_URL); }
}
