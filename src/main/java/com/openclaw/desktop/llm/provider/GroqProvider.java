package com.openclaw.desktop.llm.provider;

import com.openclaw.desktop.llm.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Groq Provider — LPU 快速推理。OpenAI-compatible API。
 */
public class GroqProvider implements LlmProvider {
    private static final Logger log = LoggerFactory.getLogger(GroqProvider.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String DEFAULT_BASE_URL = "https://api.groq.com/openai/v1";

    private final String apiKey;
    private final String baseUrl;
    private final HttpClient httpClient;

    public GroqProvider(String apiKey) { this(apiKey, DEFAULT_BASE_URL); }
    public GroqProvider(String apiKey, String baseUrl) {
        this.apiKey = apiKey;
        this.baseUrl = baseUrl != null ? baseUrl : DEFAULT_BASE_URL;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build();
    }

    @Override public String id() { return "groq"; }
    @Override public String name() { return "Groq (LPU)"; }

    @Override
    public Mono<LlmResponse> chat(LlmRequest request) {
        return OpenAiCompatibleHelper.chat(httpClient, apiKey, baseUrl, request);
    }

    @Override
    public Flux<LlmEvent> chatStream(LlmRequest request) {
        return OpenAiCompatibleHelper.chatStream(httpClient, apiKey, baseUrl, request);
    }

    @Override
    public Flux<Model> listModels() {
        return Flux.just(
            Model.of("llama-3.3-70b-versatile", "Llama 3.3 70B Versatile", "groq"),
            Model.of("llama-3.1-8b-instant", "Llama 3.1 8B Instant", "groq"),
            Model.of("mixtral-8x7b-32768", "Mixtral 8x7B (32K)", "groq"),
            Model.of("gemma2-9b-it", "Gemma 2 9B IT", "groq"),
            Model.of("deepseek-r1-distill-llama-70b", "DeepSeek R1 Distill 70B", "groq")
        );
    }

    @Override
    public Mono<Boolean> healthCheck() {
        return OpenAiCompatibleHelper.healthCheck(httpClient, apiKey, baseUrl);
    }
}
