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
 * DeepSeek LLM Provider — 兼容 OpenAI API 格式。
 * 支持 deepseek-chat, deepseek-reasoner 等模型。
 */
public class DeepSeekProvider implements LlmProvider {

    private static final Logger log = LoggerFactory.getLogger(DeepSeekProvider.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String DEFAULT_BASE_URL = "https://api.deepseek.com/v1";

    private final String apiKey;
    private final String baseUrl;
    private final HttpClient httpClient;

    public DeepSeekProvider(String apiKey) {
        this(apiKey, DEFAULT_BASE_URL);
    }

    public DeepSeekProvider(String apiKey, String baseUrl) {
        this.apiKey = apiKey;
        this.baseUrl = baseUrl != null ? baseUrl : DEFAULT_BASE_URL;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();
    }

    @Override
    public String id() { return "deepseek"; }

    @Override
    public String name() { return "DeepSeek"; }

    @Override
    public Mono<LlmResponse> chat(LlmRequest request) {
        return Mono.fromCallable(() -> {
            var body = buildRequestBody(request, false);
            var httpRequest = buildHttpRequest(body);
            var response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new RuntimeException("DeepSeek API error: " + response.statusCode() + " - " + response.body());
            }
            return parseResponse(MAPPER.readTree(response.body()));
        });
    }

    @Override
    public Flux<LlmEvent> chatStream(LlmRequest request) {
        // DeepSeek 兼容 OpenAI SSE 格式，复用 OpenAiProvider 的流式解析逻辑
        return Flux.<LlmEvent>create(sink -> {
            try {
                var body = buildRequestBody(request, true);
                var httpRequest = buildHttpRequest(body);
                var response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofLines());

                if (response.statusCode() != 200) {
                    sink.error(new RuntimeException("DeepSeek API error: " + response.statusCode()));
                    return;
                }

                response.body()
                    .filter(line -> line.startsWith("data: "))
                    .map(line -> line.substring(6).trim())
                    .takeWhile(line -> !line.equals("[DONE]"))
                    .forEach(line -> {
                        try {
                            var chunk = MAPPER.readTree(line);
                            parseChunk(chunk, sink);
                        } catch (Exception e) {
                            log.warn("Failed to parse SSE chunk: {}", e.getMessage());
                        }
                    });

                sink.complete();
                sink.complete();
            } catch (Exception e) {
                sink.error(e);
            }
        });
    }

    @Override
    public Flux<Model> listModels() {
        return Flux.just(
            Model.of("deepseek-chat", "DeepSeek Chat", "deepseek"),
            Model.of("deepseek-reasoner", "DeepSeek Reasoner", "deepseek")
        );
    }

    @Override
    public Mono<Boolean> healthCheck() {
        return Mono.just(true);
    }

    // ---- internal ----

    private HttpRequest buildHttpRequest(String body) {
        return HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + "/chat/completions"))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer " + apiKey)
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .timeout(Duration.ofMinutes(5))
            .build();
    }

    private String buildRequestBody(LlmRequest request, boolean stream) throws Exception {
        var root = MAPPER.createObjectNode();
        root.put("model", request.modelId());
        root.put("stream", stream);
        if (request.temperature() != null) root.put("temperature", request.temperature());
        if (request.maxTokens() != null) root.put("max_tokens", request.maxTokens());

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
                    msgNode.put("tool_call_id", toolCallId);
                    msgNode.put("content", MessageContent.extractText(contents));
                }
            }
            messages.add(msgNode);
        }
        root.set("messages", messages);

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
        var choice = root.path("choices").path(0);
        var message = choice.path("message");
        var contentText = message.path("content").asText("");

        var contents = new java.util.ArrayList<MessageContent>();
        if (!contentText.isEmpty()) {
            contents.add(new MessageContent.TextContent(contentText));
        }

        var tcArray = message.path("tool_calls");
        if (tcArray.isArray()) {
            for (var tc : tcArray) {
                contents.add(new MessageContent.ToolCallContent(
                    tc.path("id").asText(),
                    tc.path("function").path("name").asText(),
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

        return new LlmResponse(
            contents, usageInfo,
            choice.path("finish_reason").asText("stop"),
            null, "openai-completions", "deepseek", "", null, null,
            Map.of()
        );
    }

    private void parseChunk(JsonNode chunk, reactor.core.publisher.FluxSink<LlmEvent> sink) {
        var choices = chunk.path("choices");
        if (!choices.isArray() || choices.isEmpty()) return;
        var delta = choices.get(0).path("delta");

        if (delta.has("content")) {
            var text = delta.path("content").asText("");
            if (!text.isEmpty()) {
                sink.next(new LlmEvent.TextDelta(0, text));
            }
        }

        // reasoning content (deepseek-reasoner)
        if (delta.has("reasoning_content")) {
            var reasoning = delta.path("reasoning_content").asText("");
            if (!reasoning.isEmpty()) {
                sink.next(new LlmEvent.ThinkingDelta(0, reasoning));
            }
        }

        if (chunk.has("usage")) {
            var u = chunk.path("usage");
            sink.next(new LlmEvent.Usage(new UsageInfo(
                u.path("prompt_tokens").asInt(0),
                u.path("completion_tokens").asInt(0),
                u.path("total_tokens").asInt(0),
                null, null
            )));
        }
    }
}
