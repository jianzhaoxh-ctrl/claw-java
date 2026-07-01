package com.openclaw.desktop.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * MCP 客户端 — 管理与单个 MCP 服务器的 JSON-RPC 2.0 连接（stdio 传输）。
 *
 * <p>对应 MCP 规范：
 * <ol>
 *   <li>{@link #start()} 启动子进程，发送 {@code initialize} 请求完成握手，
 *       再发送 {@code notifications/initialized} 通知</li>
 *   <li>{@link #listTools()} 发送 {@code tools/list} 发现工具</li>
 *   <li>{@link #callTool} 发送 {@code tools/call} 调用工具</li>
 *   <li>{@link #close()} 终止进程并释放资源</li>
 * </ol>
 *
 * <p>通信格式：每行一个 JSON-RPC 消息（换行分隔），通过子进程的 stdin/stdout 传输。
 * 请求-响应通过 {@code id} 匹配，使用 {@link CompletableFuture} 异步等待。
 */
public class McpClient implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(McpClient.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final McpServerConfig config;
    private final AtomicLong nextId = new AtomicLong(1);
    private final Map<Long, CompletableFuture<JsonNode>> pending = new ConcurrentHashMap<>();
    private final AtomicBoolean running = new AtomicBoolean(false);

    private Process process;
    private PrintWriter stdin;
    private BufferedReader stdout;
    private Thread readerThread;
    private Thread stderrDrainThread;

    /** 服务器声明的能力（initialize 握手后获得）。 */
    private JsonNode serverCapabilities;
    /** 服务器信息。 */
    private String serverName;
    private String serverVersion;

    public McpClient(McpServerConfig config) {
        this.config = config;
    }

    /**
     * 启动 MCP 服务器进程并完成 initialize 握手。
     */
    public synchronized void start() throws IOException {
        if (running.get()) return;
        if (config.command() == null) {
            throw new IOException("McpClient (stdio) requires a command, got SSE url for: " + config.name());
        }

        // 1. 构建命令
        var cmdList = new ArrayList<String>();
        cmdList.add(config.command());
        if (config.args() != null) cmdList.addAll(config.args());

        log.info("Starting MCP server '{}': {}", config.name(), String.join(" ", cmdList));
        var pb = new ProcessBuilder(cmdList);
        pb.environment().putAll(config.env());
        pb.redirectErrorStream(false);
        process = pb.start();

        stdin = new PrintWriter(process.getOutputStream(), true);
        stdout = new BufferedReader(new java.io.InputStreamReader(process.getInputStream()));

        running.set(true);

        // 2. 后台读取 stdout（虚拟线程），分发响应/通知
        readerThread = Thread.ofVirtual()
            .name("mcp-reader-" + config.name())
            .unstarted(this::readLoop);
        readerThread.start();

        // 3. 后台排空 stderr（避免子进程因 stderr 缓冲区满而阻塞）
        stderrDrainThread = Thread.ofVirtual()
            .name("mcp-stderr-" + config.name())
            .unstarted(this::drainStderr);
        stderrDrainThread.start();

        // 4. initialize 握手
        try {
            initialize();
        } catch (Exception e) {
            close();
            throw new IOException("MCP initialize handshake failed for '" + config.name() + "': " + e.getMessage(), e);
        }

        log.info("MCP server '{}' connected: {} v{}", config.name(), serverName, serverVersion);
    }

    /** 执行 initialize 握手。 */
    private void initialize() throws Exception {
        var params = MAPPER.createObjectNode();
        params.put("protocolVersion", "2024-11-05");
        var clientInfo = MAPPER.createObjectNode();
        clientInfo.put("name", "ClawDesktop");
        clientInfo.put("version", "0.1.0");
        params.set("clientInfo", clientInfo);
        var clientCaps = MAPPER.createObjectNode();
        params.set("capabilities", clientCaps);

        var result = sendRequest("initialize", params).get(30, TimeUnit.SECONDS);
        if (result != null) {
            serverCapabilities = result.path("capabilities");
            var info = result.path("serverInfo");
            if (!info.isMissingNode()) {
                serverName = info.path("name").asText("unknown");
                serverVersion = info.path("version").asText("unknown");
            }
        }

        // 发送 initialized 通知（无 id）
        sendNotification("notifications/initialized", MAPPER.createObjectNode());
    }

    /**
     * 列出服务器提供的工具。
     */
    public List<McpTool> listTools() throws Exception {
        var result = sendRequest("tools/list", MAPPER.createObjectNode()).get(30, TimeUnit.SECONDS);
        if (result == null) return List.of();
        var toolsArray = result.path("tools");
        if (!toolsArray.isArray()) return List.of();

        var tools = new ArrayList<McpTool>();
        for (var t : toolsArray) {
            var name = t.path("name").asText();
            var desc = t.path("description").asText("");
            var schema = t.path("inputSchema");
            var schemaStr = schema.isMissingNode() ? "{}" : MAPPER.writeValueAsString(schema);
            tools.add(new McpTool(config.name(), name, desc, schemaStr));
        }
        return tools;
    }

    /**
     * 调用服务器上的工具。
     * @param toolName 工具名
     * @param argumentsJson 参数（JSON 字符串）
     * @return 工具返回的文本内容（拼接所有 text 类型 content）
     */
    public String callTool(String toolName, String argumentsJson) throws Exception {
        var params = MAPPER.createObjectNode();
        params.put("name", toolName);
        // arguments 必须是对象
        JsonNode argsNode;
        try {
            argsNode = argumentsJson == null || argumentsJson.isBlank()
                ? MAPPER.createObjectNode()
                : MAPPER.readTree(argumentsJson);
        } catch (Exception e) {
            argsNode = MAPPER.createObjectNode();
        }
        params.set("arguments", argsNode);

        var result = sendRequest("tools/call", params).get(60, TimeUnit.SECONDS);
        if (result == null) return "";

        // 拼接 content 数组中的文本
        var content = result.path("content");
        var sb = new StringBuilder();
        if (content.isArray()) {
            for (var item : content) {
                if ("text".equals(item.path("type").asText())) {
                    if (sb.length() > 0) sb.append("\n");
                    sb.append(item.path("text").asText());
                }
            }
        }
        // 如果服务器标记 isError，抛出
        if (result.path("isError").asBoolean(false)) {
            throw new RuntimeException("MCP tool error: " + sb);
        }
        return sb.toString();
    }

    // ---- JSON-RPC 底层 ----

    /**
     * 发送 JSON-RPC 请求（带 id），返回响应的 CompletableFuture。
     */
    public CompletableFuture<JsonNode> sendRequest(String method, JsonNode params) {
        var id = nextId.getAndIncrement();
        var req = MAPPER.createObjectNode();
        req.put("jsonrpc", "2.0");
        req.put("id", id);
        req.put("method", method);
        if (params != null) req.set("params", params);

        var future = new CompletableFuture<JsonNode>();
        pending.put(id, future);
        writeLine(req.toString());
        log.debug("MCP [{}] → request #{}: {}", config.name(), id, method);
        return future;
    }

    /**
     * 发送 JSON-RPC 通知（无 id，无需响应）。
     */
    public void sendNotification(String method, JsonNode params) {
        var notif = MAPPER.createObjectNode();
        notif.put("jsonrpc", "2.0");
        notif.put("method", method);
        if (params != null) notif.set("params", params);
        writeLine(notif.toString());
        log.debug("MCP [{}] → notification: {}", config.name(), method);
    }

    private synchronized void writeLine(String json) {
        if (!running.get()) return;
        stdin.println(json);
        stdin.flush();
    }

    /** 后台读取子进程 stdout，按行解析 JSON-RPC 消息并分发。 */
    private void readLoop() {
        try {
            String line;
            while (running.get() && (line = stdout.readLine()) != null) {
                if (line.isBlank()) continue;
                handleIncoming(line);
            }
        } catch (IOException e) {
            if (running.get()) {
                log.warn("MCP [{}] stdout read error: {}", config.name(), e.getMessage());
            }
        } finally {
            // 进程结束，让所有 pending 请求失败
            failAllPending("MCP server '" + config.name() + "' closed connection");
        }
    }

    private void drainStderr() {
        try (var errReader = new BufferedReader(new java.io.InputStreamReader(process.getErrorStream()))) {
            String line;
            while ((line = errReader.readLine()) != null) {
                log.debug("MCP [{}] stderr: {}", config.name(), line);
            }
        } catch (IOException e) {
            // ignore
        }
    }

    private void handleIncoming(String line) {
        JsonNode msg;
        try {
            msg = MAPPER.readTree(line);
        } catch (Exception e) {
            log.warn("MCP [{}] invalid JSON: {} - {}", config.name(), line, e.getMessage());
            return;
        }

        // 响应（带 id）
        if (msg.has("id")) {
            var id = msg.path("id").asLong();
            var future = pending.remove(id);
            if (future == null) {
                log.warn("MCP [{}] received response for unknown id {}", config.name(), id);
                return;
            }
            if (msg.has("error")) {
                var err = msg.path("error");
                var code = err.path("code").asInt();
                var message = err.path("message").asText("unknown error");
                log.warn("MCP [{}] ← error #{}: {} ({})", config.name(), id, message, code);
                future.completeExceptionally(new RuntimeException("MCP error " + code + ": " + message));
            } else {
                var result = msg.path("result");
                log.debug("MCP [{}] ← result #{}", config.name(), id);
                future.complete(result);
            }
            return;
        }

        // 通知（无 id）
        var method = msg.path("method").asText("");
        if (!method.isEmpty()) {
            log.debug("MCP [{}] ← notification: {}", config.name(), method);
            handleNotification(method, msg.path("params"));
        }
    }

    /** 处理服务器主动发送的通知（如 resources/updated, tools/list_changed）。 */
    protected void handleNotification(String method, JsonNode params) {
        // 子类可重写以处理资源变更、工具列表变更等通知
        switch (method) {
            case "notifications/resources/updated" ->
                log.debug("MCP [{}] resource updated: {}", config.name(), params.path("uri").asText());
            case "notifications/tools/list_changed" ->
                log.info("MCP [{}] tool list changed, may need refresh", config.name());
            default -> { /* ignore unknown */ }
        }
    }

    private void failAllPending(String reason) {
        if (pending.isEmpty()) return;
        log.warn("MCP [{}] failing {} pending request(s): {}", config.name(), pending.size(), reason);
        var it = pending.entrySet().iterator();
        while (it.hasNext()) {
            var e = it.next();
            it.remove();
            e.getValue().completeExceptionally(new RuntimeException(reason));
        }
    }

    // ---- 生命周期 ----

    public boolean isRunning() {
        return running.get() && process != null && process.isAlive();
    }

    public McpServerConfig config() { return config; }
    public String serverName() { return serverName; }
    public String serverVersion() { return serverVersion; }
    public JsonNode serverCapabilities() { return serverCapabilities; }

    @Override
    public synchronized void close() {
        running.set(false);
        failAllPending("MCP client closed");

        if (stdin != null) {
            try { stdin.close(); } catch (Exception e) { /* ignore */ }
        }
        if (process != null && process.isAlive()) {
            process.destroy();
            try {
                if (!process.waitFor(3, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                    log.warn("MCP [{}] force-killed", config.name());
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                process.destroyForcibly();
            }
        }
        log.info("MCP server '{}' disconnected", config.name());
    }
}
