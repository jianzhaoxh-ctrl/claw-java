package com.openclaw.desktop.gateway.ws;

import com.openclaw.desktop.session.SessionKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Sinks;

/**
 * WebSocket 客户端会话 — 封装单个 WS 连接的状态。
 */
public class WebSocketClientSession {

    private static final Logger log = LoggerFactory.getLogger(WebSocketClientSession.class);

    private final String connectionId;
    private final SessionKey sessionKey;
    private final WebSocketConnectionManager manager;
    private final Sinks.Many<String> messageSink;
    private volatile boolean active = true;
    private volatile long lastActivityAt;

    public WebSocketClientSession(String connectionId, SessionKey sessionKey, WebSocketConnectionManager manager) {
        this.connectionId = connectionId;
        this.sessionKey = sessionKey;
        this.manager = manager;
        this.messageSink = Sinks.many().multicast().onBackpressureBuffer();
        this.lastActivityAt = System.currentTimeMillis();
    }

    public String connectionId() { return connectionId; }
    public SessionKey sessionKey() { return sessionKey; }
    public boolean isActive() { return active; }

    /**
     * 发送消息给此客户端。
     */
    public void send(String message) {
        if (!active) return;
        messageSink.tryEmitNext(message);
        lastActivityAt = System.currentTimeMillis();
    }

    /**
     * 获取出站消息流（由 Netty WS handler 消费）。
     */
    public reactor.core.publisher.Flux<String> outbound() {
        return messageSink.asFlux();
    }

    /**
     * 关闭连接。
     */
    public void close() {
        active = false;
        messageSink.tryEmitComplete();
        manager.unregister(connectionId);
    }

    public long lastActivityAt() { return lastActivityAt; }
}
