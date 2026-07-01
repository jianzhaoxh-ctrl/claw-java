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
 * Shell 执行工具 — 对应 OpenClaw 的 exec。
 * 在 Windows 上使用 PowerShell，在 Linux/Mac 上使用 bash。
 */
public class ShellExecTool implements Tool {

    private static final Logger log = LoggerFactory.getLogger(ShellExecTool.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final boolean isWindows;

    public ShellExecTool() {
        this.isWindows = System.getProperty("os.name").toLowerCase().contains("win");
    }

    @Override
    public ToolDescriptor descriptor() {
        ObjectNode schema = MAPPER.createObjectNode();
        schema.put("type", "object");
        var props = schema.putObject("properties");
        props.putObject("command").put("type", "string").put("description", "Shell command to execute");
        props.putObject("timeout").put("type", "number").put("description", "Timeout in seconds (default 30)");
        schema.putArray("required").add("command");
        return new ToolDescriptor("shell_exec", "Execute Shell Command",
            "Execute a shell command and return stdout/stderr.",
            JsonObject.wrap(schema), JsonObject.empty());
    }

    @Override
    public Mono<ToolResult> execute(ToolInput input, ToolContext context) {
        return Mono.fromCallable(() -> {
            var args = MAPPER.readTree(input.arguments());
            var command = args.path("command").asText();
            var timeoutSec = args.path("timeout").asInt(30);

            var shell = isWindows ? new String[]{"powershell", "-NoProfile", "-Command", command}
                                  : new String[]{"bash", "-c", command};

            var pb = new ProcessBuilder(shell);
            pb.redirectErrorStream(true);
            var process = pb.start();

            var output = new BufferedReader(
                new InputStreamReader(process.getInputStream(), Charset.defaultCharset())
            ).lines().collect(Collectors.joining("\n"));

            var finished = process.waitFor(timeoutSec, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return ToolResult.failure(input.toolCallId(), "Command timed out after " + timeoutSec + "s");
            }

            var exitCode = process.exitValue();
            var result = output;
            if (exitCode != 0) {
                result = "Exit code: " + exitCode + "\n" + output;
            }
            return ToolResult.success(input.toolCallId(), result);
        });
    }
}
