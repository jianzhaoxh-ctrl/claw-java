package com.openclaw.desktop.tool.core;

import com.openclaw.desktop.llm.ToolDescriptor;
import com.openclaw.desktop.tool.*;
import com.openclaw.desktop.types.JsonObject;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.io.*;
import java.nio.file.*;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 代码执行沙箱 — 在隔离进程中执行代码，超时自动终止。
 * 对应 OpenClaw 的 sandbox 工具。
 * 支持：Python3、JavaScript(Node)、Shell、Java。
 */
public class CodeSandboxTool implements Tool {

    private static final Logger log = LoggerFactory.getLogger(CodeSandboxTool.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int DEFAULT_TIMEOUT_SECONDS = 30;
    private static final int MAX_OUTPUT_LENGTH = 20000;

    @Override
    public ToolDescriptor descriptor() {
        var inputSchema = new JsonObject(Map.of(
            "language", Map.of("type", "string", "enum", "python,javascript,shell,java", "description", "Programming language"),
            "code", Map.of("type", "string", "description", "Code to execute"),
            "timeout", Map.of("type", "number", "description", "Timeout in seconds (default 30)")
        ));
        return new ToolDescriptor(
            "code_sandbox",
            "Code Sandbox",
            "Execute code in a sandboxed process. Supports Python, JavaScript (Node), Shell, and Java. Auto-timeout.",
            inputSchema,
            JsonObject.empty()
        );
    }

    @Override
    public Mono<ToolResult> execute(ToolInput input, ToolContext context) {
        return Mono.fromCallable(() -> {
            var args = MAPPER.readTree(input.arguments());
            var language = args.path("language").asText("python");
            var code = args.path("code").asText("");
            var timeout = args.path("timeout").asInt(DEFAULT_TIMEOUT_SECONDS);

            if (code.isEmpty()) {
                return ToolResult.failure(input.toolCallId(), "Code is required");
            }

            return switch (language) {
                case "python" -> executePython(code, timeout, input);
                case "javascript", "js", "node" -> executeNode(code, timeout, input);
                case "shell", "bash", "sh" -> executeShell(code, timeout, input);
                case "java" -> executeJava(code, timeout, input);
                default -> ToolResult.failure(input.toolCallId(), "Unsupported language: " + language);
            };
        });
    }

    private ToolResult runProcess(ProcessBuilder pb, int timeout, ToolInput input) {
        try {
            pb.redirectErrorStream(true);
            var process = pb.start();
            var output = new StringBuilder();
            try (var reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append('\n');
                    if (output.length() > MAX_OUTPUT_LENGTH) {
                        output.append("\n... [output truncated at ").append(MAX_OUTPUT_LENGTH).append(" chars]");
                        break;
                    }
                }
            }
            var finished = process.waitFor(timeout, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return ToolResult.failure(input.toolCallId(),
                    "Execution timed out after " + timeout + "s\nPartial output:\n" + output);
            }
            var exitCode = process.exitValue();
            var result = output.toString();
            if (exitCode != 0) {
                result = "Exit code: " + exitCode + "\n\n" + result;
            }
            return ToolResult.success(input.toolCallId(), result);
        } catch (Exception e) {
            return ToolResult.failure(input.toolCallId(), "Execution failed: " + e.getMessage());
        }
    }

    private ToolResult executePython(String code, int timeout, ToolInput input) throws IOException {
        var tmp = Files.createTempFile("claw-sandbox-", ".py");
        Files.writeString(tmp, code);
        return runProcess(new ProcessBuilder("python", tmp.toString()), timeout, input);
    }

    private ToolResult executeNode(String code, int timeout, ToolInput input) throws IOException {
        var tmp = Files.createTempFile("claw-sandbox-", ".js");
        Files.writeString(tmp, code);
        return runProcess(new ProcessBuilder("node", tmp.toString()), timeout, input);
    }

    private ToolResult executeShell(String code, int timeout, ToolInput input) throws IOException {
        var osName = System.getProperty("os.name").toLowerCase();
        if (osName.contains("win")) {
            return runProcess(new ProcessBuilder("cmd", "/c", code), timeout, input);
        } else {
            return runProcess(new ProcessBuilder("sh", "-c", code), timeout, input);
        }
    }

    private ToolResult executeJava(String code, int timeout, ToolInput input) throws IOException {
        var tmpDir = Files.createTempDirectory("claw-sandbox-");
        var javaFile = tmpDir.resolve("Main.java");
        Files.writeString(javaFile, code);
        var compilePb = new ProcessBuilder("javac", javaFile.toString());
        compilePb.directory(tmpDir.toFile());
        var compileResult = runProcess(compilePb, 15, input);
        if (!compileResult.success()) {
            return compileResult;
        }
        var runPb = new ProcessBuilder("java", "-cp", tmpDir.toString(), "Main");
        return runProcess(runPb, timeout, input);
    }
}
