package com.openclaw.desktop.channel.webhook;

import com.openclaw.desktop.channel.*;
import com.openclaw.desktop.session.SessionKey;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * Webhook 通道 — 通用的 HTTP 回调通道，可对接任意第三方系统。
 * 对应 OpenClaw 的 webhooks extension。
 *
 * 接收：任何 POST 到 /v1/webhook/{token} 的请求被当作消息。
 * 发送：通过配置的出站 Webhook URL 将回复 POST 回去。
 */
public class WebhookChannel implements Channel {

    private static final Logger log = LoggerFactory.getLogger(WebhookChannel.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ChannelId id;
    private final WebhookConfig whConfig;
    private final ChannelConfig config;
    private final HttpClient httpClient;
    private final Sinks.Many<InboundMessage> messageSink;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public WebhookChannel(WebhookConfig whConfig) {
        this.id = new ChannelId("webhook");
        this.whConfig = whConfig;
        this.config = new ChannelConfig(id, whConfig.enabled(), Map.of());
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
            running.set(true);
            log.info("Webhook channel started (inbound token={}, outbound url={})",
                whConfig.inboundToken() != null ? "set" : "none",
                whConfig.outboundUrl() != null ? whConfig.outboundUrl() : "none");
        });
    }

    @Override
    public Mono<Void> stop() {
        return Mono.fromRunnable(() -> running.set(false));
    }

    @Override
    public Flux<InboundMessage> inbound() {
        return messageSink.asFlux();
    }

    /**
     * 由 Gateway HTTP 端点调用，将外部 Webhook POST 转为消息。
     */
    public void handleIncoming(String body, String sourceId) {
        try {
            var payload = MAPPER.readTree(body);
            var text = payload.path("text").asText("");
            if (text.isEmpty()) {
                text = payload.path("message").asText("");
            }
            if (text.isEmpty()) {
                text = payload.path("content").asText("");
            }
            if (!text.isEmpty()) {
                var inbound = InboundMessage.text(
                    id,
                    SessionKey.main("webhook-" + sourceId),
                    text
                );
                messageSink.tryEmitNext(inbound);
                log.debug("Webhook message from {}: {}", sourceId,
                    text.substring(0, Math.min(50, text.length())));
            }
        } catch (Exception e) {
            log.warn("Failed to handle webhook: {}", e.getMessage());
        }
    }

    @Override
    public Mono<Void> send(OutboundMessage message) {
        if (whConfig.outboundUrl() == null || whConfig.outboundUrl().isEmpty()) {
            return Mono.empty(); // 无出站 URL，静默丢弃
        }
        return Mono.fromCallable(() -> {
            var text = switch (message.content()) {
                case OutboundMessage.OutboundContent.Text(var t) -> t;
                default -> message.content().toString();
            };

            var body = MAPPER.createObjectNode();
            body.put("text", text);
            body.put("source", "clawdesktop");
            body.put("timestamp", java.time.Instant.now().toString());

            var request = HttpRequest.newBuilder()
                .uri(URI.create(whConfig.outboundUrl()))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(body)))
                .build();

            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                log.warn("Webhook outbound failed: {} {}", response.statusCode(), response.body());
            }
            return null;
        });
    }

    @Override
    public com.openclaw.desktop.infra.health.HealthCheckResult healthCheck() {
        return com.openclaw.desktop.infra.health.HealthCheckResult.healthy();
    }

    @Override
    public boolean isRunning() { return running.get(); }
}
