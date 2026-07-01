package com.openclaw.desktop.channel.slack;

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
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Slack Bot 通道 �?通过 Slack Web API 接收和发送消息�? * 对应 OpenClaw �?slack extension�? */
public class SlackChannel implements Channel {

    private static final Logger log = LoggerFactory.getLogger(SlackChannel.class);
    private static final String API_BASE = "https://slack.com/api";

    private final ChannelId id;
    private final SlackConfig slackConfig;
    private final ChannelConfig config;
    private final HttpClient httpClient;
    private final Sinks.Many<InboundMessage> messageSink;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private SlackWebSocketClient wsClient;

    public SlackChannel(SlackConfig config) {
        this.id = new ChannelId("slack");
        this.slackConfig = config;
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
                log.info("Starting Slack channel: {}", id);
                // 获取 WSS URL
                var wssUrl = getWssUrl();
                if (wssUrl == null) {
                    throw new RuntimeException("Failed to get Slack WSS URL");
                }
                wsClient = new SlackWebSocketClient(wssUrl, slackConfig.botToken(), messageSink);
                wsClient.connect();
                log.info("Slack WebSocket connected");
            }
        });
    }

    @Override
    public Mono<Void> stop() {
        return Mono.fromRunnable(() -> {
            if (running.compareAndSet(true, false)) {
                log.info("Stopping Slack channel: {}", id);
                if (wsClient != null) {
                    wsClient.disconnect();
                }
            }
        });
    }

    @Override
    public Flux<InboundMessage> inbound() { return messageSink.asFlux(); }

    @Override
    public Mono<Void> send(OutboundMessage message) {
        return Mono.fromCallable(() -> {
            var text = switch (message.content()) {
                case OutboundMessage.OutboundContent.Text(var t) -> t;
                default -> message.content().toString();
            };
            var channel = message.sessionKey() != null ? message.sessionKey().toString() : "";
            var body = String.format(
                "{\"channel\":\"%s\",\"text\":\"%s\"}",
                channel,
                text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
            );
            var request = HttpRequest.newBuilder()
                .uri(URI.create(API_BASE + "/chat.postMessage"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + slackConfig.botToken())
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .timeout(Duration.ofSeconds(10))
                .build();
            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.error("Slack send failed: {} - {}", response.statusCode(), response.body());
            }
            return response.body();
        }).then();
    }

    @Override
    public com.openclaw.desktop.infra.health.HealthCheckResult healthCheck() {
        try {
            var request = HttpRequest.newBuilder()
                .uri(URI.create(API_BASE + "/auth.test"))
                .header("Authorization", "Bearer " + slackConfig.botToken())
                .GET()
                .timeout(Duration.ofSeconds(5))
                .build();
            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            var ok = response.statusCode() == 200 && response.body().contains("\"ok\":true");
            return ok
                ? com.openclaw.desktop.infra.health.HealthCheckResult.healthy()
                : com.openclaw.desktop.infra.health.HealthCheckResult.unhealthy("Token invalid");
        } catch (Exception e) {
            return com.openclaw.desktop.infra.health.HealthCheckResult.unhealthy(e.getMessage());
        }
    }

    @Override
    public boolean isRunning() { return running.get(); }

    private String getWssUrl() {
        try {
            var request = HttpRequest.newBuilder()
                .uri(URI.create(API_BASE + "/rtm.connect"))
                .header("Authorization", "Bearer " + slackConfig.botToken())
                .GET()
                .timeout(Duration.ofSeconds(5))
                .build();
            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                var root = new com.fasterxml.jackson.databind.ObjectMapper().readTree(response.body());
                if (root.path("ok").asBoolean()) {
                    return root.path("url").asText();
                }
            }
        } catch (Exception e) {
            log.error("Failed to get Slack WSS URL: {}", e.getMessage());
        }
        return null;
    }
}
