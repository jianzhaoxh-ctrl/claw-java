package com.openclaw.desktop.llm.provider;

import com.openclaw.desktop.llm.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
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
 * Mistral Provider — OpenAI-compatible API。
 */
public class MistralProvider implements LlmProvider {
    private static final Logger log = LoggerFactory.getLogger(MistralProvider.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String DEFAULT_BASE_URL = "https://api.mistral.ai/v1";

    private final String apiKey;
    private final String baseUrl;
    private final HttpClient httpClient;

    public MistralProvider(String apiKey) { this(apiKey, DEFAULT_BASE_URL); }
    public MistralProvider(String apiKey, String baseUrl) {
        this.apiKey = apiKey;
        this.baseUrl = baseUrl != null ? baseUrl : DEFAULT_BASE_URL;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build();
    }

    @Override public String id() { return "mistral"; }
    @Override public String name() { return "Mistral AI"; }

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
            Model.of("mistral-large-latest", "Mistral Large", "mistral"),
            Model.of("mistral-medium-latest", "Mistral Medium", "mistral"),
            Model.of("mistral-small-latest", "Mistral Small", "mistral"),
            Model.of("codestral-latest", "Codestral", "mistral"),
            Model.of("mistral-embed", "Mistral Embed", "mistral")
        );
    }

    @Override
    public Mono<Boolean> healthCheck() {
        return OpenAiCompatibleHelper.healthCheck(httpClient, apiKey, baseUrl);
    }
}
