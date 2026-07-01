package com.openclaw.desktop.gateway.api;

import com.openclaw.desktop.gateway.server.GatewayRouter;
import com.openclaw.desktop.session.SessionManager;
import com.openclaw.desktop.session.SessionKey;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import reactor.core.publisher.Mono;
import reactor.netty.http.server.HttpServerRequest;
import reactor.netty.http.server.HttpServerResponse;

/**
 * 会话 API — GET/DELETE/POST /v1/sessions
 * 接入 SessionManager。
 */
public class SessionApi {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private SessionManager sessionManager;

    public SessionApi() {}

    public void setSessionManager(SessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    public Mono<Void> listSessions(HttpServerRequest req, HttpServerResponse res) {
        GatewayRouter.addCorsHeaders(res);
        return Mono.fromCallable(() -> {
            var arr = MAPPER.createArrayNode();
            if (sessionManager != null) {
                for (var session : sessionManager.all().toIterable()) {
                    var obj = MAPPER.createObjectNode();
                    obj.put("key", session.key().toString());
                    obj.put("createdAt", session.createdAt().toString());
                    obj.put("updatedAt", session.updatedAt().toString());
                    obj.put("messageCount", session.transcript().size());
                    arr.add(obj);
                }
            }
            return MAPPER.writeValueAsString(arr);
        }).flatMap(json -> res
            .header("Content-Type", "application/json")
            .sendString(Mono.just(json))
            .then());
    }

    public Mono<Void> getSession(HttpServerRequest req, HttpServerResponse res) {
        GatewayRouter.addCorsHeaders(res);
        var key = req.param("key");
        if (key == null || key.isEmpty()) {
            return errorResponse(res, 400, "Missing 'key' parameter");
        }
        return Mono.fromCallable(() -> {
            if (sessionManager == null) {
                return "{\"error\":\"Session manager not initialized\"}";
            }
            var sessionKey = SessionKey.main(key);
            var sessionMono = sessionManager.get(sessionKey);
            var session = sessionMono.block();
            if (session == null) {
                return "{\"error\":\"Session not found: " + key + "\"}";
            }
            var obj = MAPPER.createObjectNode();
            obj.put("key", session.key().toString());
            obj.put("createdAt", session.createdAt().toString());
            obj.put("updatedAt", session.updatedAt().toString());
            var messages = MAPPER.createArrayNode();
            for (var entry : session.transcript().entries()) {
                var msg = MAPPER.createObjectNode();
                msg.put("id", entry.id());
                msg.put("role", entry.role());
                msg.put("content", entry.content());
                if (entry.toolCallId() != null) {
                    msg.put("toolCallId", entry.toolCallId());
                }
                messages.add(msg);
            }
            obj.set("messages", messages);
            return MAPPER.writeValueAsString(obj);
        }).flatMap(json -> res
            .header("Content-Type", "application/json")
            .sendString(Mono.just(json))
            .then());
    }

    public Mono<Void> deleteSession(HttpServerRequest req, HttpServerResponse res) {
        GatewayRouter.addCorsHeaders(res);
        var key = req.param("key");
        if (key != null && sessionManager != null) {
            sessionManager.delete(SessionKey.main(key));
        }
        return res.status(204).send().then();
    }

    public Mono<Void> resetSession(HttpServerRequest req, HttpServerResponse res) {
        GatewayRouter.addCorsHeaders(res);
        var key = req.param("key");
        if (key != null && sessionManager != null) {
            var sessionMono = sessionManager.get(SessionKey.main(key));
            var session = sessionMono.block();
            if (session != null) {
                session.reset().block();
            }
        }
        return res.header("Content-Type", "application/json")
            .sendString(Mono.just("{\"status\":\"reset\"}"))
            .then();
    }

    private Mono<Void> errorResponse(HttpServerResponse res, int status, String message) {
        GatewayRouter.addCorsHeaders(res);
        return res.status(status)
            .header("Content-Type", "application/json")
            .sendString(Mono.just("{\"error\":\"" + message + "\"}"))
            .then();
    }
}
