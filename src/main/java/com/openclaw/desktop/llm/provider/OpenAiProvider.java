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
 * OpenAI LLM Provider — 支持 GPT-4o, GPT-4 Turbo, GPT-3.5 Turbo 等。
 */
public class OpenAiProvider implements LlmProvider {

    private static final Logger log = LoggerFactory.getLogger(OpenAiProvider.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String DEFAULT_BASE_URL = "https://api.openai.com/v1";

    private final String apiKey;
    private final String baseUrl;
    private final HttpClient httpClient;

    public OpenAiProvider(String apiKey) {
        this(apiKey, DEFAULT_BASE_URL);
    }

    public OpenAiProvider(String apiKey, String baseUrl) {
        this.apiKey = apiKey;
        this.baseUrl = baseUrl != null ? baseUrl : DEFAULT_BASE_URL;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();
    }

    @Override
    public String id() { return "openai"; }

    @Override
    public String name() { return "OpenAI"; }

    @Override
    public Mono<LlmResponse> chat(LlmRequest request) {
        return Mono.fromCallable(() -> {
            var body = buildRequestBody(request, false);
            var httpRequest = buildHttpRequest(body);
            var response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.error("OpenAI API error: {} - {}", response.statusCode(), response.body());
                throw new RuntimeException("OpenAI API error: " + response.statusCode());
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
                    sink.error(new RuntimeException("OpenAI API error: " + response.statusCode()));
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
            } catch (Exception e) {
                sink.error(e);
            }
        });
    }

    @Override
    public Flux<Model> listModels() {
        return Mono.fromCallable(() -> {
            var request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/models"))
                .header("Authorization", "Bearer " + apiKey)
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
                    models.add(Model.of(id, id, "openai"));
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

        // messages — v2.0: 使用新 Message sealed interface
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
                case Message.AssistantMessage(
                    var contents, var api, var provider, var model, var rm, var ri, var u, var sr, var em, var ts
                ) -> {
                    msgNode.put("role", "assistant");
                    msgNode.put("content", MessageContent.extractText(contents));
                    var toolCalls = MessageContent.extractToolCalls(contents);
                    if (!toolCalls.isEmpty()) {
                        var tcArray = MAPPER.createArrayNode();
                        for (var tc : toolCalls) {
                            var tcNode = MAPPER.createObjectNode();
                            tcNode.put("id", tc.id());
                            tcNode.put("type", "function");
                            var fn = MAPPER.createObjectNode();
                            fn.put("name", tc.name());
                            fn.put("arguments", tc.arguments());
                            tcNode.set("function", fn);
                            tcArray.add(tcNode);
                        }
                        msgNode.set("tool_calls", tcArray);
                    }
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

        // tools
        if (request.tools() != null && !request.tools().isEmpty()) {
            var toolsArray = MAPPER.createArrayNode();
            for (var td : request.tools()) {
                var toolNode = MAPPER.createObjectNode();
                toolNode.put("type", "function");
                var fn = MAPPER.createObjectNode();
                fn.put("name", td.name());
                fn.put("description", td.description());
                fn.set("parameters", MAPPER.readTree("{}")); // TODO: from inputSchema
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
            int idx = 0;
            for (var tc : tcArray) {
                contents.add(new MessageContent.ToolCallContent(
                    tc.path("id").asText(),
                    tc.path("function").path("name").asText(),
                    tc.path("function").path("arguments").asText("{}")
                ));
                idx++;
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
            null, "openai-completions", "openai", "", null, null,
            Map.of()
        );
    }

    private void parseChunk(JsonNode chunk, reactor.core.publisher.FluxSink<LlmEvent> sink) {
        var choices = chunk.path("choices");
        if (!choices.isArray() || choices.isEmpty()) return;

        var delta = choices.get(0).path("delta");

        // text content
        if (delta.has("content")) {
            var text = delta.path("content").asText("");
            if (!text.isEmpty()) {
                sink.next(new LlmEvent.TextDelta(0, text));
            }
        }

        // tool calls
        var tcArray = delta.path("tool_calls");
        if (tcArray.isArray()) {
            for (var tc : tcArray) {
                var index = tc.path("index").asInt(0);
                var fn = tc.path("function");

                if (tc.has("id")) {
                    sink.next(new LlmEvent.ToolCallStart(
                        index,
                        fn.path("name").asText()
                    ));
                }

                if (fn.has("arguments")) {
                    var argsDelta = fn.path("arguments").asText("");
                    if (!argsDelta.isEmpty()) {
                        sink.next(new LlmEvent.ToolCallDelta(
                            index,
                            argsDelta
                        ));
                    }
                }
            }
        }

        // usage
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
