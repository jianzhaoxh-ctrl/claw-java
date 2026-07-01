package com.openclaw.desktop.gateway.api;

import com.openclaw.desktop.agent.Agent;
import com.openclaw.desktop.agent.AgentEvent;
import com.openclaw.desktop.gateway.server.GatewayRouter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import reactor.netty.http.server.HttpServerRequest;
import reactor.netty.http.server.HttpServerResponse;

import java.time.Duration;

/**
 * 聊天 API — POST /v1/chat, GET /v1/chat/stream
 * 接入真实 Agent，支持流式输出。
 */
public class ChatApi {

    private static final Logger log = LoggerFactory.getLogger(ChatApi.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Agent agent;

    public ChatApi(Agent agent) {
        this.agent = agent;
    }

    /**
     * POST /v1/chat
     * Body: {"message": "hello", "stream": false}
     * Response: {"content": "...", "session": "..."}
     */
    public Mono<Void> chat(HttpServerRequest req, HttpServerResponse res) {
        GatewayRouter.addCorsHeaders(res);
        return req.receive().aggregate().asString().flatMap(body -> {
            try {
                var json = MAPPER.readTree(body);
                var message = json.path("message").asText("");
                if (message.isEmpty()) {
                    return sendError(res, 400, "Missing 'message' field");
                }

                return agent.chat(message)
                    .map(content -> {
                        try {
                            var resp = MAPPER.createObjectNode();
                            resp.put("content", content);
                            resp.put("session", agent.session().key().toString());
                            return MAPPER.writeValueAsString(resp);
                        } catch (Exception e) {
                            return "{\"error\":\"serialization failed\"}";
                        }
                    })
                    .flatMap(respText -> res
                        .header("Content-Type", "application/json")
                        .sendString(Mono.just(respText))
                        .then())
                    .onErrorResume(e -> {
                        log.error("Chat error", e);
                        return sendError(res, 500, e.getMessage());
                    });
            } catch (Exception e) {
                return sendError(res, 400, "Invalid JSON: " + e.getMessage());
            }
        });
    }

    /**
     * GET /v1/chat/stream?message=hello
     * SSE 流式响应
     */
    public Mono<Void> chatStream(HttpServerRequest req, HttpServerResponse res) {
        GatewayRouter.addCorsHeaders(res);
        var message = req.param("message");
        if (message == null || message.isEmpty()) {
            return sendError(res, 400, "Missing 'message' parameter");
        }

        return res.header("Content-Type", "text/event-stream")
            .header("Cache-Control", "no-cache")
            .header("Connection", "keep-alive")
            .sendString(
                agent.chatStream(message)
                    .map(chunk -> "event: text\ndata: {\"delta\":\"" + escapeJson(chunk) + "\"}\n\n")
                    .onErrorResume(e -> {
                        log.error("Stream error", e);
                        return reactor.core.publisher.Flux.just(
                            "event: error\ndata: {\"message\":\"" + escapeJson(e.getMessage()) + "\"}\n\n"
                        );
                    })
                    .concatWith(Mono.just("event: done\ndata: {}\n\n"))
            )
            .then();
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }

    private Mono<Void> sendError(HttpServerResponse res, int status, String message) {
        try {
            var err = MAPPER.createObjectNode();
            err.put("error", message);
            return res.status(status)
                .header("Content-Type", "application/json")
                .sendString(Mono.just(MAPPER.writeValueAsString(err)))
                .then();
        } catch (Exception e) {
            return res.status(status).sendString(Mono.just("{\"error\":\"" + message + "\"}")).then();
        }
    }
}
