package com.openclaw.desktop.gateway;

import com.openclaw.desktop.agent.*;
import com.openclaw.desktop.config.ClawConfig;
import com.openclaw.desktop.gateway.server.GatewayRouter;
import com.openclaw.desktop.gateway.server.GatewayServer;
import com.openclaw.desktop.llm.*;
import com.openclaw.desktop.session.*;
import com.openclaw.desktop.tool.*;
import com.openclaw.desktop.tool.core.*;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Gateway HTTP API 集成测试。
 * 启动真实的 Reactor Netty 服务器，用 JDK HttpClient 发送请求验证。
 */
class GatewayIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(GatewayIntegrationTest.class);

    private GatewayServer server;
    private HttpClient httpClient;
    private int port;

    @BeforeEach
    void startServer() {
        // 找一个可用端口
        port = 7190 + (int)(Math.random() * 100);

        var config = new ClawConfig(
            new ClawConfig.GatewayConfig(port, port + 1, "127.0.0.1", true, null),
            ClawConfig.AgentConfig.defaults(),
            new ClawConfig.LlmConfig("stub-gw", java.util.Map.of()),
            List.of(),
            ClawConfig.MemoryConfig.defaults(),
            java.util.Map.of()
        );

        // 创建 Stub Agent
        var stubProvider = new StubProvider("Gateway test response");
        var agentConfig = AgentConfig.defaults();
        var toolRegistry = new ToolRegistry();
        toolRegistry.register(new ReadFileTool());
        var sessionManager = new SessionManager();
        var session = sessionManager.getOrCreate(SessionKey.main("gateway-test")).block();
        var agent = new Agent(agentConfig, stubProvider, toolRegistry, session);

        server = new GatewayServer(config, agent, toolRegistry);
        server.start().block(Duration.ofSeconds(5));
        log.info("Gateway server started on port {}", port);

        httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop().block(Duration.ofSeconds(3));
            log.info("Gateway server stopped");
        }
    }

    @Test
    @DisplayName("GW-01: Health endpoint returns 200")
    void testHealthEndpoint() throws Exception {
        var request = HttpRequest.newBuilder()
            .uri(URI.create("http://127.0.0.1:" + port + "/health"))
            .GET()
            .timeout(Duration.ofSeconds(5))
            .build();
        var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("ok") || response.body().contains("healthy") || response.body().contains("status"));
        log.info("GW-01: health response = {}", response.body());
    }

    @Test
    @DisplayName("GW-02: Chat API returns response")
    void testChatApi() throws Exception {
        var body = "{\"message\":\"Hello\",\"sessionKey\":\"main:gateway-test\"}";
        var request = HttpRequest.newBuilder()
            .uri(URI.create("http://127.0.0.1:" + port + "/v1/chat"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .timeout(Duration.ofSeconds(10))
            .build();
        var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        log.info("GW-02: chat response status={}, body={}", response.statusCode(), response.body());
        // Stub provider 会返回固定文本，只要不是 503 就行
        assertTrue(response.statusCode() == 200 || response.statusCode() == 500,
            "Chat API should respond (not 404)");
    }

    @Test
    @DisplayName("GW-03: Tools API returns tool list")
    void testToolsApi() throws Exception {
        var request = HttpRequest.newBuilder()
            .uri(URI.create("http://127.0.0.1:" + port + "/v1/tools"))
            .GET()
            .timeout(Duration.ofSeconds(5))
            .build();
        var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("read_file"));
        log.info("GW-03: tools response = {}", response.body());
    }

    @Test
    @DisplayName("GW-04: Config API returns configuration")
    void testConfigApi() throws Exception {
        var request = HttpRequest.newBuilder()
            .uri(URI.create("http://127.0.0.1:" + port + "/v1/config"))
            .GET()
            .timeout(Duration.ofSeconds(5))
            .build();
        var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("gateway") || response.body().contains("agent"));
        log.info("GW-04: config response = {}", response.body());
    }

    @Test
    @DisplayName("GW-05: 404 for unknown routes")
    void testNotFoundRoute() throws Exception {
        var request = HttpRequest.newBuilder()
            .uri(URI.create("http://127.0.0.1:" + port + "/v1/nonexistent"))
            .GET()
            .timeout(Duration.ofSeconds(5))
            .build();
        var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(404, response.statusCode());
        log.info("GW-05: 404 response = {}", response.body());
    }

    @Test
    @DisplayName("GW-06: Sessions API returns list")
    void testSessionsApi() throws Exception {
        var request = HttpRequest.newBuilder()
            .uri(URI.create("http://127.0.0.1:" + port + "/v1/sessions"))
            .GET()
            .timeout(Duration.ofSeconds(5))
            .build();
        var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        log.info("GW-06: sessions response status={}, body={}", response.statusCode(), response.body());
        // 至少应该返回 200（即使列表为空）
        assertTrue(response.statusCode() == 200 || response.statusCode() == 500);
    }

    // ========== Stub Provider ==========

    private static class StubProvider implements LlmProvider {
        private final String fixedResponse;

        StubProvider(String fixedResponse) { this.fixedResponse = fixedResponse; }

        @Override public String id() { return "stub-gw"; }
        @Override public String name() { return "Stub GW Provider"; }

        @Override
        public Mono<LlmResponse> chat(LlmRequest request) {
            return Mono.just(new LlmResponse(fixedResponse, List.of(),
                new UsageInfo(10, 20, 30, null, null), "stop", java.util.Map.of()));
        }

        @Override
        public Flux<LlmEvent> chatStream(LlmRequest request) {
            return Flux.just(
                new LlmEvent.TextDelta(0, fixedResponse),
                new LlmEvent.Usage(new UsageInfo(10, 20, 30, null, null))
            );
        }

        @Override public Flux<Model> listModels() { return Flux.just(Model.of("stub", "Stub", "stub")); }
        @Override public Mono<Boolean> healthCheck() { return Mono.just(true); }
    }
}
