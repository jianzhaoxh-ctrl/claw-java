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
 * Google Gemini Provider — 支持 Gemini Pro, Gemini Flash。
 * Gemini 有独特的 API 格式（contents/systemInstruction/parts）。
 */
public class GoogleProvider implements LlmProvider {
    private static final Logger log = LoggerFactory.getLogger(GoogleProvider.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String DEFAULT_BASE_URL = "https://generativelanguage.googleapis.com/v1beta";

    private final String apiKey;
    private final String baseUrl;
    private final HttpClient httpClient;

    public GoogleProvider(String apiKey) { this(apiKey, DEFAULT_BASE_URL); }
    public GoogleProvider(String apiKey, String baseUrl) {
        this.apiKey = apiKey;
        this.baseUrl = baseUrl != null ? baseUrl : DEFAULT_BASE_URL;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build();
    }

    @Override public String id() { return "google"; }
    @Override public String name() { return "Google Gemini"; }

    @Override
    public Mono<LlmResponse> chat(LlmRequest request) {
        return Mono.fromCallable(() -> {
            var modelId = request.modelId();
            var body = buildRequestBody(request);
            var httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/models/" + modelId + ":generateContent?key=" + apiKey))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .timeout(Duration.ofSeconds(60))
                .build();
            var response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new RuntimeException("Google Gemini API error: " + response.statusCode());
            }
            return parseResponse(MAPPER.readTree(response.body()));
        });
    }

    @Override
    public Flux<LlmEvent> chatStream(LlmRequest request) {
        return Flux.<LlmEvent>create(sink -> {
            try {
                var modelId = request.modelId();
                var body = buildRequestBody(request);
                var httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/models/" + modelId + ":streamGenerateContent?key=" + apiKey))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .timeout(Duration.ofSeconds(120))
                    .build();
                var response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofLines());
                if (response.statusCode() != 200) {
                    sink.error(new RuntimeException("Google Gemini API error: " + response.statusCode()));
                    return;
                }
                var partialMsg = new Message.AssistantMessage("", List.of());
                response.body()
                    .filter(line -> !line.isBlank())
                    .forEach(line -> {
                        try {
                            var chunk = MAPPER.readTree(line);
                            parseChunk(chunk, sink, partialMsg);
                        } catch (Exception e) {
                            log.warn("Failed to parse Gemini chunk: {}", e.getMessage());
                        }
                    });
                sink.next(new LlmEvent.TurnEnd(partialMsg, List.of()));
                sink.complete();
            } catch (Exception e) {
                sink.error(e);
            }
        });
    }

    @Override
    public Flux<Model> listModels() {
        return Flux.just(
            Model.of("gemini-2.5-pro", "Gemini 2.5 Pro", "google"),
            Model.of("gemini-2.5-flash", "Gemini 2.5 Flash", "google"),
            Model.of("gemini-1.5-pro", "Gemini 1.5 Pro", "google"),
            Model.of("gemini-1.5-flash", "Gemini 1.5 Flash", "google"),
            Model.of("gemini-ultra", "Gemini Ultra", "google")
        );
    }

    @Override
    public Mono<Boolean> healthCheck() {
        return Mono.fromCallable(() -> {
            var request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/models?key=" + apiKey))
                .GET()
                .timeout(Duration.ofSeconds(5))
                .build();
            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200;
        });
    }

    // ========== 请求构建 ==========

    private String buildRequestBody(LlmRequest request) {
        var root = MAPPER.createObjectNode();
        var contents = MAPPER.createArrayNode();

        for (var msg : request.messages()) {
            if (msg instanceof Message.SystemMessage m) {
                root.putObject("systemInstruction").putObject("parts").put("text", MessageContent.extractText(m.content()));
            } else if (msg instanceof Message.UserMessage m) {
                var entry = MAPPER.createObjectNode();
                entry.put("role", "user");
                entry.putObject("parts").put("text", MessageContent.extractText(m.content()));
                contents.add(entry);
            } else if (msg instanceof Message.AssistantMessage m) {
                var entry = MAPPER.createObjectNode();
                entry.put("role", "model");
                entry.putObject("parts").put("text", m.text());
                contents.add(entry);
            } else if (msg instanceof Message.ToolResultMessage m) {
                var entry = MAPPER.createObjectNode();
                entry.put("role", "function");
                entry.putObject("parts").put("text", MessageContent.extractText(m.content()));
                contents.add(entry);
            }
        }
        root.set("contents", contents);

        var config = root.putObject("generationConfig");
        if (request.temperature() != null) config.put("temperature", request.temperature());
        if (request.maxTokens() != null) config.put("maxOutputTokens", request.maxTokens());

        try { return MAPPER.writeValueAsString(root); }
        catch (Exception e) { throw new RuntimeException(e); }
    }

    // ========== 响应解析 ==========

    private LlmResponse parseResponse(JsonNode root) {
        var candidates = root.path("candidates");
        if (!candidates.isArray() || candidates.isEmpty()) return LlmResponse.empty();
        var first = candidates.get(0);
        var content = first.path("content").path("parts").path(0).path("text").asText("");

        var toolCalls = new ArrayList<ToolCall>();
        int idx = 0;
        for (var part : first.path("content").path("parts")) {
            if (part.has("functionCall")) {
                var fc = part.path("functionCall");
                toolCalls.add(new ToolCall("call_" + idx, fc.path("name").asText(), idx, fc.path("args").toString()));
            }
            idx++;
        }

        var usageMeta = root.path("usageMetadata");
        var usageInfo = new UsageInfo(
            usageMeta.path("promptTokenCount").asInt(0),
            usageMeta.path("candidatesTokenCount").asInt(0),
            usageMeta.path("totalTokenCount").asInt(0), null, null);

        return new LlmResponse(content, toolCalls, usageInfo, "stop", Map.of());
    }

    private void parseChunk(JsonNode chunk, FluxSink<LlmEvent> sink, Message.AssistantMessage partialMsg) {
        var candidates = chunk.path("candidates");
        if (!candidates.isArray() || candidates.isEmpty()) return;
        var parts = candidates.get(0).path("content").path("parts");
        for (var part : parts) {
            if (part.has("text")) {
                var text = part.path("text").asText("");
                if (!text.isEmpty()) sink.next(new LlmEvent.TextDelta(0, text, partialMsg));
            }
            if (part.has("functionCall")) {
                var fc = part.path("functionCall");
                sink.next(new LlmEvent.ToolCallStart(0, fc.path("name").asText(), partialMsg));
                sink.next(new LlmEvent.ToolCallEnd(0,
                    new MessageContent.ToolCallContent("call_0", fc.path("name").asText(), fc.path("args").toString()),
                    partialMsg));
            }
        }
        var usageMeta = chunk.path("usageMetadata");
        if (!usageMeta.isMissingNode()) {
            sink.next(new LlmEvent.Usage(new UsageInfo(
                usageMeta.path("promptTokenCount").asInt(0),
                usageMeta.path("candidatesTokenCount").asInt(0),
                usageMeta.path("totalTokenCount").asInt(0), null, null)));
        }
    }
}
