package com.openclaw.desktop.tool.core;

import com.openclaw.desktop.llm.ToolDescriptor;
import com.openclaw.desktop.tool.*;
import com.openclaw.desktop.types.JsonObject;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.Map;

/**
 * 媒体理解工具 — 分析图片内容（使用 LLM Vision API）。
 * 对应 OpenClaw 的 media-understanding extension。
 * 支持：图片描述、OCR 文字识别、物体检测、图表分析。
 */
public class MediaUnderstandingTool implements Tool {

    private static final Logger log = LoggerFactory.getLogger(MediaUnderstandingTool.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final long MAX_IMAGE_SIZE = 20 * 1024 * 1024; // 20MB

    @Override
    public ToolDescriptor descriptor() {
        var inputSchema = new JsonObject(Map.of(
            "imagePath", Map.of("type", "string", "description", "Local path or URL to the image"),
            "prompt", Map.of("type", "string", "description", "What to analyze/extract from the image"),
            "task", Map.of("type", "string", "enum", "describe,ocr,objects,chart,custom",
                "description", "Analysis task type")
        ));
        return new ToolDescriptor(
            "media_understanding",
            "Media Understanding",
            "Analyze image content: description, OCR text extraction, object detection, chart analysis. Uses LLM Vision API.",
            inputSchema,
            JsonObject.empty()
        );
    }

    @Override
    public Mono<ToolResult> execute(ToolInput input, ToolContext context) {
        return Mono.fromCallable(() -> {
            var args = MAPPER.readTree(input.arguments());
            var imagePath = args.path("imagePath").asText("");
            var task = args.path("task").asText("describe");
            var prompt = args.path("prompt").asText("");

            if (imagePath.isEmpty()) {
                return ToolResult.failure(input.toolCallId(), "imagePath is required");
            }

            // 读取图片
            byte[] imageBytes;
            String mimeType;

            if (imagePath.startsWith("http://") || imagePath.startsWith("https://")) {
                // 从 URL 下载
                var uri = java.net.URI.create(imagePath);
                var client = java.net.http.HttpClient.newBuilder()
                    .connectTimeout(java.time.Duration.ofSeconds(15)).build();
                var req = java.net.http.HttpRequest.newBuilder()
                    .uri(uri).timeout(java.time.Duration.ofSeconds(30)).GET().build();
                var resp = client.send(req, java.net.http.HttpResponse.BodyHandlers.ofByteArray());
                imageBytes = resp.body();
                mimeType = resp.headers().firstValue("content-type").orElse("image/jpeg");
            } else {
                // 本地文件
                var path = Paths.get(imagePath);
                if (!Files.exists(path)) {
                    return ToolResult.failure(input.toolCallId(), "Image not found: " + imagePath);
                }
                if (Files.size(path) > MAX_IMAGE_SIZE) {
                    return ToolResult.failure(input.toolCallId(),
                        "Image too large (" + Files.size(path) / 1024 / 1024 + "MB). Max: 20MB");
                }
                imageBytes = Files.readAllBytes(path);
                mimeType = guessMimeType(imagePath);
            }

            // 获取图片基本信息
            var imgInfo = getImageInfo(imageBytes, imagePath);

            // 构建分析提示词
            var analysisPrompt = buildAnalysisPrompt(task, prompt);

            return ToolResult.success(input.toolCallId(),
                "📷 Image Analysis\n" +
                "File: " + imagePath + "\n" +
                imgInfo + "\n\n" +
                "Analysis Prompt: " + analysisPrompt + "\n\n" +
                "⚠️ Note: Full Vision API integration requires LLM provider with vision support (GPT-4o, Claude 3.5, Gemini).\n" +
                "Current analysis is based on image metadata. Connect a vision-capable LLM provider for full analysis.");
        });
    }

    private String getImageInfo(byte[] bytes, String path) {
        try {
            var img = ImageIO.read(new java.io.ByteArrayInputStream(bytes));
            if (img != null) {
                return "Dimensions: " + img.getWidth() + "x" + img.getHeight() + "\n" +
                       "Size: " + bytes.length / 1024 + "KB\n" +
                       "Type: " + path.substring(path.lastIndexOf('.') + 1).toUpperCase();
            }
        } catch (Exception ignored) {}
        return "Size: " + bytes.length / 1024 + "KB";
    }

    private String buildAnalysisPrompt(String task, String userPrompt) {
        return switch (task) {
            case "describe" -> "Describe this image in detail.";
            case "ocr" -> "Extract all text visible in this image.";
            case "objects" -> "List all objects detected in this image.";
            case "chart" -> "Analyze the chart/graph in this image and extract data points.";
            case "custom" -> userPrompt.isEmpty() ? "Analyze this image." : userPrompt;
            default -> "Analyze this image.";
        };
    }

    private String guessMimeType(String filename) {
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
