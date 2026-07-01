package com.openclaw.desktop.tool.core;

import com.openclaw.desktop.llm.ToolDescriptor;
import com.openclaw.desktop.tool.*;
import com.openclaw.desktop.types.JsonObject;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/**
 * 图片生成工具 — 调用 OpenAI DALL-E / Stability AI 等图片生成 API。
 * 对应 OpenClaw 的 image-generation extension。
 */
public class ImageGenerationTool implements Tool {

    private static final Logger log = LoggerFactory.getLogger(ImageGenerationTool.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String apiKey;
    private final String provider;
    private final String baseUrl;

    public ImageGenerationTool() {
        this(null, "openai", null);
    }

    public ImageGenerationTool(String apiKey, String provider, String baseUrl) {
        this.apiKey = apiKey;
        this.provider = provider != null ? provider : "openai";
        this.baseUrl = baseUrl != null ? baseUrl : switch (this.provider) {
            case "stability" -> "https://api.stability.ai/v2beta";
            default -> "https://api.openai.com/v1";
        };
    }

    @Override
    public ToolDescriptor descriptor() {
        var inputSchema = new JsonObject(Map.of(
            "prompt", Map.of("type", "string", "description", "Image generation prompt"),
            "size", Map.of("type", "string", "enum", "256x256,512x512,1024x1024,1792x1024,1024x1792", "description", "Image size"),
            "quality", Map.of("type", "string", "enum", "standard,hd", "description", "Image quality"),
            "style", Map.of("type", "string", "description", "Style preset (vivid, natural, etc.)"),
            "n", Map.of("type", "number", "description", "Number of images (1-4)")
        ));
        return new ToolDescriptor(
            "image_generation",
            "Image Generation",
            "Generate images from text prompts using DALL-E or Stability AI. Returns image URLs.",
            inputSchema,
            JsonObject.empty()
        );
    }

    @Override
    public Mono<ToolResult> execute(ToolInput input, ToolContext context) {
        return Mono.fromCallable(() -> {
            var args = MAPPER.readTree(input.arguments());
            var prompt = args.path("prompt").asText("");

            if (prompt.isEmpty()) {
                return ToolResult.failure(input.toolCallId(), "Prompt is required");
            }
            if (apiKey == null || apiKey.isBlank()) {
                return ToolResult.failure(input.toolCallId(),
                    "No API Key configured for image generation. Set OPENAI_API_KEY or STABILITY_API_KEY.");
            }

            return switch (provider) {
                case "stability" -> generateStability(args, prompt, input);
                default -> generateOpenAi(args, prompt, input);
            };
        });
    }

    private ToolResult generateOpenAi(com.fasterxml.jackson.databind.JsonNode args, String prompt, ToolInput input) throws Exception {
        var size = args.path("size").asText("1024x1024");
        var quality = args.path("quality").asText("standard");
        var style = args.path("style").asText("vivid");
        var n = args.path("n").asInt(1);

        var body = MAPPER.createObjectNode();
        body.put("model", "dall-e-3");
        body.put("prompt", prompt);
        body.put("size", size);
        body.put("quality", quality);
        body.put("style", style);
        body.put("n", Math.min(n, 1)); // DALL-E 3 only supports n=1

        var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build();
        var request = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + "/images/generations"))
            .header("Authorization", "Bearer " + apiKey)
            .header("Content-Type", "application/json")
            .timeout(Duration.ofMinutes(2))
            .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(body)))
            .build();

        var response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            return ToolResult.failure(input.toolCallId(),
                "OpenAI API error " + response.statusCode() + ": " + response.body());
        }

        var result = MAPPER.readTree(response.body());
        var images = result.path("data");
        var sb = new StringBuilder();
        sb.append("✅ Generated ").append(images.size()).append(" image(s):\n\n");
        for (int i = 0; i < images.size(); i++) {
            var img = images.get(i);
            var url = img.path("url").asText("");
            var revisedPrompt = img.path("revised_prompt").asText("");
            sb.append("Image ").append(i + 1).append(":\n");
            sb.append("  URL: ").append(url).append("\n");
            if (!revisedPrompt.isEmpty()) {
                sb.append("  Revised prompt: ").append(revisedPrompt).append("\n");
            }
            sb.append("\n");
        }
        return ToolResult.success(input.toolCallId(), sb.toString().trim());
    }

    private ToolResult generateStability(com.fasterxml.jackson.databind.JsonNode args, String prompt, ToolInput input) {
        return ToolResult.failure(input.toolCallId(), "Stability AI provider not yet implemented. Use OpenAI DALL-E.");
    }
}
