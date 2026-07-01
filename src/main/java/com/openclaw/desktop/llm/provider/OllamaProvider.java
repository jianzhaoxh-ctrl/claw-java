package com.openclaw.desktop.llm.provider;

import com.openclaw.desktop.llm.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
 * Ollama LLM Provider — 本地模型支持。
 * 适用于信创环境（离线运行，无需 API Key）。
 * 支持 llama3, qwen2, deepseek-r1 等本地模型。
 */
public class OllamaProvider implements LlmProvider {

    private static final Logger log = LoggerFactory.getLogger(OllamaProvider.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String DEFAULT_BASE_URL = "http://localhost:11434";

    private final String baseUrl;
    private final HttpClient httpClient;

    public OllamaProvider() {
        this(DEFAULT_BASE_URL);
    }

    public OllamaProvider(String baseUrl) {
        this.baseUrl = baseUrl != null ? baseUrl : DEFAULT_BASE_URL;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    }

    @Override
    public String id() { return "ollama"; }

    @Override
    public String name() { return "Ollama (Local)"; }

    @Override
    public Mono<LlmResponse> chat(LlmRequest request) {
        return Mono.fromCallable(() -> {
            var body = buildRequestBody(request, false);
            var httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/chat"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .timeout(Duration.ofMinutes(10))
                .build();

            var response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new RuntimeException("Ollama API error: " + response.statusCode() + " - " + response.body());
            }
            return parseResponse(MAPPER.readTree(response.body()));
        });
    }

    @Override
    public Flux<LlmEvent> chatStream(LlmRequest request) {
        return Flux.<LlmEvent>create(sink -> {
            try {
                var body = buildRequestBody(request, true);
                var httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/chat"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .timeout(Duration.ofMinutes(10))
                    .build();

                var response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofLines());
                if (response.statusCode() != 200) {
                    sink.error(new RuntimeException("Ollama API error: " + response.statusCode()));
                    return;
                }

                response.body().forEach(line -> {
                    try {
                        var chunk = MAPPER.readTree(line);
                        if (chunk.has("message")) {
                            var content = chunk.path("message").path("content").asText("");
                            if (!content.isEmpty()) {
                                sink.next(new LlmEvent.TextDelta(0, content));
                            }
                        }
                        if (chunk.path("done").asBoolean(false)) {
                            sink.next(new LlmEvent.AgentEnd(java.util.List.of()));
                            sink.complete();
                        }
                    } catch (Exception e) {
                        log.warn("Failed to parse Ollama stream chunk: {}", e.getMessage());
                    }
                });

                if (!sink.isCancelled()) {
                    sink.complete();
                }
            } catch (Exception e) {
                sink.error(e);
            }
        });
    }

    @Override
    public Flux<Model> listModels() {
        return Mono.fromCallable(() -> {
            var request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/tags"))
                .GET()
                .timeout(Duration.ofSeconds(10))
                .build();
            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) return List.<Model>of();

            var root = MAPPER.readTree(response.body());
            var models = new ArrayList<Model>();
            var modelsArray = root.path("models");
            if (modelsArray.isArray()) {
                for (var m : modelsArray) {
                    var name = m.path("name").asText();
                    models.add(Model.of(name, name, "ollama"));
                }
            }
            return models;
        }).flatMapMany(Flux::fromIterable);
    }

    @Override
    public Mono<Boolean> healthCheck() {
        return Mono.fromCallable(() -> {
            var request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/tags"))
                .GET()
                .timeout(Duration.ofSeconds(5))
                .build();
            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200;
        }).onErrorReturn(false);
    }

    // ---- internal ----

    private String buildRequestBody(LlmRequest request, boolean stream) throws Exception {
        var root = MAPPER.createObjectNode();
        root.put("model", request.modelId());
        root.put("stream", stream);

        if (request.temperature() != null) root.put("temperature", request.temperature());
        if (request.maxTokens() != null) root.put("num_predict", request.maxTokens());

        var messages = MAPPER.createArrayNode();
        for (var msg : request.messages()) {
            var msgNode = MAPPER.createObjectNode();
            switch (msg) {
                case Message.SystemMessage(var contents, var ts) -> {
                    msgNode.put("role", "system");
                    msgNode.put("content", MessageContent.extractText(contents));
                }
                case Message.UserMessage(var contents, var ts2) -> {
                    msgNode.put("role", "user");
                    msgNode.put("content", MessageContent.extractText(contents));
                }
                case Message.AssistantMessage(var contents, var a1, var a2, var a3, var a4, var a5, var a6, var a7, var a8, var a9) -> {
                    msgNode.put("role", "assistant");
                    msgNode.put("content", MessageContent.extractText(contents));
                }
                case Message.ToolResultMessage(var toolCallId, var tn, var contents, var ie, var ts3) -> {
                    msgNode.put("role", "tool");
                    msgNode.put("content", MessageContent.extractText(contents));
                }
            }
            messages.add(msgNode);
        }
        root.set("messages", messages);

        // Ollama 支持 tools（如果模型支持）
        if (request.tools() != null && !request.tools().isEmpty()) {
            var toolsArray = MAPPER.createArrayNode();
            for (var td : request.tools()) {
                var toolNode = MAPPER.createObjectNode();
                toolNode.put("type", "function");
                var fn = MAPPER.createObjectNode();
                fn.put("name", td.name());
                fn.put("description", td.description());
                fn.set("parameters", MAPPER.readTree("{}"));
                toolNode.set("function", fn);
                toolsArray.add(toolNode);
            }
            root.set("tools", toolsArray);
        }

        return MAPPER.writeValueAsString(root);
    }

    private LlmResponse parseResponse(JsonNode root) {
        var message = root.path("message");
        var contentText = message.path("content").asText("");

        var contents = new java.util.ArrayList<MessageContent>();
        if (!contentText.isEmpty()) {
            contents.add(new MessageContent.TextContent(contentText));
        }

        // Ollama tool_calls 格式
        var tcArray = message.path("tool_calls");
        if (tcArray.isArray()) {
            for (var tc : tcArray) {
                contents.add(new MessageContent.ToolCallContent(
                    tc.path("id").asText("call_0"),
                    tc.path("function").path("name").asText(),
                    tc.path("function").path("arguments").asText("{}")
                ));
            }
        }

        var evalCount = root.path("eval_count").asInt(0);
        var promptEvalCount = root.path("prompt_eval_count").asInt(0);
        var usageInfo = new UsageInfo(promptEvalCount, evalCount, promptEvalCount + evalCount, null, null);

        return new LlmResponse(
            contents, usageInfo,
            root.path("done").asBoolean(true) ? "stop" : "toolUse",
            null, "openai-completions", "ollama", "", null, null,
            Map.of()
        );
    }
}
