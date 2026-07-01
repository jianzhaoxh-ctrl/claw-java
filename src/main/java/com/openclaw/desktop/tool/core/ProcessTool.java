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

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 进程管理工具 — 列出/终止进程。
 */
public class ProcessTool implements Tool {

    private static final Logger log = LoggerFactory.getLogger(ProcessTool.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");

    @Override
    public ToolDescriptor descriptor() {
        ObjectNode schema = MAPPER.createObjectNode();
        schema.put("type", "object");
        var props = MAPPER.createObjectNode();
        props.set("action", MAPPER.createObjectNode()
            .put("type", "string")
            .put("description", "Action: list or kill"));
        props.set("pid", MAPPER.createObjectNode()
            .put("type", "number")
            .put("description", "Process ID (for kill action)"));
        schema.set("properties", props);
        schema.set("required", MAPPER.createArrayNode().add("action"));
        return new ToolDescriptor("process", "Process Manager",
            "List running processes or kill a process by PID.",
            JsonObject.wrap(schema), JsonObject.empty());
    }

    @Override
    public Mono<ToolResult> execute(ToolInput input, ToolContext context) {
        return Mono.fromCallable(() -> {
            var args = MAPPER.readTree(input.arguments());
            var action = args.path("action").asText("list");

            if ("list".equals(action)) {
                var cmd = isWindows
                    ? new String[]{"powershell", "-NoProfile", "-Command", "Get-Process | Select-Object Id,ProcessName,CPU,WorkingSet | Format-Table -AutoSize"}
                    : new String[]{"bash", "-c", "ps aux --sort=-%mem | head -30"};
                return execCommand(input.toolCallId(), cmd, 10);
            } else if ("kill".equals(action)) {
                var pid = args.path("pid").asInt();
                var cmd = isWindows
                    ? new String[]{"taskkill", "/PID", String.valueOf(pid), "/F"}
                    : new String[]{"kill", "-9", String.valueOf(pid)};
                return execCommand(input.toolCallId(), cmd, 5);
            }
            return ToolResult.failure(input.toolCallId(), "Unknown action: " + action);
        });
    }

    private ToolResult execCommand(String toolCallId, String[] cmd, int timeoutSec) throws Exception {
        var pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        var process = pb.start();
        var output = new BufferedReader(
            new InputStreamReader(process.getInputStream(), Charset.defaultCharset())
        ).lines().collect(Collectors.joining("\n"));
        var finished = process.waitFor(timeoutSec, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            return ToolResult.failure(toolCallId, "Command timed out");
        }
        return ToolResult.success(toolCallId, output);
    }
}
