package com.openclaw.desktop.channel.webchat;

import com.openclaw.desktop.channel.*;
import com.openclaw.desktop.session.SessionKey;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

/**
 * WebChat 通道 — 内置 Web 聊天页面。
 * 通过 Gateway HTTP API 接收消息，不走外部协议。
 */
public class WebChatChannel implements Channel {

    private final Sinks.Many<InboundMessage> messageSink = Sinks.many().unicast().onBackpressureBuffer();
    private volatile boolean running = false;

    @Override
    public ChannelId id() {
        return new ChannelId("webchat");
    }

    @Override
    public ChannelConfig config() {
        return new ChannelConfig(new ChannelId("webchat"), true, java.util.Map.of());
    }

    @Override
    public Mono<Void> start() {
        running = true;
        return Mono.empty();
    }

    @Override
    public Mono<Void> stop() {
        running = false;
        messageSink.tryEmitComplete();
        return Mono.empty();
    }

    @Override
    public Flux<InboundMessage> inbound() {
        return messageSink.asFlux();
    }

    @Override
    public Mono<Void> send(OutboundMessage message) {
        // WebChat 的回复直接通过 WebSocket 推送，不需要额外发送
        return Mono.empty();
    }

    @Override
    public com.openclaw.desktop.infra.health.HealthCheckResult healthCheck() {
        return com.openclaw.desktop.infra.health.HealthCheckResult.healthy();
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    /**
     * 接收来自 HTTP API 的消息。
     */
    public void receiveMessage(String text, String sessionId) {
        var inbound = InboundMessage.text(
            new ChannelId("webchat"),
            SessionKey.main(sessionId),
            text
        );
        messageSink.tryEmitNext(inbound);
    }
}
