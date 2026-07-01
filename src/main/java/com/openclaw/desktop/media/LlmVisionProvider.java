package com.openclaw.desktop.media;

import com.openclaw.desktop.llm.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.*;

/**
 * 多模态 LLM 视觉引擎 — 通过 OpenAI-compatible Vision API 分析图片。
 * 对应 OpenClaw 的 media-understanding-core。
 *
 * 支持：
 * - GPT-4o / GPT-4o-mini (OpenAI)
 * - Claude 3.5 Sonnet (Anthropic)
 * - Gemini Pro Vision (Google)
 * - Qwen-VL (通义千问)
 *
 * 工作方式：将图片编码为 base64 → 作为 user message 的 image_url → 发送给 LLM
 */
public class LlmVisionProvider implements VisionProvider {

    private static final Logger log = LoggerFactory.getLogger(LlmVisionProvider.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final long MAX_IMAGE_BYTES = 20 * 1024 * 1024; // 20MB

    private final String apiKey;
    private final String baseUrl;
    private final String model;
    private final HttpClient httpClient;

    public LlmVisionProvider(String apiKey, String baseUrl, String model) {
        this.apiKey = apiKey;
        this.baseUrl = baseUrl != null ? baseUrl : "https://api.openai.com/v1";
        this.model = model != null ? model : "gpt-4o";
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build();
    }

    @Override public String id() { return "llm-vision"; }
    @Override public String name() { return "LLM Vision (" + model + ")"; }
    @Override public boolean isAvailable() { return apiKey != null && !apiKey.isBlank(); }

    @Override
    public Mono<String> analyzeImage(String imagePath, String prompt) {
        if (!isAvailable()) {
            return Mono.error(new IllegalStateException("Vision API key not configured"));
        }
        return Mono.fromCallable(() -> {
            var imageData = loadImage(imagePath);
            var mediaType = guessMediaType(imagePath);
            var base64 = Base64.getEncoder().encodeToString(imageData);
            var dataUrl = "data:" + mediaType + ";base64," + base64;

            var body = buildVisionRequest(prompt, List.of(dataUrl));
            var response = callApi(body);
            return extractContent(response);
        });
    }

    @Override
    public Mono<List<String>> analyzeImages(List<String> imagePaths, String prompt) {
        if (!isAvailable()) {
            return Mono.error(new IllegalStateException("Vision API key not configured"));
        }
        return Mono.fromCallable(() -> {
            var dataUrls = new ArrayList<String>();
            for (var path : imagePaths) {
                var data = loadImage(path);
                var mediaType = guessMediaType(path);
                dataUrls.add("data:" + mediaType + ";base64," + Base64.getEncoder().encodeToString(data));
            }
            var body = buildVisionRequest(prompt, dataUrls);
            var response = callApi(body);
            return List.of(extractContent(response));
        });
    }

    // ---- internal ----

    private String buildVisionRequest(String prompt, List<String> dataUrls) throws Exception {
        var root = MAPPER.createObjectNode();
        root.put("model", model);
        root.put("max_tokens", 1000);

        var messages = root.putArray("messages");

        // user message with text + images
        var userMsg = messages.addObject();
        userMsg.put("role", "user");
        var content = userMsg.putArray("content");

        // 文本部分
        var textPart = content.addObject();
        textPart.put("type", "text");
        textPart.put("text", prompt);

        // 图片部分
        for (var url : dataUrls) {
            var imagePart = content.addObject();
            imagePart.put("type", "image_url");
            var imageUrl = imagePart.putObject("image_url");
            imageUrl.put("url", url);
        }

        return MAPPER.writeValueAsString(root);
    }

    private String callApi(String body) throws Exception {
        var request = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + "/chat/completions"))
            .header("Authorization", "Bearer " + apiKey)
            .header("Content-Type", "application/json")
            .timeout(Duration.ofSeconds(60))
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();

        var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new RuntimeException("Vision API error " + response.statusCode() + ": " + response.body());
        }
        return response.body();
    }

    private String extractContent(String json) throws Exception {
        var root = MAPPER.readTree(json);
        return root.path("choices").path(0).path("message").path("content").asText("");
    }

    private byte[] loadImage(String path) throws Exception {
        if (path.startsWith("http://") || path.startsWith("https://")) {
            var uri = URI.create(path);
            var req = HttpRequest.newBuilder().uri(uri).timeout(Duration.ofSeconds(30)).GET().build();
            var resp = httpClient.send(req, HttpResponse.BodyHandlers.ofByteArray());
            return resp.body();
        } else {
            var file = Paths.get(path);
            if (!Files.exists(file)) throw new IllegalArgumentException("Image not found: " + path);
            if (Files.size(file) > MAX_IMAGE_BYTES) throw new IllegalArgumentException("Image too large (>20MB)");
            return Files.readAllBytes(file);
        }
    }

    private String guessMediaType(String filename) {
        var ext = filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
        return switch (ext) {
            case "png" -> "image/png";
            case "gif" -> "image/gif";
            case "webp" -> "image/webp";
            case "bmp" -> "image/bmp";
            default -> "image/jpeg";
        };
    }
}
