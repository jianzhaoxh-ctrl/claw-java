package com.openclaw.desktop.gateway.ws;

import com.openclaw.desktop.agent.Agent;
import com.openclaw.desktop.session.SessionKey;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import reactor.netty.http.websocket.WebsocketInbound;
import reactor.netty.http.websocket.WebsocketOutbound;

import java.util.UUID;

/**
 * WebSocket Handler — 处理 WS 连接的建立、消息收发和关闭。
 * 对应 OpenClaw 的 WebSocket 运行时。
 *
 * 协议：
 * - 客户端发送: {"type":"chat","message":"你好"}
 * - 服务端响应: {"type":"text_delta","delta":"你"} ... {"type":"done"}
 *
 * 心跳：
 * - 客户端发送: {"type":"ping"}
 * - 服务端响应: {"type":"pong","timestamp":...}
 */
public class WebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(WebSocketHandler.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final WebSocketConnectionManager connectionManager;

    public WebSocketHandler(Agent agent) {
        this.connectionManager = new WebSocketConnectionManager(agent);
    }

    /**
     * 处理新的 WebSocket 连接。
     */
    public Mono<Void> handle(WebsocketInbound inbound, WebsocketOutbound outbound) {
        var connectionId = UUID.randomUUID().toString().substring(0, 8);
        var sessionKey = SessionKey.main("ws-" + connectionId);

        var clientSession = connectionManager.register(connectionId, sessionKey);

        // 入站消息 → ConnectionManager
        var receive = inbound.receive()
            .asString()
            .flatMap(msg -> connectionManager.handleMessage(connectionId, msg)
                .doOnError(e -> log.warn("WS message error from {}: {}", connectionId, e.getMessage()))
                .onErrorResume(e -> Mono.empty()))
            .then();

        // 出站消息 ← ClientSession
        var send = outbound.sendString(clientSession.outbound());

        return Mono.when(receive, send)
            .doFinally(signal -> {
                clientSession.close();
                log.debug("WS connection {} finalized: {}", connectionId, signal);
            });
    }

    public WebSocketConnectionManager connectionManager() {
        return connectionManager;
    }

    public int connectionCount() {
        return connectionManager.connectionCount();
    }
}
