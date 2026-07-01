package com.openclaw.desktop.llm.provider;

import com.openclaw.desktop.llm.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
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
 * Anthropic LLM Provider — 支持 Claude 3.5 Sonnet, Claude 3 Opus 等。
 * 对应 OpenClaw 的 anthropic extension。
 */
public class AnthropicProvider implements LlmProvider {

    private static final Logger log = LoggerFactory.getLogger(AnthropicProvider.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String DEFAULT_BASE_URL = "https://api.anthropic.com";
    private static final String API_VERSION = "2023-06-01";

    private final String apiKey;
    private final String baseUrl;
    private final HttpClient httpClient;

    public AnthropicProvider(String apiKey) {
        this(apiKey, DEFAULT_BASE_URL);
    }

    public AnthropicProvider(String apiKey, String baseUrl) {
        this.apiKey = apiKey;
        this.baseUrl = baseUrl != null ? baseUrl : DEFAULT_BASE_URL;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();
    }

    @Override
    public String id() { return "anthropic"; }

    @Override
    public String name() { return "Anthropic"; }

    @Override
    public Mono<LlmResponse> chat(LlmRequest request) {
        return Mono.fromCallable(() -> {
            var body = buildRequestBody(request, false);
            var httpRequest = buildHttpRequest(body);
            var response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.error("Anthropic API error: {} - {}", response.statusCode(), response.body());
                throw new RuntimeException("Anthropic API error: " + response.statusCode());
            }
            return parseResponse(MAPPER.readTree(response.body()));
        });
    }

    @Override
    public Flux<LlmEvent> chatStream(LlmRequest request) {
        return Flux.<LlmEvent>create(sink -> {
            try {
                var body = buildRequestBody(request, true);
                var httpRequest = buildHttpRequest(body);
                var response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofLines());

                if (response.statusCode() != 200) {
                    sink.error(new RuntimeException("Anthropic API error: " + response.statusCode()));
                    return;
                }

                response.body()
                    .filter(line -> line.startsWith("data: "))
                    .map(line -> line.substring(6).trim())
                    .forEach(line -> {
                        try {
                            var event = MAPPER.readTree(line);
                            parseStreamEvent(event, sink);
                        } catch (Exception e) {
                            log.warn("Failed to parse SSE: {}", e.getMessage());
                        }
                    });

                sink.complete();
            } catch (Exception e) {
                sink.error(e);
            }
        });
    }

    @Override
    public Flux<Model> listModels() {
        return Mono.fromCallable(() -> {
            var request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/v1/models"))
                .header("x-api-key", apiKey)
                .header("anthropic-version", API_VERSION)
                .GET()
                .build();
            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) return List.<Model>of();
            var root = MAPPER.readTree(response.body());
            var data = root.path("data");
            var models = new ArrayList<Model>();
            if (data.isArray()) {
                for (var node : data) {
                    var id = node.path("id").asText();
                    models.add(Model.of(id, id, "anthropic"));
                }
            }
            return models;
        }).flatMapMany(Flux::fromIterable);
    }

    @Override
    public Mono<Boolean> healthCheck() {
        return listModels().hasElements();
    }

    // ---- internal ----

    private HttpRequest buildHttpRequest(String body) {
        return HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + "/v1/messages"))
            .header("Content-Type", "application/json")
            .header("x-api-key", apiKey)
            .header("anthropic-version", API_VERSION)
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .timeout(Duration.ofMinutes(5))
            .build();
    }

    private String buildRequestBody(LlmRequest request, boolean stream) throws Exception {
        var root = MAPPER.createObjectNode();
        root.put("model", request.modelId());
        root.put("stream", stream);
        root.put("max_tokens", request.maxTokens() != null ? request.maxTokens() : 4096);

        if (request.temperature() != null) root.put("temperature", request.temperature());

        // 分离 system message
        var systemContent = new StringBuilder();
        var messages = MAPPER.createArrayNode();
        for (var msg : request.messages()) {
            switch (msg) {
                case Message.SystemMessage(var contents, var ts) -> systemContent.append(MessageContent.extractText(contents)).append("\n");
                case Message.UserMessage(var contents, var ts) -> {
                    var m = MAPPER.createObjectNode();
                    m.put("role", "user");
                    m.put("content", MessageContent.extractText(contents));
                    messages.add(m);
                }
                case Message.AssistantMessage(var contents, var a1, var a2, var a3, var a4, var a5, var a6, var a7, var a8, var a9) -> {
                    var m = MAPPER.createObjectNode();
                    m.put("role", "assistant");
                    m.put("content", MessageContent.extractText(contents));
                    messages.add(m);
                }
                case Message.ToolResultMessage(var toolCallId, var tn, var contents, var ie, var ts) -> {
                    var m = MAPPER.createObjectNode();
                    m.put("role", "user");
                    var contentArr = MAPPER.createArrayNode();
                    var toolResult = MAPPER.createObjectNode();
                    toolResult.put("type", "tool_result");
                    toolResult.put("tool_use_id", toolCallId);
                    toolResult.put("content", MessageContent.extractText(contents));
                    contentArr.add(toolResult);
                    m.set("content", contentArr);
                    messages.add(m);
                }
            }
        }

        if (systemContent.length() > 0) {
            root.put("system", systemContent.toString().trim());
        }
        root.set("messages", messages);

        // tools
        if (request.tools() != null && !request.tools().isEmpty()) {
            var toolsArray = MAPPER.createArrayNode();
            for (var td : request.tools()) {
                var toolNode = MAPPER.createObjectNode();
                toolNode.put("name", td.name());
                toolNode.put("description", td.description());
                toolNode.put("input_schema", MAPPER.readTree("{}"));
                toolsArray.add(toolNode);
            }
            root.set("tools", toolsArray);
        }

        return MAPPER.writeValueAsString(root);
    }

    private LlmResponse parseResponse(JsonNode root) {
        var contentArray = root.path("content");
        var contents = new java.util.ArrayList<MessageContent>();

        if (contentArray.isArray()) {
            for (var block : contentArray) {
                var type = block.path("type").asText();
                if ("text".equals(type)) {
                    contents.add(new MessageContent.TextContent(block.path("text").asText("")));
                } else if ("tool_use".equals(type)) {
                    contents.add(new MessageContent.ToolCallContent(
                        block.path("id").asText(),
                        block.path("name").asText(),
                        block.path("input").toString()
                    ));
                }
            }
        }

        var usage = root.path("usage");
        var usageInfo = new UsageInfo(
            usage.path("input_tokens").asInt(0),
            usage.path("output_tokens").asInt(0),
            usage.path("input_tokens").asInt(0) + usage.path("output_tokens").asInt(0),
            null, null
        );

        return new LlmResponse(
            contents, usageInfo,
            root.path("stop_reason").asText("end_turn"),
            null, "anthropic-messages", "anthropic", "", null, null,
            Map.of()
        );
    }

    private void parseStreamEvent(JsonNode event, reactor.core.publisher.FluxSink<LlmEvent> sink) {
        var type = event.path("type").asText();
        switch (type) {
            case "content_block_delta" -> {
                var delta = event.path("delta");
                var deltaType = delta.path("type").asText();
                if ("text_delta".equals(deltaType)) {
                    sink.next(new LlmEvent.TextDelta(0, delta.path("text").asText("")));
                }
            }
            case "content_block_start" -> {
                var block = event.path("content_block");
                if ("tool_use".equals(block.path("type").asText())) {
                    sink.next(new LlmEvent.ToolCallStart(
                        event.path("index").asInt(),
                        block.path("name").asText()
                    ));
                }
            }
            case "message_delta" -> {
                var usage = event.path("usage");
                if (usage.has("output_tokens")) {
                    sink.next(new LlmEvent.Usage(new UsageInfo(
                        0, usage.path("output_tokens").asInt(0),
                        usage.path("output_tokens").asInt(0), null, null
                    )));
                }
            }
            case "message_stop" -> {}
            default -> {}
        }
    }
}
