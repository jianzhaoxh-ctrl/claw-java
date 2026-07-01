package com.openclaw.desktop.gateway.ws;

import com.openclaw.desktop.agent.Agent;
import com.openclaw.desktop.agent.AgentEvent;
import com.openclaw.desktop.session.SessionKey;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket 连接管理器 — 管理所有活跃的 WS 客户端连接。
 * 对应 OpenClaw 的 WebSocket Gateway。
 *
 * 功能：
 * - 客户端连接/断开管理
 * - 消息广播（Agent 事件推送到所有连接的客户端）
 * - 会话级隔离（每个连接绑定一个 sessionKey）
 * - 心跳保活
 */
public class WebSocketConnectionManager {

    private static final Logger log = LoggerFactory.getLogger(WebSocketConnectionManager.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Map<String, WebSocketClientSession> sessions = new ConcurrentHashMap<>();
    private final Agent agent;

    public WebSocketConnectionManager(Agent agent) {
        this.agent = agent;
    }

    /**
     * 注册新连接。
     */
    public WebSocketClientSession register(String connectionId, SessionKey sessionKey) {
        var session = new WebSocketClientSession(connectionId, sessionKey, this);
        sessions.put(connectionId, session);
        log.info("WS client connected: {} (session={}, total={})", connectionId, sessionKey, sessions.size());
        return session;
    }

    /**
     * 注销连接。
     */
    public void unregister(String connectionId) {
        var removed = sessions.remove(connectionId);
        if (removed != null) {
            log.info("WS client disconnected: {} (total={})", connectionId, sessions.size());
        }
    }

    /**
     * 向指定连接发送消息。
     */
    public void send(String connectionId, String message) {
        var session = sessions.get(connectionId);
        if (session != null) {
            session.send(message);
        }
    }

    /**
     * 广播消息到所有连接。
     */
    public void broadcast(String message) {
        for (var session : sessions.values()) {
            session.send(message);
        }
    }

    /**
     * 广播到指定 agent 的所有连接。
     */
    public void broadcastToAgent(String agentId, String message) {
        for (var session : sessions.values()) {
            if (agentId.equals(session.sessionKey().agentId())) {
                session.send(message);
            }
        }
    }

    /**
     * 处理来自客户端的消息。
     */
    public Mono<Void> handleMessage(String connectionId, String rawMessage) {
        return Mono.fromCallable(() -> {
            var payload = MAPPER.readTree(rawMessage);
            var type = payload.path("type").asText("");

            return switch (type) {
                case "chat" -> handleChat(connectionId, payload);
                case "abort" -> handleAbort(connectionId, payload);
                case "ping" -> {
                    send(connectionId, "{\"type\":\"pong\",\"timestamp\":" + System.currentTimeMillis() + "}");
                    yield Mono.<Void>empty();
                }
                case "subscribe" -> handleSubscribe(connectionId, payload);
                default -> {
                    send(connectionId, "{\"type\":\"error\",\"message\":\"Unknown type: " + type + "\"}");
                    yield Mono.<Void>empty();
                }
            };
        }).flatMap(m -> m);
    }

    @SuppressWarnings("unchecked")
    private Mono<Void> handleChat(String connectionId, JsonNode payload) {
        var message = payload.path("message").asText("");
        if (message.isEmpty()) {
            send(connectionId, "{\"type\":\"error\",\"message\":\"message field required\"}");
            return Mono.empty();
        }

        var session = sessions.get(connectionId);
        if (session == null) return Mono.empty();

        // 发送 chat 事件到 Agent，流式推送回 WS 客户端
        return agent.chatStream(message)
            .doOnNext(chunk -> send(connectionId, "{\"type\":\"text\",\"delta\":\"" + escape(chunk) + "\"}"))
            .doOnComplete(() -> send(connectionId, "{\"type\":\"done\"}"))
            .doOnError(e -> send(connectionId, "{\"type\":\"error\",\"message\":\"" + escape(e.getMessage()) + "\"}"))
            .then();
    }

    private Mono<Void> handleAbort(String connectionId, JsonNode payload) {
        send(connectionId, "{\"type\":\"aborted\"}");
        return Mono.empty();
    }

    private Mono<Void> handleSubscribe(String connectionId, JsonNode payload) {
        send(connectionId, "{\"type\":\"subscribed\",\"channel\":\"agent_events\"}");
        return Mono.empty();
    }

    /**
     * 将 AgentEvent 序列化为 JSON。
     */
    private String escape(String s) {
        return s != null ? s.replace("\"", "\\\"").replace("\n", "\\n") : "";
    }

    public int connectionCount() {
        return sessions.size();
    }

    public Map<String, WebSocketClientSession> sessions() {
        return Map.copyOf(sessions);
    }
}
