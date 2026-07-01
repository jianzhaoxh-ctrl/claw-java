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
 * Web 搜索工具 — 对应 OpenClaw 的 web_search。
 * 使用 DuckDuckGo HTML 接口（无需 API Key）。
 */
public class WebSearchTool implements Tool {

    private static final Logger log = LoggerFactory.getLogger(WebSearchTool.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String SEARCH_URL = "https://html.duckduckgo.com/html/?q=";

    private final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();

    @Override
    public ToolDescriptor descriptor() {
        ObjectNode schema = MAPPER.createObjectNode();
        schema.put("type", "object");
        schema.set("properties", MAPPER.createObjectNode()
            .set("query", MAPPER.createObjectNode()
                .put("type", "string")
                .put("description", "Search query")));
        schema.set("required", MAPPER.createArrayNode().add("query"));
        return new ToolDescriptor("web_search", "Web Search",
            "Search the web and return results.",
            JsonObject.wrap(schema), JsonObject.empty());
    }

    @Override
    public Mono<ToolResult> execute(ToolInput input, ToolContext context) {
        return Mono.fromCallable(() -> {
            var args = MAPPER.readTree(input.arguments());
            var query = args.path("query").asText();
            var encodedQuery = java.net.URLEncoder.encode(query, java.nio.charset.StandardCharsets.UTF_8);

            var request = HttpRequest.newBuilder()
                .uri(URI.create(SEARCH_URL + encodedQuery))
                .header("User-Agent", "Mozilla/5.0")
                .GET()
                .timeout(Duration.ofSeconds(15))
                .build();

            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            var body = response.body();

            // 简单解析 DuckDuckGo HTML 结果
            var sb = new StringBuilder();
            var titlePattern = java.util.regex.Pattern.compile("<a rel=\"nofollow\" class=\"result__a\"[^>]*>(.*?)</a>");
            var snippetPattern = java.util.regex.Pattern.compile("<a class=\"result__snippet\"[^>]*>(.*?)</a>");
            var linkPattern = java.util.regex.Pattern.compile("href=\"(https?://[^\"]+)\"");
            var stripTags = java.util.regex.Pattern.compile("<[^>]+>");

            var titles = titlePattern.matcher(body);
            var snippets = snippetPattern.matcher(body);
            int count = 0;
            int pos = 0;
            while (titles.find() && count < 10) {
                var title = stripTags.matcher(titles.group(1)).replaceAll("").trim();
                String snippet = "";
                if (snippets.find()) {
                    snippet = stripTags.matcher(snippets.group(1)).replaceAll("").trim();
                }
                sb.append(String.format("%d. %s%n   %s%n%n", ++count, title, snippet));
            }

            if (count == 0) {
                return ToolResult.failure(input.toolCallId(), "No results found for: " + query);
            }
            return ToolResult.success(input.toolCallId(), sb.toString());
        });
    }
}
