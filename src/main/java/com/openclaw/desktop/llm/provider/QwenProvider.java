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
 * 通义千问 LLM Provider — 兼容 OpenAI API 格式。
 * 支持 qwen-turbo, qwen-plus, qwen-max, qwen-long 等模型。
 * 对应 OpenClaw 的 alibaba extension。
 *
 * DashScope 兼容模式 API: https://dashscope.aliyuncs.com/compatible-mode/v1
 */
public class QwenProvider implements LlmProvider {

    private static final Logger log = LoggerFactory.getLogger(QwenProvider.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String DEFAULT_BASE_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1";

    private final String apiKey;
    private final String baseUrl;
    private final HttpClient httpClient;

    public QwenProvider(String apiKey) {
        this(apiKey, DEFAULT_BASE_URL);
    }

    public QwenProvider(String apiKey, String baseUrl) {
        this.apiKey = apiKey;
        this.baseUrl = baseUrl != null ? baseUrl : DEFAULT_BASE_URL;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();
    }

    @Override
    public String id() { return "qwen"; }

    @Override
    public String name() { return "通义千问 (Qwen)"; }

    @Override
    public Mono<LlmResponse> chat(LlmRequest request) {
        return Mono.fromCallable(() -> {
            var body = buildRequestBody(request, false);
            var httpRequest = buildHttpRequest(body);
            var response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new RuntimeException("Qwen API error: " + response.statusCode() + " - " + response.body());
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
                    sink.error(new RuntimeException("Qwen API error: " + response.statusCode()));
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
                            log.warn("Failed to parse Qwen SSE chunk: {}", e.getMessage());
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
        // 千问模型列表（DashScope 不提供 list models API，硬编码常用模型）
        return Flux.just(
            Model.of("qwen-max", "Qwen Max (旗舰)", "qwen"),
            Model.of("qwen-plus", "Qwen Plus (均衡)", "qwen"),
            Model.of("qwen-turbo", "Qwen Turbo (快速)", "qwen"),
            Model.of("qwen-long", "Qwen Long (长文本)", "qwen"),
            Model.of("qwen2.5-72b-instruct", "Qwen2.5-72B", "qwen"),
            Model.of("qwen2.5-7b-instruct", "Qwen2.5-7B", "qwen"),
            Model.of("qwen2-72b-instruct", "Qwen2-72B", "qwen"),
            Model.of("qwen2-7b-instruct", "Qwen2-7B", "qwen"),
            Model.of("qwen-vl-max", "Qwen VL Max (视觉)", "qwen"),
            Model.of("qwen-coder-32b", "Qwen Coder 32B", "qwen")
        );
    }

    @Override
    public Mono<Boolean> healthCheck() {
        return Mono.just(apiKey != null && !apiKey.isBlank());
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
                    // 如果 assistant 消息中有 tool_calls，必须带上
                    var toolCalls = MessageContent.extractToolCalls(contents);
                    if (toolCalls != null && !toolCalls.isEmpty()) {
                        var tcArray = MAPPER.createArrayNode();
                        for (var tc : toolCalls) {
                            var tcNode = MAPPER.createObjectNode();
                            tcNode.put("id", tc.id());
                            tcNode.put("type", "function");
                            var fnNode = MAPPER.createObjectNode();
                            fnNode.put("name", tc.name());
                            fnNode.put("arguments", tc.arguments());
                            tcNode.set("function", fnNode);
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
                // 构建完整的 JSON Schema
                var schemaObj = MAPPER.createObjectNode();
                schemaObj.put("type", "object");
                if (td.inputSchema() != null && td.inputSchema().value() != null) {
                    schemaObj.set("properties", MAPPER.valueToTree(td.inputSchema().value()));
                    // 提取 required 字段
                    var required = MAPPER.createArrayNode();
                    for (var key : td.inputSchema().value().keySet()) {
                        var val = td.inputSchema().value().get(key);
                        if (val instanceof Map m && m.containsKey("type")) {
                            required.add(key);
                        }
                    }
                    if (!required.isEmpty()) schemaObj.set("required", required);
                } else {
                    schemaObj.set("properties", MAPPER.createObjectNode());
                }
                fn.set("parameters", schemaObj);
                toolNode.set("function", fn);
                toolsArray.add(toolNode);
            }
            root.set("tools", toolsArray);
        }

        // 千问特有参数：enable_search（联网搜索）
        var options = request.extraParams();
        if (options != null) {
            var enableSearch = options.get("enable_search");
            if (Boolean.TRUE.equals(enableSearch)) {
                root.put("enable_search", true);
            }
            var resultFormat = options.get("result_format");
            if (resultFormat != null) {
                root.put("result_format", resultFormat.toString());
            }
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
            null, "openai-completions", "qwen", "", null, null,
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

        // 千问 reasoning_content（qwen-reasoner 等模型）
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
