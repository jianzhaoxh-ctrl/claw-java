package com.openclaw.desktop.tool.core;

import com.openclaw.desktop.llm.ToolDescriptor;
import com.openclaw.desktop.tool.*;
import com.openclaw.desktop.types.JsonObject;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 差异比较工具 — 比较两个文件/文本的差异，输出 unified diff 格式。
 * 对应 OpenClaw 的 diffs extension。
 */
public class DiffComparisonTool implements Tool {

    private static final Logger log = LoggerFactory.getLogger(DiffComparisonTool.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public ToolDescriptor descriptor() {
        var inputSchema = new JsonObject(Map.of(
            "fileA", Map.of("type", "string", "description", "First file path or text"),
            "fileB", Map.of("type", "string", "description", "Second file path or text"),
            "isPath", Map.of("type", "boolean", "description", "If true, treat inputs as file paths; false = raw text"),
            "contextLines", Map.of("type", "number", "description", "Context lines around changes (default 3)")
        ));
        return new ToolDescriptor(
            "diff",
            "Diff Comparison",
            "Compare two files or text blocks and show differences in unified diff format.",
            inputSchema,
            JsonObject.empty()
        );
    }

    @Override
    public Mono<ToolResult> execute(ToolInput input, ToolContext context) {
        return Mono.fromCallable(() -> {
            var args = MAPPER.readTree(input.arguments());
            var a = args.path("fileA").asText("");
            var b = args.path("fileB").asText("");
            var isPath = args.path("isPath").asBoolean(true);

            if (a.isEmpty() || b.isEmpty()) {
                return ToolResult.failure(input.toolCallId(), "Both fileA and fileB are required");
            }

            var linesA = isPath ? readFileLines(a) : splitLines(a);
            var linesB = isPath ? readFileLines(b) : splitLines(b);

            var diff = computeDiff(linesA, linesB);

            if (diff.isEmpty()) {
                return ToolResult.success(input.toolCallId(), "✅ No differences found. Files are identical.");
            }

            var sb = new StringBuilder();
            sb.append("--- ").append(isPath ? a : "text A").append("\n");
            sb.append("+++ ").append(isPath ? b : "text B").append("\n");
            for (var line : diff) {
                sb.append(line).append("\n");
            }
            return ToolResult.success(input.toolCallId(), sb.toString());
        });
    }

    private List<String> readFileLines(String path) throws Exception {
        return Files.readAllLines(Paths.get(path));
    }

    private List<String> splitLines(String text) {
        return List.of(text.split("\n"));
    }

    /**
     * 简化 LCS diff 算法。
     */
    private List<String> computeDiff(List<String> a, List<String> b) {
        var result = new ArrayList<String>();
        int m = a.size(), n = b.size();
        // LCS DP 表
        var dp = new int[m + 1][n + 1];
        for (int i = m - 1; i >= 0; i--) {
            for (int j = n - 1; j >= 0; j--) {
                if (a.get(i).equals(b.get(j))) {
                    dp[i][j] = dp[i + 1][j + 1] + 1;
                } else {
                    dp[i][j] = Math.max(dp[i + 1][j], dp[i][j + 1]);
                }
            }
        }
        // 回溯
        int i = 0, j = 0;
        int added = 0, removed = 0;
        while (i < m && j < n) {
            if (a.get(i).equals(b.get(j))) {
                result.add("  " + a.get(i));
                i++; j++;
            } else if (dp[i + 1][j] >= dp[i][j + 1]) {
                result.add("- " + a.get(i));
                i++;
                removed++;
            } else {
                result.add("+ " + b.get(j));
                j++;
                added++;
            }
        }
        while (i < m) { result.add("- " + a.get(i)); i++; removed++; }
        while (j < n) { result.add("+ " + b.get(j)); j++; added++; }

        // 在头部插入统计
        result.add(0, "@@ Summary: +" + added + " -" + removed + " @@");
        return result;
    }
}
