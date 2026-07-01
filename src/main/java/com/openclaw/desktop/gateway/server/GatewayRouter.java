package com.openclaw.desktop.gateway.server;

import com.openclaw.desktop.agent.Agent;
import com.openclaw.desktop.config.ClawConfig;
import com.openclaw.desktop.gateway.api.ChatApi;
import com.openclaw.desktop.gateway.api.ConfigApi;
import com.openclaw.desktop.gateway.api.HealthApi;
import com.openclaw.desktop.gateway.api.SessionApi;
import com.openclaw.desktop.gateway.api.ToolApi;
import com.openclaw.desktop.tool.ToolRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import reactor.netty.http.server.HttpServerResponse;
import reactor.netty.http.server.HttpServerRoutes;

/**
 * 请求路由器 — 注册所有 API 路由。
 */
public class GatewayRouter {

    private static final Logger log = LoggerFactory.getLogger(GatewayRouter.class);

    private final ClawConfig config;
    private final HealthApi healthApi;
    private final ChatApi chatApi;
    private final SessionApi sessionApi;
    private final ToolApi toolApi;
    private final ConfigApi configApi;
    private com.openclaw.desktop.gateway.ws.WebSocketHandler wsHandler;

    public GatewayRouter(ClawConfig config) {
        this(config, null, null);
    }

    public GatewayRouter(ClawConfig config, Agent agent, ToolRegistry toolRegistry) {
        this(config, agent, toolRegistry, null);
    }

    public GatewayRouter(ClawConfig config, Agent agent, ToolRegistry toolRegistry,
                         com.openclaw.desktop.session.SessionManager sessionManager) {
        this.config = config;
        this.healthApi = new HealthApi();
        this.chatApi = agent != null ? new ChatApi(agent) : null;
        this.sessionApi = new SessionApi();
        if (sessionManager != null) {
            this.sessionApi.setSessionManager(sessionManager);
        }
        this.toolApi = toolRegistry != null ? new ToolApi(toolRegistry) : new ToolApi();
        this.configApi = new ConfigApi(config);
    }

    public void configure(HttpServerRoutes routes) {
        log.info("Registering API routes...");

        // CORS preflight
        routes.options("/v1/**", (req, res) -> {
            addCorsHeaders(res);
            return res.status(204).send().then();
        });

        // Health
        routes.get("/health", healthApi::health);

        // Chat API
        if (chatApi != null) {
            routes.post("/v1/chat", chatApi::chat);
            routes.get("/v1/chat/stream", chatApi::chatStream);
        } else {
            routes.post("/v1/chat", (req, res) -> jsonResponse(res, 503, "{\"error\":\"Agent not initialized\"}"));
            routes.get("/v1/chat/stream", (req, res) -> jsonResponse(res, 503, "{\"error\":\"Agent not initialized\"}"));
        }

        // Session API
        routes.get("/v1/sessions", sessionApi::listSessions);
        routes.get("/v1/sessions/{key}", sessionApi::getSession);
        routes.delete("/v1/sessions/{key}", sessionApi::deleteSession);
        routes.post("/v1/sessions/{key}/reset", sessionApi::resetSession);

        // Tool API
        routes.get("/v1/tools", toolApi::listTools);
        routes.get("/v1/tools/{name}", toolApi::getTool);

        // Config API
        routes.get("/v1/config", configApi::getConfig);
        routes.route(req -> req.method() == io.netty.handler.codec.http.HttpMethod.PATCH && req.uri().equals("/v1/config"), configApi::updateConfig);

        // WebSocket
        if (wsHandler != null) {
            routes.ws("/ws", wsHandler::handle);
            log.info("WebSocket endpoint registered: /ws");
        }

        // Fallback
        routes.get("/**", (req, res) -> {
            addCorsHeaders(res);
            return res.status(404).sendString(Mono.just("Not Found: " + req.uri())).then();
        });

        log.info("API routes registered");
    }

    public void setWsHandler(com.openclaw.desktop.gateway.ws.WebSocketHandler wsHandler) {
        this.wsHandler = wsHandler;
    }

    public static void addCorsHeaders(HttpServerResponse res) {
        res.header("Access-Control-Allow-Origin", "*");
        res.header("Access-Control-Allow-Methods", "GET, POST, PUT, PATCH, DELETE, OPTIONS");
        res.header("Access-Control-Allow-Headers", "Content-Type, Authorization, X-Session-Key");
    }

    private Mono<Void> jsonResponse(HttpServerResponse res, int status, String body) {
        addCorsHeaders(res);
        return res.status(status)
            .header("Content-Type", "application/json")
            .sendString(Mono.just(body))
            .then();
    }
}
