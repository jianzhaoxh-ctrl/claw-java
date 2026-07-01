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
 * OpenAI-compatible API 辅助类 — Groq/Mistral/DeepSeek 等都使用 OpenAI 格式。
 * 提供统一的 chat/stream/healthCheck 实现。
 *
 * <p>这个类避免重复代码，所有 OpenAI-compatible Provider 只需传入 baseUrl 和 apiKey。
 */
public class OpenAiCompatibleHelper {
    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleHelper.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * 同步聊天。
     */
    public static Mono<LlmResponse> chat(HttpClient httpClient, String apiKey, String baseUrl, LlmRequest request) {
        return Mono.fromCallable(() -> {
            var body = buildRequestBody(request, false);
            var httpRequest = buildHttpRequest(baseUrl, apiKey, body);
            var response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new RuntimeException("API error " + response.statusCode() + ": " + response.body());
            }
            return parseResponse(MAPPER.readTree(response.body()));
        });
    }

    /**
     * 流式聊天。
     */
    public static Flux<LlmEvent> chatStream(HttpClient httpClient, String apiKey, String baseUrl, LlmRequest request) {
        return Flux.<LlmEvent>create(sink -> {
            try {
                var body = buildRequestBody(request, true);
                var httpRequest = buildHttpRequest(baseUrl, apiKey, body);
                var response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofLines());
                if (response.statusCode() != 200) {
                    sink.error(new RuntimeException("API error " + response.statusCode()));
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
                            log.warn("Failed to parse SSE chunk: {}", e.getMessage());
                        }
                    });
                sink.next(new LlmEvent.TurnEnd(partialMsg, List.of()));
                sink.complete();
            } catch (Exception e) {
                sink.error(e);
            }
        });
    }

    /**
     * 健康检查。
     */
    public static Mono<Boolean> healthCheck(HttpClient httpClient, String apiKey, String baseUrl) {
        return Mono.fromCallable(() -> {
            var request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/models"))
                .header("Authorization", "Bearer " + apiKey)
                .GET()
                .timeout(Duration.ofSeconds(5))
                .build();
            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200;
        });
    }

    /**
     * 列出模型。
     */
    public static Flux<Model> listModels(HttpClient httpClient, String apiKey, String baseUrl, String providerId) {
        return Mono.fromCallable(() -> {
            var request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/models"))
                .header("Authorization", "Bearer " + apiKey)
                .GET()
                .timeout(Duration.ofSeconds(10))
                .build();
            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) return List.<Model>of();
            var root = MAPPER.readTree(response.body());
            var data = root.path("data");
            var models = new java.util.ArrayList<Model>();
            if (data.isArray()) {
                for (var m : data) {
                    var id = m.path("id").asText("");
                    if (!id.isEmpty()) {
                        models.add(Model.of(id, m.path("id").asText(id), providerId));
                    }
                }
            }
            return models;
        }).flatMapMany(Flux::fromIterable);
    }

    // ========== 请求构建 ==========

    private static String buildRequestBody(LlmRequest request, boolean stream) {
        var root = MAPPER.createObjectNode();
        root.put("model", request.modelId());
        root.put("stream", stream);
        if (request.temperature() != null) root.put("temperature", request.temperature());
        if (request.maxTokens() != null) root.put("max_tokens", request.maxTokens());

        var messages = MAPPER.createArrayNode();
        for (var msg : request.messages()) {
            var msgNode = MAPPER.createObjectNode();
            switch (msg) {
                case Message.SystemMessage m      -> { msgNode.put("role", "system"); msgNode.put("content", MessageContent.extractText(m.content())); }
                case Message.UserMessage m       -> { msgNode.put("role", "user"); msgNode.put("content", MessageContent.extractText(m.content())); }
                case Message.AssistantMessage m  -> { msgNode.put("role", "assistant"); msgNode.put("content", m.text()); }
                case Message.ToolResultMessage m -> { msgNode.put("role", "tool"); msgNode.put("tool_call_id", m.toolCallId()); msgNode.put("content", MessageContent.extractText(m.content())); }
            }
            messages.add(msgNode);
        }
        root.set("messages", messages);

        if (request.tools() != null && !request.tools().isEmpty()) {
            var toolsArray = root.putArray("tools");
            for (var tool : request.tools()) {
                var t = toolsArray.addObject();
                t.put("type", "function");
                var func = t.putObject("function");
                func.put("name", tool.name());
                func.put("description", tool.description());
                func.putObject("parameters").put("type", "object");
            }
        }

        try { return MAPPER.writeValueAsString(root); }
        catch (Exception e) { throw new RuntimeException(e); }
    }

    private static HttpRequest buildHttpRequest(String baseUrl, String apiKey, String body) {
        return HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + "/chat/completions"))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer " + apiKey)
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .timeout(Duration.ofSeconds(120))
            .build();
    }

    // ========== 响应解析 ==========

    private static LlmResponse parseResponse(JsonNode root) {
        var choice = root.path("choices").path(0);
        var message = choice.path("message");
        var content = message.path("content").asText("");

        var toolCalls = new ArrayList<ToolCall>();
        var tcArray = message.path("tool_calls");
        if (tcArray.isArray()) {
            int idx = 0;
            for (var tc : tcArray) {
                toolCalls.add(new ToolCall(
                    tc.path("id").asText(),
                    tc.path("function").path("name").asText(),
                    idx++,
                    tc.path("function").path("arguments").asText("{}")
                ));
            }
        }

        var usage = root.path("usage");
        var usageInfo = new UsageInfo(
            usage.path("prompt_tokens").asInt(0),
            usage.path("completion_tokens").asInt(0),
            usage.path("total_tokens").asInt(0),
            null, null
        );

        return new LlmResponse(content, toolCalls, usageInfo,
            choice.path("finish_reason").asText("stop"), Map.of());
    }

    private static void parseChunk(JsonNode chunk, FluxSink<LlmEvent> sink, Message.AssistantMessage partialMsg) {
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

        var usage = chunk.path("usage");
        if (!usage.isMissingNode()) {
            sink.next(new LlmEvent.Usage(new UsageInfo(
                usage.path("prompt_tokens").asInt(0),
                usage.path("completion_tokens").asInt(0),
                usage.path("total_tokens").asInt(0),
                null, null
            )));
        }
    }
}
