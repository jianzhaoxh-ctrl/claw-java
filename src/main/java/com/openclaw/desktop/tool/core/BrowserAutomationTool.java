package com.openclaw.desktop.tool.core;

import com.openclaw.desktop.llm.ToolDescriptor;
import com.openclaw.desktop.tool.*;
import com.openclaw.desktop.types.JsonObject;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Map;

/**
 * 浏览器自动化工具 — 打开 URL、截图、提取页面内容。
 * 对应 OpenClaw 的 browser extension。
 */
public class BrowserAutomationTool implements Tool {

    private static final Logger log = LoggerFactory.getLogger(BrowserAutomationTool.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int MAX_CONTENT_LENGTH = 50000;

    @Override
    public ToolDescriptor descriptor() {
        var inputSchema = new JsonObject(Map.of(
            "action", Map.of("type", "string", "enum", "open,screenshot,fetch", "description", "Action to perform"),
            "url", Map.of("type", "string", "description", "Target URL"),
            "selector", Map.of("type", "string", "description", "CSS selector (for future use)"),
            "outputPath", Map.of("type", "string", "description", "Save screenshot to this path")
        ));
        return new ToolDescriptor(
            "browser",
            "Browser Automation",
            "Open URLs, take screenshots, and fetch web page content. Actions: open, screenshot, fetch.",
            inputSchema,
            JsonObject.empty()
        );
    }

    @Override
    public Mono<ToolResult> execute(ToolInput input, ToolContext context) {
        return Mono.fromCallable(() -> {
            var args = MAPPER.readTree(input.arguments());
            var action = args.path("action").asText("fetch");
            var url = args.path("url").asText("");

            if (url.isEmpty()) {
                return ToolResult.failure(input.toolCallId(), "URL is required");
            }

            return switch (action) {
                case "open" -> openUrl(url, input);
                case "screenshot" -> takeScreenshot(url, args, input);
                case "fetch" -> fetchContent(url, input);
                default -> ToolResult.failure(input.toolCallId(), "Unknown action: " + action);
            };
        });
    }

    private ToolResult openUrl(String url, ToolInput input) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(url));
                return ToolResult.success(input.toolCallId(), "Opened URL in default browser: " + url);
            }
            return ToolResult.failure(input.toolCallId(), "Desktop browsing not supported on this platform");
        } catch (Exception e) {
            return ToolResult.failure(input.toolCallId(), "Failed to open URL: " + e.getMessage());
        }
    }

    private ToolResult takeScreenshot(String url, JsonNode args, ToolInput input) {
        try {
            // 使用 Java Robot 截取全屏（简化实现 — 完整版需嵌入浏览器引擎）
            var robot = new Robot();
            var screenRect = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
            var image = robot.createScreenCapture(screenRect);

            var outputPath = args.path("outputPath").asText("");
            Path targetPath;
            if (outputPath.isEmpty()) {
                targetPath = Files.createTempFile("claw-screenshot-", ".png");
            } else {
                targetPath = Paths.get(outputPath);
            }
            ImageIO_write(image, "png", targetPath.toFile());

            return ToolResult.success(input.toolCallId(),
                "Screenshot saved to: " + targetPath + "\n(屏幕截图，非页面截图。完整页面截图需要浏览器引擎。)");
        } catch (Exception e) {
            return ToolResult.failure(input.toolCallId(), "Screenshot failed: " + e.getMessage());
        }
    }

    // 避免 javax.imageio import 冲突
    private static void ImageIO_write(BufferedImage image, String format, java.io.File file) throws java.io.IOException {
        javax.imageio.ImageIO.write(image, format, file);
    }

    private ToolResult fetchContent(String url, ToolInput input) {
        try {
            var client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
            var request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "ClawDesktop/0.1 (Java 21)")
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());

            var body = response.body();
            if (body.length() > MAX_CONTENT_LENGTH) {
                body = body.substring(0, MAX_CONTENT_LENGTH) + "\n\n... [truncated, total " + body.length() + " chars]";
            }

            return ToolResult.success(input.toolCallId(),
                "URL: " + url + "\nStatus: " + response.statusCode() + "\n\n" + body);
        } catch (Exception e) {
            return ToolResult.failure(input.toolCallId(), "Fetch failed: " + e.getMessage());
        }
    }
}
