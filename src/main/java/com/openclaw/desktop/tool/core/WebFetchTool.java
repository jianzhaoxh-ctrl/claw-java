package com.openclaw.desktop.tool.core;

import com.openclaw.desktop.tool.Tool;
import com.openclaw.desktop.tool.ToolContext;
import com.openclaw.desktop.llm.ToolDescriptor;
import com.openclaw.desktop.tool.ToolInput;
import com.openclaw.desktop.tool.ToolResult;
import com.openclaw.desktop.types.JsonObject;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * URL 抓取工具 — 对应 OpenClaw 的 web_fetch。
 */
public class WebFetchTool implements Tool {

    private static final Logger log = LoggerFactory.getLogger(WebFetchTool.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int MAX_CHARS = 50000;

    private final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();

    @Override
    public ToolDescriptor descriptor() {
        ObjectNode schema = MAPPER.createObjectNode();
        schema.put("type", "object");
        schema.set("properties", MAPPER.createObjectNode()
            .set("url", MAPPER.createObjectNode()
                .put("type", "string")
                .put("description", "URL to fetch")));
        schema.set("required", MAPPER.createArrayNode().add("url"));
        return new ToolDescriptor("web_fetch", "Fetch URL",
            "Fetch content from a URL and return as text.",
            JsonObject.wrap(schema), JsonObject.empty());
    }

    @Override
    public Mono<ToolResult> execute(ToolInput input, ToolContext context) {
        return Mono.fromCallable(() -> {
            var args = MAPPER.readTree(input.arguments());
            var url = args.path("url").asText();

            var request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "Mozilla/5.0 (compatible; ClawDesktop/0.1)")
                .GET()
                .timeout(Duration.ofSeconds(30))
                .build();

            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            var body = response.body();

            // 简单 HTML 清理
            body = body.replaceAll("(?s)<script[^>]*>.*?</script>", "");
            body = body.replaceAll("(?s)<style[^>]*>.*?</style>", "");
            body = body.replaceAll("(?s)<!--.*?-->", "");
            body = body.replaceAll("<[^>]+>", " ");
            body = body.replaceAll("&nbsp;", " ");
            body = body.replaceAll("&amp;", "&");
            body = body.replaceAll("&lt;", "<");
            body = body.replaceAll("&gt;", ">");
            body = body.replaceAll("&quot;", "\"");
            body = body.replaceAll("\\s{2,}", " ").trim();

            if (body.length() > MAX_CHARS) {
                body = body.substring(0, MAX_CHARS) + "\n... [truncated]";
            }

            return ToolResult.success(input.toolCallId(), body);
        });
    }
}
