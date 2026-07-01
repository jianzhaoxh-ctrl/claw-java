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
 * 视频生成工具 — 调用 Runway / Pika / Sora 等 API 生成视频。
 * 对应 OpenClaw 的 video-generation extension。
 */
public class VideoGenerationTool implements Tool {

    private static final Logger log = LoggerFactory.getLogger(VideoGenerationTool.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String apiKey;
    private final String provider;
    private final String baseUrl;

    public VideoGenerationTool() {
        this(null, "runway", null);
    }

    public VideoGenerationTool(String apiKey, String provider, String baseUrl) {
        this.apiKey = apiKey;
        this.provider = provider != null ? provider : "runway";
        this.baseUrl = baseUrl != null ? baseUrl : "https://api.runwayml.com/v1";
    }

    @Override
    public ToolDescriptor descriptor() {
        var inputSchema = new JsonObject(Map.of(
            "prompt", Map.of("type", "string", "description", "Video generation prompt"),
            "duration", Map.of("type", "number", "description", "Video duration in seconds (1-10)"),
            "aspectRatio", Map.of("type", "string", "enum", "16:9,9:16,1:1", "description", "Aspect ratio"),
            "imageUrl", Map.of("type", "string", "description", "Optional reference image URL")
        ));
        return new ToolDescriptor(
            "video_generation",
            "Video Generation",
            "Generate videos from text prompts or reference images. Returns video URL when ready (async).",
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
                    "No API Key configured for video generation. Set RUNWAY_API_KEY or provider key.");
            }

            var duration = args.path("duration").asInt(4);
            var aspectRatio = args.path("aspectRatio").asText("16:9");
            var imageUrl = args.path("imageUrl").asText("");

            var body = MAPPER.createObjectNode();
            body.put("promptText", prompt);
            body.put("duration", Math.min(Math.max(duration, 1), 10));
            body.put("ratio", aspectRatio);
            if (!imageUrl.isEmpty()) {
                body.put("promptImage", imageUrl);
            }

            var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build();
            var request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/image_to_video"))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .header("X-Runway-Version", "2024-11-06")
                .timeout(Duration.ofSeconds(30))
                .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(body)))
                .build();

            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200 && response.statusCode() != 201) {
                return ToolResult.failure(input.toolCallId(),
                    "Video API error " + response.statusCode() + ": " + response.body());
            }

            var result = MAPPER.readTree(response.body());
            var taskId = result.path("id").asText("");
            var status = result.path("status").asText("queued");

            return ToolResult.success(input.toolCallId(),
                "🎬 Video generation started.\n" +
                "Task ID: " + taskId + "\n" +
                "Status: " + status + "\n" +
                "Prompt: " + prompt + "\n" +
                "Duration: " + duration + "s | Ratio: " + aspectRatio + "\n\n" +
                "Video generation is asynchronous. Use task ID to check status:\n" +
                "video_generation_status(taskId=\"" + taskId + "\")");
        });
    }
}
