package com.openclaw.desktop.llm.provider;

import com.openclaw.desktop.llm.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
 * Azure OpenAI Provider — 企业版 OpenAI API。
 * 使用部署名(deploymentId)而非模型名。
 * 认证用 api-key header 而非 Bearer token。
 */
public class AzureOpenAiProvider implements LlmProvider {
    private static final Logger log = LoggerFactory.getLogger(AzureOpenAiProvider.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String API_VERSION = "2024-02-15-preview";

    private final String apiKey;
    private final String baseUrl;
    private final String deploymentId;
    private final HttpClient httpClient;

    public AzureOpenAiProvider(String apiKey, String baseUrl, String deploymentId) {
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.deploymentId = deploymentId;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();
    }

    @Override
    public String id() { return "azure-openai"; }

    @Override
    public String name() { return "Azure OpenAI"; }

    @Override
    public Mono<LlmResponse> chat(LlmRequest request) {
        return Mono.fromCallable(() -> {
            var body = buildRequestBody(request, false);
            var url = baseUrl + "/openai/deployments/" + deploymentId + "/chat/completions?api-version=" + API_VERSION;
            var httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .header("api-key", apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .timeout(Duration.ofSeconds(60))
                .build();
            var response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new RuntimeException("Azure OpenAI API error: " + response.statusCode());
            }
            return parseResponse(MAPPER.readTree(response.body()));
        });
    }

    @Override
    public Flux<LlmEvent> chatStream(LlmRequest request) {
        return Flux.<LlmEvent>create(sink -> {
            try {
                var body = buildRequestBody(request, true);
                var url = baseUrl + "/openai/deployments/" + deploymentId + "/chat/completions?api-version=" + API_VERSION;
                var httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .header("api-key", apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .timeout(Duration.ofSeconds(120))
                    .build();
                var response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofLines());
                if (response.statusCode() != 200) {
                    sink.error(new RuntimeException("Azure OpenAI API error: " + response.statusCode()));
                    return;
                }
                var partialMsg = new Message.AssistantMessage("", List.of());
                response.body()
                    .filter(line -> line.startsWith("data: "))
                    .map(line -> line.substring(6).trim())
                    .takeWhile(line -> !line.equals("[DONE]"))
                    .forEach(line -> {
                        try {
                            var chunk = MAPPER.readTree(line);
                            parseChunk(chunk, sink, partialMsg);
                        } catch (Exception e) {
                            log.warn("Failed to parse Azure OpenAI SSE chunk: {}", e.getMessage());
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
        return Flux.just(Model.of(deploymentId, "Azure " + deploymentId, "azure-openai"));
    }

    @Override
    public Mono<Boolean> healthCheck() {
        return Mono.fromCallable(() -> {
            var url = baseUrl + "/openai/deployments/" + deploymentId + "/chat/completions?api-version=" + API_VERSION;
            var request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("api-key", apiKey)
                .POST(HttpRequest.BodyPublishers.ofString("{\"model\":\"" + deploymentId + "\",\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}],\"max_tokens\":1}"))
                .timeout(Duration.ofSeconds(5))
                .build();
            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() < 500;
        });
    }

    private String buildRequestBody(LlmRequest request, boolean stream) {
        var root = MAPPER.createObjectNode();
        root.put("model", deploymentId);
        root.put("stream", stream);
        if (request.temperature() != null) root.put("temperature", request.temperature());
        if (request.maxTokens() != null) root.put("max_tokens", request.maxTokens());

        var messages = MAPPER.createArrayNode();
        for (var msg : request.messages()) {
            var msgNode = MAPPER.createObjectNode();
            switch (msg) {
                case Message.SystemMessage m     -> { msgNode.put("role", "system"); msgNode.put("content", MessageContent.extractText(m.content())); }
                case Message.UserMessage m      -> { msgNode.put("role", "user"); msgNode.put("content", MessageContent.extractText(m.content())); }
                case Message.AssistantMessage m -> { msgNode.put("role", "assistant"); msgNode.put("content", m.text()); }
                case Message.ToolResultMessage m -> { msgNode.put("role", "tool"); msgNode.put("tool_call_id", m.toolCallId()); msgNode.put("content", MessageContent.extractText(m.content())); }
            }
            messages.add(msgNode);
        }
        root.set("messages", messages);

        try { return MAPPER.writeValueAsString(root); }
        catch (Exception e) { throw new RuntimeException(e); }
    }

    private LlmResponse parseResponse(JsonNode root) {
        var choice = root.path("choices").path(0);
        var message = choice.path("message");
        var content = message.path("content").asText("");

        var toolCalls = new ArrayList<ToolCall>();
        var tcArray = message.path("tool_calls");
        if (tcArray.isArray()) {
            int idx = 0;
            for (var tc : tcArray) {
                toolCalls.add(new ToolCall(tc.path("id").asText(), tc.path("function").path("name").asText(), idx++, tc.path("function").path("arguments").asText("{}")));
            }
        }

        var usage = root.path("usage");
        var usageInfo = new UsageInfo(usage.path("prompt_tokens").asInt(0), usage.path("completion_tokens").asInt(0), usage.path("total_tokens").asInt(0), null, null);
        return new LlmResponse(content, toolCalls, usageInfo, choice.path("finish_reason").asText("stop"), Map.of());
    }

    private void parseChunk(JsonNode chunk, FluxSink<LlmEvent> sink, Message.AssistantMessage partialMsg) {
        var choices = chunk.path("choices");
        if (!choices.isArray() || choices.isEmpty()) return;
        var delta = choices.get(0).path("delta");

        if (delta.has("content")) {
            var text = delta.path("content").asText("");
            if (!text.isEmpty()) {
                sink.next(new LlmEvent.TextDelta(0, text, partialMsg));
            }
        }

        var tcArray = delta.path("tool_calls");
        if (tcArray.isArray()) {
            for (var tc : tcArray) {
                var idx = tc.path("index").asInt(0);
                if (tc.has("function")) {
                    var func = tc.path("function");
                    if (func.has("name")) {
                        sink.next(new LlmEvent.ToolCallStart(idx, func.path("name").asText(), partialMsg));
                    }
                    if (func.has("arguments")) {
                        sink.next(new LlmEvent.ToolCallDelta(idx, func.path("arguments").asText(), partialMsg));
                    }
                }
            }
        }
    }
}
