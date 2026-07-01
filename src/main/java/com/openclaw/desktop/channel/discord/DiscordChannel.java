package com.openclaw.desktop.channel.discord;

import com.openclaw.desktop.channel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Discord Bot 通道 �?通过 Discord Bot API 接收和发送消息�? * 对应 OpenClaw �?discord extension�? *
 * 使用 Discord Gateway WebSocket + REST API�? */
public class DiscordChannel implements Channel {

    private static final Logger log = LoggerFactory.getLogger(DiscordChannel.class);
    private static final String API_BASE = "https://discord.com/api/v10";

    private final ChannelId id;
    private final DiscordConfig discordConfig;
    private final ChannelConfig config;
    private final HttpClient httpClient;
    private final Sinks.Many<InboundMessage> messageSink;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private DiscordWebSocketClient wsClient;

    public DiscordChannel(DiscordConfig config) {
        this.id = new ChannelId("discord");
        this.discordConfig = config;
        this.config = new com.openclaw.desktop.channel.ChannelConfig(id, config.enabled(), java.util.Map.of());
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
        this.messageSink = Sinks.many().multicast().onBackpressureBuffer();
    }

    @Override
    public ChannelId id() { return id; }

    @Override
    public ChannelConfig config() { return config; }

    @Override
    public Mono<Void> start() {
        return Mono.fromRunnable(() -> {
            if (running.compareAndSet(false, true)) {
                log.info("Starting Discord channel: {}", id);
                // 获取 Gateway URL
                var gatewayUrl = getGatewayUrl();
                if (gatewayUrl == null) {
                    throw new RuntimeException("Failed to get Discord gateway URL");
                }
                // 启动 WebSocket 连接
                wsClient = new DiscordWebSocketClient(gatewayUrl, discordConfig.botToken(), discordConfig.applicationId(), messageSink);
                wsClient.connect();
                log.info("Discord WebSocket connected");
            }
        });
    }

    @Override
    public Mono<Void> stop() {
        return Mono.fromRunnable(() -> {
            if (running.compareAndSet(true, false)) {
                log.info("Stopping Discord channel: {}", id);
                if (wsClient != null) {
                    wsClient.disconnect();
                }
            }
        });
    }

    @Override
    public Flux<InboundMessage> inbound() {
        return messageSink.asFlux();
    }

    @Override
    public Mono<Void> send(OutboundMessage message) {
        return Mono.fromCallable(() -> {
            var text = switch (message.content()) {
                case OutboundMessage.OutboundContent.Text(var t) -> t;
                default -> message.content().toString();
            };
            var channelId = message.sessionKey() != null ? message.sessionKey().toString() : "";
            var body = String.format(
                "{\"content\":\"%s\"}",
                text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
            );
            var request = HttpRequest.newBuilder()
                .uri(URI.create(API_BASE + "/channels/" + channelId + "/messages"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bot " + discordConfig.botToken())
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .timeout(Duration.ofSeconds(10))
                .build();

            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.error("Discord send failed: {} - {}", response.statusCode(), response.body());
            }
            return response.body();
        }).then();
    }

    @Override
    public com.openclaw.desktop.infra.health.HealthCheckResult healthCheck() {
        try {
            var request = HttpRequest.newBuilder()
                .uri(URI.create(API_BASE + "/users/@me"))
                .header("Authorization", "Bot " + discordConfig.botToken())
                .GET()
                .timeout(Duration.ofSeconds(5))
                .build();
            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            var ok = response.statusCode() == 200;
            return ok
                ? com.openclaw.desktop.infra.health.HealthCheckResult.healthy()
                : com.openclaw.desktop.infra.health.HealthCheckResult.unhealthy("Token invalid");
        } catch (Exception e) {
            return com.openclaw.desktop.infra.health.HealthCheckResult.unhealthy(e.getMessage());
        }
    }

    @Override
    public boolean isRunning() { return running.get(); }

    private String getGatewayUrl() {
        try {
            var request = HttpRequest.newBuilder()
                .uri(URI.create(API_BASE + "/gateway/bot"))
                .header("Authorization", "Bot " + discordConfig.botToken())
                .GET()
                .timeout(Duration.ofSeconds(5))
                .build();
            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                var root = new com.fasterxml.jackson.databind.ObjectMapper().readTree(response.body());
                return root.path("url").asText();
            }
        } catch (Exception e) {
            log.error("Failed to get Discord gateway URL: {}", e.getMessage());
        }
        return null;
    }
}
